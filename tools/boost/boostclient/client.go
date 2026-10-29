// Package boostclient is the app's half of SSHPeaches' file boost, bound to Kotlin with gomobile. It talks to the
// sshpeaches-boost helper the app started over SSH: QUIC over UDP, or TLS over TCP when UDP doesn't get through.
// The helper's certificate is pinned by the SHA-256 the helper reported over SSH, and every request carries the
// token the app gave it there.
package boostclient

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"strconv"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/server"
	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
	"github.com/quic-go/quic-go"
)

// Piece is how much one request moves; Workers requests are in flight at once.
const Piece = 1 << 20

// Session is a connection to one helper.
type Session struct {
	token     []byte
	transport string
	quicConn  quic.Connection
	tcpAddr   string
	tlsConf   *tls.Config
	// Where shells dial, each attachment afresh: udpAddr when UDP is allowed, tcpFallback when TCP is.
	udpAddr     string
	tcpFallback string
	tcpPool     chan net.Conn
	closed      atomic.Bool
}

// Dial connects to the helper at host: QUIC on udpPort first, then (when allowTCP and tcpPort > 0) TLS over TCP.
// certSha256 and tokenHex are what the app exchanged with the helper over SSH.
func Dial(host string, udpPort, tcpPort int, certSha256, tokenHex string, allowTCP bool, timeoutMs int) (*Session, error) {
	token, err := hex.DecodeString(tokenHex)
	if err != nil || len(token) != wire.TokenSize {
		return nil, errors.New("bad token")
	}
	pinned, err := hex.DecodeString(certSha256)
	if err != nil || len(pinned) != sha256.Size {
		return nil, errors.New("bad certificate fingerprint")
	}
	tlsConf := &tls.Config{
		// The certificate is self-signed and checked by fingerprint below, not by a CA.
		InsecureSkipVerify: true,
		NextProtos:         []string{wire.ALPN},
		MinVersion:         tls.VersionTLS13,
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errors.New("no certificate")
			}
			sum := sha256.Sum256(raw[0])
			if !bytes.Equal(sum[:], pinned) {
				return errors.New("certificate does not match the one the helper reported over SSH")
			}
			return nil
		},
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	s := &Session{token: token, tlsConf: tlsConf}
	if udpPort > 0 {
		s.udpAddr = net.JoinHostPort(host, strconv.Itoa(udpPort))
	}
	if allowTCP && tcpPort > 0 {
		s.tcpFallback = net.JoinHostPort(host, strconv.Itoa(tcpPort))
	}
	var errs []string
	if udpPort > 0 {
		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		conn, err := quic.DialAddr(ctx, net.JoinHostPort(host, strconv.Itoa(udpPort)), tlsConf, server.QuicConfig())
		cancel()
		if err == nil {
			s.quicConn = conn
			s.transport = "udp"
			if err = s.Ping(); err == nil {
				return s, nil
			}
			conn.CloseWithError(0, "")
			s.quicConn = nil
		}
		errs = append(errs, "UDP: "+err.Error())
	}
	if allowTCP && tcpPort > 0 {
		s.tcpAddr = net.JoinHostPort(host, strconv.Itoa(tcpPort))
		s.tcpPool = make(chan net.Conn, 64)
		s.transport = "tcp"
		if err := s.Ping(); err == nil {
			return s, nil
		} else {
			errs = append(errs, "TCP: "+err.Error())
		}
	}
	return nil, fmt.Errorf("could not reach the boost helper (%s)", joinErrs(errs))
}

func joinErrs(errs []string) string {
	if len(errs) == 0 {
		return "no transport allowed"
	}
	out := errs[0]
	for _, e := range errs[1:] {
		out += "; " + e
	}
	return out
}

// Transport is "udp" or "tcp".
func (s *Session) Transport() string { return s.transport }

func (s *Session) Close() {
	if !s.closed.CompareAndSwap(false, true) {
		return
	}
	if s.quicConn != nil {
		s.quicConn.CloseWithError(0, "")
	}
	if s.tcpPool != nil {
		for {
			select {
			case c := <-s.tcpPool:
				c.Close()
			default:
				return
			}
		}
	}
}

// stream is one request's channel: a QUIC stream, or a pooled TCP connection.
type stream struct {
	rw     io.ReadWriter
	finish func(ok bool)
}

func (s *Session) open(ctx context.Context) (*stream, error) {
	if s.closed.Load() {
		return nil, errors.New("closed")
	}
	if s.quicConn != nil {
		st, err := s.quicConn.OpenStreamSync(ctx)
		if err != nil {
			return nil, err
		}
		if deadline, ok := ctx.Deadline(); ok {
			st.SetDeadline(deadline)
		}
		return &stream{rw: st, finish: func(bool) { st.CancelRead(0); st.Close() }}, nil
	}
	var conn net.Conn
	select {
	case conn = <-s.tcpPool:
	default:
		d := tls.Dialer{NetDialer: &net.Dialer{Timeout: 10 * time.Second}, Config: s.tlsConf}
		c, err := d.DialContext(ctx, "tcp", s.tcpAddr)
		if err != nil {
			return nil, err
		}
		conn = c
	}
	if deadline, ok := ctx.Deadline(); ok {
		conn.SetDeadline(deadline)
	} else {
		conn.SetDeadline(time.Time{})
	}
	return &stream{rw: conn, finish: func(ok bool) {
		if ok && !s.closed.Load() {
			select {
			case s.tcpPool <- conn:
				return
			default:
			}
		}
		conn.Close()
	}}, nil
}

// RequestTimeout ends a request that gets no reply: over TCP a dead link would otherwise block it forever.
const RequestTimeout = 60 * time.Second

// call sends [req] (with [payload] for a write) and returns the reply's payload.
func (s *Session) call(parent context.Context, req wire.Request, payload []byte, into []byte) (int, []byte, error) {
	ctx, cancel := context.WithTimeout(parent, RequestTimeout)
	defer cancel()
	st, err := s.open(ctx)
	if err != nil {
		return 0, nil, err
	}
	ok := false
	defer func() { st.finish(ok) }()
	if err := wire.WriteRequest(st.rw, s.token, req); err != nil {
		return 0, nil, err
	}
	if len(payload) > 0 {
		if _, err := st.rw.Write(payload); err != nil {
			return 0, nil, err
		}
	}
	rep, err := wire.ReadReply(st.rw)
	if err != nil {
		return 0, nil, err
	}
	if rep.Error != "" {
		ok = true
		return 0, nil, &RemoteError{rep.Error}
	}
	if req.Op == "write" || rep.N == 0 {
		ok = true
		return int(rep.N), nil, nil
	}
	if rep.N < 0 || (into != nil && rep.N > int64(len(into))) || rep.N > 64<<20 {
		return 0, nil, errors.New("reply too large")
	}
	buf := into
	if buf == nil {
		buf = make([]byte, rep.N)
	}
	if _, err := io.ReadFull(st.rw, buf[:rep.N]); err != nil {
		return 0, nil, err
	}
	ok = true
	return int(rep.N), buf[:rep.N], nil
}

// RemoteError is an error the helper reported (no such file, not allowed): the connection is fine and retrying
// won't help. Its text starts with RemoteErrorPrefix, which is how the app tells it from a failed connection.
type RemoteError struct{ Message string }

const RemoteErrorPrefix = "server: "

func (e *RemoteError) Error() string { return RemoteErrorPrefix + e.Message }

func (s *Session) Ping() error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, _, err := s.call(ctx, wire.Request{Op: "ping"}, nil, nil)
	return err
}

// List returns the folder at [path] as JSON: {"path": "...", "entries": [{"n","t","s","m","p","lt","ld","lb"}]}.
func (s *Session) List(path string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	_, body, err := s.call(ctx, wire.Request{Op: "list", Path: path}, nil, nil)
	if err != nil {
		return "", err
	}
	return string(body), nil
}

// Progress hears about a transfer as it goes: chunk [index] now has every byte below [done].
type Progress interface {
	OnChunk(index int, done int64)
}

// Transfer is a running download or upload.
type Transfer struct {
	cancel context.CancelFunc
	done   chan struct{}
	err    error
}

// Await blocks until the transfer ends and returns its error, if any.
func (t *Transfer) Await() error {
	<-t.done
	return t.err
}

func (t *Transfer) Cancel() { t.cancel() }

type chunk struct {
	Index      int
	Start, End int64
	Done       int64
}

func parseChunks(chunksJSON string) ([]chunk, error) {
	var raw [][3]int64
	if err := json.Unmarshal([]byte(chunksJSON), &raw); err != nil {
		return nil, err
	}
	chunks := make([]chunk, 0, len(raw))
	for i, c := range raw {
		if c[2] < c[1] {
			chunks = append(chunks, chunk{Index: i, Start: c[0], End: c[1], Done: c[2]})
		}
	}
	return chunks, nil
}

// StartDownload reads [path] (which the app allowed over SSH) into file descriptor [fd] at the same offsets.
// [chunksJSON] is [[start, end, done], ...]: the parts still missing are fetched by [workers] parallel workers, a
// chunk at a time, and progress is reported per chunk so the app can resume.
func (s *Session) StartDownload(path string, fd int, chunksJSON string, workers int, progress Progress) *Transfer {
	return s.start(fd, chunksJSON, workers, progress, func(ctx context.Context, f *os.File, c *chunk, buf []byte) error {
		for c.Done < c.End {
			length := min(int64(Piece), c.End-c.Done)
			n, data, err := s.call(ctx, wire.Request{Op: "read", Path: path, Offset: c.Done, Length: length}, nil, buf)
			if err != nil {
				return err
			}
			if n == 0 {
				return errors.New("the remote file is shorter than expected")
			}
			if _, err := f.WriteAt(data, c.Done); err != nil {
				return err
			}
			c.Done += int64(n)
			progress.OnChunk(c.Index, c.Done)
		}
		return nil
	})
}

// StartUpload writes file descriptor [fd] to [path] (which the app allowed over SSH), the same way.
func (s *Session) StartUpload(path string, fd int, chunksJSON string, workers int, progress Progress) *Transfer {
	return s.start(fd, chunksJSON, workers, progress, func(ctx context.Context, f *os.File, c *chunk, buf []byte) error {
		for c.Done < c.End {
			length := min(int64(Piece), c.End-c.Done)
			n, err := f.ReadAt(buf[:length], c.Done)
			if err != nil && !errors.Is(err, io.EOF) {
				return err
			}
			if int64(n) != length {
				return errors.New("the local file is shorter than expected")
			}
			if _, _, err := s.call(ctx, wire.Request{Op: "write", Path: path, Offset: c.Done, Length: length}, buf[:length], nil); err != nil {
				return err
			}
			c.Done += length
			progress.OnChunk(c.Index, c.Done)
		}
		return nil
	})
}

func (s *Session) start(fd int, chunksJSON string, workers int, progress Progress, run func(context.Context, *os.File, *chunk, []byte) error) *Transfer {
	ctx, cancel := context.WithCancel(context.Background())
	t := &Transfer{cancel: cancel, done: make(chan struct{})}
	go func() {
		defer close(t.done)
		defer cancel()
		chunks, err := parseChunks(chunksJSON)
		if err != nil {
			t.err = err
			return
		}
		// Our own copy of the descriptor: the app keeps and closes its own.
		dup, err := syscall.Dup(fd)
		if err != nil {
			t.err = err
			return
		}
		f := os.NewFile(uintptr(dup), "transfer")
		defer f.Close()
		if workers < 1 {
			workers = 1
		}
		queue := make(chan *chunk, len(chunks))
		for i := range chunks {
			queue <- &chunks[i]
		}
		close(queue)
		var wg sync.WaitGroup
		var once sync.Once
		for w := 0; w < workers; w++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				buf := make([]byte, Piece)
				for c := range queue {
					if ctx.Err() != nil {
						return
					}
					if err := run(ctx, f, c, buf); err != nil {
						once.Do(func() {
							t.err = err
							cancel()
						})
						return
					}
				}
			}()
		}
		wg.Wait()
		if t.err == nil && ctx.Err() != nil {
			t.err = errors.New("cancelled")
		}
	}()
	return t
}
