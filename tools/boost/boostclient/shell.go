package boostclient

import (
	"context"
	"crypto/tls"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"sync/atomic"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/server"
	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
	"github.com/quic-go/quic-go"
)

const (
	shellPingEvery = 3 * time.Second
	// shellDeadAfter without a frame from the helper means the path is gone (a changed network, an outage): the
	// shell reattaches over a new connection instead of waiting out QUIC's idle timeout.
	shellDeadAfter     = 10 * time.Second
	shellAttachTimeout = 8 * time.Second
	// shellGiveUp is how long the shell keeps trying to get back to the helper, which keeps a shell without a
	// client for 12 hours.
	shellGiveUp = 12 * time.Hour
	// Reattach attempts come every few seconds for shellSlowRetryAfter, then every shellSlowRetry.
	shellSlowRetryAfter = 2 * time.Minute
	// shellHangupWait is how long closing waits for the helper to confirm the shell ended.
	shellHangupWait = 3 * time.Second
	shellSlowRetry  = 30 * time.Second
)

// ShellListener hears a shell's events, one at a time, on a goroutine of the shell's.
type ShellListener interface {
	// OnOutput is the terminal's output, in order and without gaps (except after a very long absence, when the
	// helper had dropped some).
	OnOutput(data []byte)
	// OnState is "connected" (detail: "udp" or "tcp"), "reconnecting" (detail: why), or "lost" (the helper or
	// its shell is gone, or it couldn't be reached for too long; the shell is over).
	OnState(state, detail string)
	// OnExit is the shell's exit status; the shell is over.
	OnExit(status int)
}

// Shell is the user's login shell running in the helper, reached over a connection of its own that it replaces
// whenever it breaks: typed input is kept until the helper confirms it, and output resumes where it left off.
type Shell struct {
	s        *Session
	listener ShellListener

	// pingSentAt is when the unanswered ping went out (unix nanos, 0 for none); rttMicros the smoothed round trip.
	pingSentAt atomic.Int64
	rttMicros  atomic.Int64

	mu       sync.Mutex
	id       string
	term     string
	rows     int
	cols     int
	resized  bool
	out      int64  // output offset received so far
	inAcked  int64  // input bytes the helper has confirmed
	pending  []byte // input after inAcked
	closed   bool
	hangup   bool
	current  *attachment
	wake     chan struct{}
	kick     chan struct{}
	finished chan struct{}
}

type attachment struct {
	rw    io.ReadWriter
	close func()
	once  sync.Once
	wmu   sync.Mutex
}

func (a *attachment) shut() { a.once.Do(a.close) }

// send writes one frame; Close and the serve loop both write, and a stream takes one writer at a time.
func (a *attachment) send(kind byte, payload []byte) error {
	a.wmu.Lock()
	defer a.wmu.Unlock()
	return wire.WriteShellFrame(a.rw, kind, payload)
}

var errShellGone = errors.New("the shell is gone")

// StartShell starts the user's login shell at rows x cols with terminal type term. It returns once the helper
// has started it, or with the reason it couldn't.
func (s *Session) StartShell(rows, cols int, term string, listener ShellListener) (*Shell, error) {
	sh := &Shell{
		s: s, listener: listener, term: term, rows: rows, cols: cols,
		wake: make(chan struct{}, 1), kick: make(chan struct{}, 1), finished: make(chan struct{}),
	}
	att, transport, err := sh.attach()
	if err != nil {
		return nil, err
	}
	go sh.run(att, transport)
	return sh, nil
}

// ID is the helper's name for the shell.
func (sh *Shell) ID() string {
	sh.mu.Lock()
	defer sh.mu.Unlock()
	return sh.id
}

// Write sends typed input. It never blocks: input waits in the shell until the helper confirms it.
func (sh *Shell) Write(data []byte) {
	sh.mu.Lock()
	if !sh.closed {
		sh.pending = append(sh.pending, data...)
	}
	sh.mu.Unlock()
	sh.signal(sh.wake)
}

// Resize sets the terminal's size.
func (sh *Shell) Resize(rows, cols int) {
	sh.mu.Lock()
	sh.rows, sh.cols, sh.resized = rows, cols, true
	sh.mu.Unlock()
	sh.signal(sh.wake)
}

// Kick drops the current connection and reattaches now: the app calls it when the network changed, since the old
// connection's path is probably gone.
func (sh *Shell) Kick() {
	sh.mu.Lock()
	att := sh.current
	sh.mu.Unlock()
	if att != nil {
		att.shut()
	}
	sh.signal(sh.kick)
}

// Close stops the shell's connection; with hangup it also ends the shell on the server (the session was closed),
// otherwise the helper keeps it for a while. It doesn't block: a hangup waits in the background, briefly, for the
// helper to confirm the shell ended, since closing the connection at once could drop the request.
func (sh *Shell) Close(hangup bool) {
	sh.mu.Lock()
	if sh.closed {
		sh.mu.Unlock()
		return
	}
	sh.closed = true
	sh.hangup = hangup
	att := sh.current
	sh.mu.Unlock()
	if !hangup {
		if att != nil {
			att.shut()
		}
		sh.signal(sh.kick)
		return
	}
	go func() {
		defer sh.signal(sh.kick)
		if att != nil {
			// The serve loop is still reading: the exit frame ends it, and run, and finished.
			_ = att.send(wire.FrameHangup, nil)
			select {
			case <-sh.finished:
			case <-time.After(shellHangupWait):
			}
			att.shut()
			return
		}
		// Between connections: attach once more, just to hang up.
		a, _, err := sh.attach()
		if err != nil {
			return
		}
		defer a.shut()
		if a.send(wire.FrameHangup, nil) != nil {
			return
		}
		if d, ok := a.rw.(interface{ SetDeadline(time.Time) error }); ok {
			d.SetDeadline(time.Now().Add(shellHangupWait))
		}
		buf := make([]byte, wire.MaxFrame)
		for {
			kind, _, err := wire.ReadShellFrame(a.rw, buf)
			if err != nil || kind == wire.FrameExit {
				return
			}
		}
	}()
}

func (sh *Shell) signal(ch chan struct{}) {
	select {
	case ch <- struct{}{}:
	default:
	}
}

// dial opens a connection of the shell's own to the helper: UDP first when it's allowed, then TCP.
func (sh *Shell) dial(ctx context.Context) (*attachment, string, error) {
	s := sh.s
	var errs []string
	if s.udpAddr != "" {
		conn, err := quic.DialAddr(ctx, s.udpAddr, s.tlsConf, server.QuicConfig())
		if err == nil {
			stream, err := conn.OpenStreamSync(ctx)
			if err == nil {
				return &attachment{rw: stream, close: func() { conn.CloseWithError(0, "") }}, "udp", nil
			}
			conn.CloseWithError(0, "")
		}
		errs = append(errs, "UDP: "+err.Error())
	}
	if s.tcpFallback != "" {
		d := tls.Dialer{NetDialer: &net.Dialer{Timeout: shellAttachTimeout}, Config: s.tlsConf}
		conn, err := d.DialContext(ctx, "tcp", s.tcpFallback)
		if err == nil {
			return &attachment{rw: conn, close: func() { conn.Close() }}, "tcp", nil
		}
		errs = append(errs, "TCP: "+err.Error())
	}
	return nil, "", fmt.Errorf("could not reach the boost helper (%s)", joinErrs(errs))
}

// attach connects and asks the helper for the shell (a new one the first time), from the output offset reached.
func (sh *Shell) attach() (*attachment, string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), shellAttachTimeout)
	defer cancel()
	att, transport, err := sh.dial(ctx)
	if err != nil {
		return nil, "", err
	}
	sh.mu.Lock()
	req := wire.Request{Op: "shell", ID: sh.id, Offset: sh.out, Rows: sh.rows, Cols: sh.cols, Term: sh.term}
	sh.mu.Unlock()
	if deadline, ok := ctx.Deadline(); ok {
		if d, ok := att.rw.(interface{ SetDeadline(time.Time) error }); ok {
			d.SetDeadline(deadline)
			defer d.SetDeadline(time.Time{})
		}
	}
	if err := wire.WriteRequest(att.rw, sh.s.token, req); err != nil {
		att.shut()
		return nil, "", err
	}
	rep, err := wire.ReadReply(att.rw)
	if err != nil {
		att.shut()
		return nil, "", err
	}
	if rep.Error != "" {
		att.shut()
		if rep.Error == "no such shell" {
			return nil, "", errShellGone
		}
		return nil, "", &RemoteError{Message: rep.Error}
	}
	sh.mu.Lock()
	sh.id = rep.ID
	if rep.Start > sh.out {
		sh.out = rep.Start
	}
	sh.ackLocked(rep.In)
	// The helper's size may be stale after time away.
	sh.resized = true
	sh.mu.Unlock()
	return att, transport, nil
}

func (sh *Shell) ackLocked(in int64) {
	if n := in - sh.inAcked; n > 0 {
		if n > int64(len(sh.pending)) {
			n = int64(len(sh.pending))
		}
		sh.pending = sh.pending[n:]
		sh.inAcked += n
	}
}

// run serves attachments one after another until the shell exits, is closed, or can't be reached any more.
func (sh *Shell) run(att *attachment, transport string) {
	defer close(sh.finished)
	var lostSince time.Time
	backoff := time.Second
	for {
		if att != nil {
			sh.mu.Lock()
			closed := sh.closed
			sh.current = att
			sh.mu.Unlock()
			if closed {
				att.shut()
				return
			}
			lostSince = time.Time{}
			backoff = time.Second
			sh.listener.OnState("connected", transport)
			status, exited, err := sh.serve(att)
			att.shut()
			sh.mu.Lock()
			sh.current = nil
			closed = sh.closed
			sh.mu.Unlock()
			if exited {
				sh.listener.OnExit(status)
				return
			}
			if closed {
				return
			}
			lostSince = time.Now()
			sh.listener.OnState("reconnecting", err.Error())
			att = nil
		}
		select {
		case <-sh.kick:
		case <-time.After(backoff):
			// Quick at first; after a while away (a phone offline or asleep) slowly, to spare the battery. A
			// network change or the network coming back kicks it at once anyway.
			if time.Since(lostSince) < shellSlowRetryAfter {
				backoff = min(backoff*2, 5*time.Second)
			} else {
				backoff = shellSlowRetry
			}
		}
		sh.mu.Lock()
		closed := sh.closed
		sh.mu.Unlock()
		if closed {
			return
		}
		var err error
		att, transport, err = sh.attach()
		if err != nil {
			att = nil
			var remote *RemoteError
			if errors.Is(err, errShellGone) || errors.As(err, &remote) {
				sh.listener.OnState("lost", err.Error())
				return
			}
			if time.Since(lostSince) > shellGiveUp {
				sh.listener.OnState("lost", "the helper couldn't be reached: "+err.Error())
				return
			}
			sh.listener.OnState("reconnecting", err.Error())
		}
	}
}

// serve carries one attachment: output in, input and resizes out, pings both ways. It returns when the shell
// exits or the attachment fails.
func (sh *Shell) serve(att *attachment) (status int, exited bool, err error) {
	type event struct {
		exited bool
		status int
		err    error
	}
	events := make(chan event, 1)
	heard := make(chan struct{}, 1)
	go func() {
		buf := make([]byte, wire.MaxFrame)
		for {
			kind, payload, err := wire.ReadShellFrame(att.rw, buf)
			if err != nil {
				events <- event{err: err}
				return
			}
			select {
			case heard <- struct{}{}:
			default:
			}
			switch kind {
			case wire.FrameOutput:
				sh.mu.Lock()
				sh.out += int64(len(payload))
				sh.mu.Unlock()
				sh.listener.OnOutput(append([]byte(nil), payload...))
			case wire.FrameAck:
				if len(payload) == 8 {
					sh.mu.Lock()
					sh.ackLocked(int64(binary.BigEndian.Uint64(payload)))
					sh.mu.Unlock()
				}
			case wire.FramePing:
				if sentAt := sh.pingSentAt.Swap(0); sentAt != 0 {
					sample := (time.Now().UnixNano() - sentAt) / int64(time.Microsecond)
					if old := sh.rttMicros.Load(); old == 0 {
						sh.rttMicros.Store(sample)
					} else {
						sh.rttMicros.Store((old*7 + sample*3) / 10)
					}
				}
			case wire.FrameExit:
				code := 0
				if len(payload) == 4 {
					code = int(int32(binary.BigEndian.Uint32(payload)))
				}
				events <- event{exited: true, status: code}
				return
			}
		}
	}()

	// Everything not yet confirmed goes out again on a new attachment; sent counts what this one has sent.
	sh.mu.Lock()
	sent := sh.inAcked
	sh.mu.Unlock()
	lastHeard := time.Now()
	lastPing := time.Time{}
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for {
		sh.mu.Lock()
		if sent < sh.inAcked {
			sent = sh.inAcked
		}
		var input []byte
		if unsent := sh.inAcked + int64(len(sh.pending)) - sent; unsent > 0 {
			from := sent - sh.inAcked
			input = append([]byte(nil), sh.pending[from:from+min(unsent, wire.MaxFrame)]...)
		}
		var size []byte
		if sh.resized {
			size = make([]byte, 4)
			binary.BigEndian.PutUint16(size[0:2], uint16(sh.rows))
			binary.BigEndian.PutUint16(size[2:4], uint16(sh.cols))
			sh.resized = false
		}
		sh.mu.Unlock()
		if size != nil {
			if err := att.send(wire.FrameResize, size); err != nil {
				return 0, false, err
			}
		}
		if input != nil {
			if err := att.send(wire.FrameInput, input); err != nil {
				return 0, false, err
			}
			sent += int64(len(input))
			continue
		}
		select {
		case ev := <-events:
			if ev.exited {
				return ev.status, true, nil
			}
			return 0, false, ev.err
		case <-heard:
			lastHeard = time.Now()
		case <-sh.wake:
		case <-sh.kick:
			return 0, false, errors.New("the network changed")
		case <-ticker.C:
			if time.Since(lastHeard) > shellDeadAfter {
				return 0, false, fmt.Errorf("no answer from the helper for %d s", int(shellDeadAfter/time.Second))
			}
			// Every few seconds, typing or not: the answer times the round trip too.
			if time.Since(lastPing) >= shellPingEvery {
				sh.pingSentAt.Store(time.Now().UnixNano())
				if err := att.send(wire.FramePing, nil); err != nil {
					return 0, false, err
				}
				lastPing = time.Now()
			}
		}
	}
}

// RTTMillis is the smoothed round trip to the helper, from the shell's pings; -1 until one was answered.
func (sh *Shell) RTTMillis() int {
	if us := sh.rttMicros.Load(); us > 0 {
		return int((us + 500) / 1000)
	}
	return -1
}

// Await waits until the shell is over (exited, lost or closed).
func (sh *Shell) Await() { <-sh.finished }
