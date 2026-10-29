package server

import (
	"bufio"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/creack/pty"
	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
)

const (
	// outputKeep is how much of a shell's latest output is kept for a client to catch up on after reattaching.
	outputKeep = 1 << 20
	maxShells  = 8
)

var termName = regexp.MustCompile(`^[A-Za-z0-9._+-]{1,64}$`)

// shell is one login shell on a pseudo-terminal. It outlives the client's connection: output goes into a buffer
// that the next attachment replays from the offset the client asks for.
type shell struct {
	id  string
	cmd *exec.Cmd
	pty *os.File

	mu   sync.Mutex
	cond *sync.Cond
	buf  []byte // the latest output; it holds offsets [end-len(buf), end)
	end  int64  // output bytes produced so far
	sent int64  // what the current attachment has sent
	in   int64  // input bytes given to the shell so far
	// gen counts attachments; a newer one ends the older one's loops.
	gen        int64
	attached   bool
	detachedAt time.Time
	exited     bool
	status     int
	delivered  bool // the exit reached a client
	hungUp     bool // the client ended it: nobody is coming back for the exit
}

func (s *Server) startShell(rows, cols int, term string) (*shell, error) {
	s.mu.Lock()
	count := len(s.shells)
	s.mu.Unlock()
	if count >= maxShells {
		return nil, errors.New("too many shells")
	}
	if term == "" {
		term = "xterm-256color"
	}
	if !termName.MatchString(term) {
		return nil, errors.New("bad terminal type")
	}
	path := loginShell()
	cmd := exec.Command(path)
	// A leading dash makes it a login shell, as sshd starts it.
	cmd.Args = []string{"-" + filepath.Base(path)}
	if home, err := os.UserHomeDir(); err == nil {
		cmd.Dir = home
	}
	cmd.Env = append(withoutVar(os.Environ(), "TERM"), "TERM="+term)
	f, err := pty.StartWithSize(cmd, &pty.Winsize{Rows: clampSize(rows, 24), Cols: clampSize(cols, 80)})
	if err != nil {
		return nil, err
	}
	idBytes := make([]byte, 16)
	rand.Read(idBytes)
	sh := &shell{id: hex.EncodeToString(idBytes), cmd: cmd, pty: f, detachedAt: time.Now()}
	sh.cond = sync.NewCond(&sh.mu)
	s.mu.Lock()
	s.shells[sh.id] = sh
	s.mu.Unlock()
	go sh.readOutput()
	go sh.wait()
	return sh, nil
}

func clampSize(v, fallback int) uint16 {
	if v <= 0 || v > 4096 {
		return uint16(fallback)
	}
	return uint16(v)
}

func withoutVar(env []string, name string) []string {
	out := env[:0:0]
	for _, kv := range env {
		if !strings.HasPrefix(kv, name+"=") {
			out = append(out, kv)
		}
	}
	return out
}

// loginShell is the user's shell: $SHELL (sshd sets it from the password database), else that entry, else /bin/sh.
func loginShell() string {
	if sh := os.Getenv("SHELL"); sh != "" {
		return sh
	}
	if f, err := os.Open("/etc/passwd"); err == nil {
		defer f.Close()
		uid := strconv.Itoa(os.Getuid())
		lines := bufio.NewScanner(f)
		for lines.Scan() {
			fields := strings.Split(lines.Text(), ":")
			if len(fields) >= 7 && fields[2] == uid && fields[6] != "" {
				return fields[6]
			}
		}
	}
	return "/bin/sh"
}

// readOutput moves the shell's output into the buffer. While a client is attached and more than the buffer's
// worth is unsent, it waits, so a fast writer slows down as over SSH; while detached it keeps only the latest.
func (sh *shell) readOutput() {
	tmp := make([]byte, 32<<10)
	for {
		n, err := sh.pty.Read(tmp)
		if n > 0 {
			sh.mu.Lock()
			for sh.attached && !sh.exited && sh.end-sh.sent >= outputKeep {
				sh.cond.Wait()
			}
			sh.buf = append(sh.buf, tmp[:n]...)
			if len(sh.buf) > outputKeep {
				sh.buf = append(sh.buf[:0:0], sh.buf[len(sh.buf)-outputKeep:]...)
			}
			sh.end += int64(n)
			sh.cond.Broadcast()
			sh.mu.Unlock()
		}
		if err != nil {
			return
		}
	}
}

func (sh *shell) wait() {
	err := sh.cmd.Wait()
	status := 0
	if err != nil {
		status = -1
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			status = exit.ExitCode()
		}
	}
	// Let the last output drain before the terminal closes under it.
	time.Sleep(150 * time.Millisecond)
	sh.pty.Close()
	sh.mu.Lock()
	sh.exited = true
	sh.status = status
	sh.cond.Broadcast()
	sh.mu.Unlock()
}

// hangup ends the shell's whole session, as a closing terminal does.
func (sh *shell) hangup() {
	sh.mu.Lock()
	sh.hungUp = true
	sh.mu.Unlock()
	if p := sh.cmd.Process; p != nil {
		syscall.Kill(-p.Pid, syscall.SIGHUP)
		go func() {
			time.Sleep(3 * time.Second)
			sh.mu.Lock()
			exited := sh.exited
			sh.mu.Unlock()
			if !exited {
				syscall.Kill(-p.Pid, syscall.SIGKILL)
			}
		}()
	}
}

// serveShell runs a shell request: it starts or finds the shell, replays output from the client's offset, and
// then carries the terminal both ways until the stream breaks, a newer attachment replaces it, or the shell ends.
func (s *Server) serveShell(rw io.ReadWriter, req wire.Request) bool {
	if !s.cfg.Shell {
		return fail(rw, errors.New("shells aren't enabled"))
	}
	var sh *shell
	if req.ID == "" {
		var err error
		if sh, err = s.startShell(req.Rows, req.Cols, req.Term); err != nil {
			return fail(rw, err)
		}
	} else {
		s.mu.Lock()
		sh = s.shells[req.ID]
		s.mu.Unlock()
		if sh == nil {
			return fail(rw, errors.New("no such shell"))
		}
	}
	sh.mu.Lock()
	sh.gen++
	gen := sh.gen
	start := req.Offset
	if oldest := sh.end - int64(len(sh.buf)); start < oldest {
		start = oldest
	}
	if start > sh.end {
		start = sh.end
	}
	sh.sent = start
	sh.attached = true
	in := sh.in
	sh.cond.Broadcast()
	sh.mu.Unlock()
	if err := wire.WriteReply(rw, wire.Reply{ID: sh.id, Start: start, In: in}); err != nil {
		sh.detach(gen)
		return false
	}

	var writeMu sync.Mutex
	send := func(kind byte, payload []byte) error {
		writeMu.Lock()
		defer writeMu.Unlock()
		return wire.WriteShellFrame(rw, kind, payload)
	}
	gone := make(chan struct{})
	go sh.sendLoop(gen, start, gone, send, s)

	buf := make([]byte, wire.MaxFrame)
	for {
		kind, payload, err := wire.ReadShellFrame(rw, buf)
		if err != nil {
			break
		}
		s.touch()
		switch kind {
		case wire.FrameInput:
			n, _ := sh.pty.Write(payload)
			sh.mu.Lock()
			sh.in += int64(n)
			sh.cond.Broadcast()
			sh.mu.Unlock()
		case wire.FrameResize:
			if len(payload) == 4 {
				rows := binary.BigEndian.Uint16(payload[0:2])
				cols := binary.BigEndian.Uint16(payload[2:4])
				pty.Setsize(sh.pty, &pty.Winsize{Rows: clampSize(int(rows), 24), Cols: clampSize(int(cols), 80)})
			}
		case wire.FrameHangup:
			sh.hangup()
		case wire.FramePing:
			if send(wire.FramePing, nil) != nil {
				break
			}
		}
	}
	close(gone)
	sh.detach(gen)
	return false
}

func (sh *shell) detach(gen int64) {
	sh.mu.Lock()
	if sh.gen == gen {
		sh.attached = false
		sh.detachedAt = time.Now()
		sh.gen++
	}
	sh.cond.Broadcast()
	sh.mu.Unlock()
}

// sendLoop sends output, input acknowledgements and the exit to the attachment [gen].
func (sh *shell) sendLoop(gen, sent int64, gone <-chan struct{}, send func(byte, []byte) error, s *Server) {
	// The cond can't wait on a channel: a closed [gone] wakes it through detach's broadcast, which bumps gen.
	acked := int64(-1)
	for {
		sh.mu.Lock()
		for sh.gen == gen && sh.end == sent && sh.in == acked && !(sh.exited && sent == sh.end) {
			sh.cond.Wait()
		}
		if sh.gen != gen {
			sh.mu.Unlock()
			return
		}
		var chunk []byte
		if sh.end > sent {
			oldest := sh.end - int64(len(sh.buf))
			if sent < oldest {
				sent = oldest
			}
			from := int(sent - oldest)
			to := min(len(sh.buf), from+wire.MaxFrame)
			chunk = append([]byte(nil), sh.buf[from:to]...)
		}
		in := sh.in
		exited := sh.exited && sent+int64(len(chunk)) == sh.end
		status := sh.status
		sh.mu.Unlock()

		if len(chunk) > 0 {
			if send(wire.FrameOutput, chunk) != nil {
				return
			}
			sent += int64(len(chunk))
			sh.mu.Lock()
			if sh.gen == gen {
				sh.sent = sent
			}
			sh.cond.Broadcast()
			sh.mu.Unlock()
		}
		if in != acked {
			var b [8]byte
			binary.BigEndian.PutUint64(b[:], uint64(in))
			if send(wire.FrameAck, b[:]) != nil {
				return
			}
			acked = in
		}
		if exited {
			var b [4]byte
			binary.BigEndian.PutUint32(b[:], uint32(int32(status)))
			if send(wire.FrameExit, b[:]) == nil {
				sh.mu.Lock()
				sh.delivered = true
				sh.mu.Unlock()
				s.reapShells(false)
			}
			return
		}
		select {
		case <-gone:
			return
		default:
		}
	}
}

// reapShells forgets shells whose exit reached a client, and with [expired] also ends those no client has
// attached to for Config.DetachedFor. It reports how many shells are left.
func (s *Server) reapShells(expired bool) int {
	s.mu.Lock()
	defer s.mu.Unlock()
	for id, sh := range s.shells {
		sh.mu.Lock()
		done := sh.exited && (sh.delivered || sh.hungUp)
		stale := expired && !sh.attached && time.Since(sh.detachedAt) > s.cfg.DetachedFor
		sh.mu.Unlock()
		if stale {
			sh.hangup()
		}
		if done || stale {
			delete(s.shells, id)
		}
	}
	return len(s.shells)
}

// Shells reports how many shells the helper is keeping.
func (s *Server) Shells() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.shells)
}

// anyAttached reports whether a client is attached to one of the shells.
func (s *Server) anyAttached() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, sh := range s.shells {
		sh.mu.Lock()
		attached := sh.attached
		sh.mu.Unlock()
		if attached {
			return true
		}
	}
	return false
}
