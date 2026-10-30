// Package server is the SSHPeaches boost helper: it serves one user's file browser, and with Config.Shell their
// terminal, over QUIC (UDP), with an optional TLS-over-TCP fallback.
//
// Security model:
//   - The helper runs as the SSH user, started over that user's SSH login. It lives as long as that SSH channel
//     (stdin closing ends it) or until it has been idle for Config.Idle; a shell it runs keeps it going after the
//     channel ends (the point: the terminal survives the connection), until the shell ends or no client has been
//     attached for Config.DetachedFor.
//   - Listeners use a fresh self-signed certificate; the client pins its SHA-256, which it got over SSH.
//   - Every request carries the 32-byte token the client sent over SSH; it is compared in constant time. Only the
//     helper can read it (it travels inside TLS to the pinned certificate), so the client may come back from
//     another address after its network changes.
//   - Directory listings are read-only. File contents can be read only for paths declared with AllowRead, and
//     written only for paths declared with AllowWrite, both of which arrive over SSH, not over the network.
//   - Shells run only when the app asked for them over SSH (Config.Shell).
package server

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	mrand "math/rand/v2"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
	"github.com/quic-go/quic-go"
)

type Config struct {
	Token []byte
	// Idle ends the helper after this long without a request or control message.
	Idle time.Duration
	// TCP also listens on TCP (TLS), for networks that drop UDP.
	TCP bool
	// TCPPort, when set, is the TCP port to listen on instead of the UDP one (a random one when it's taken).
	TCPPort int
	// PortMin..PortMax, when set, is the range the helper takes a free port from, like mosh-server's 60000-61000,
	// so one firewall rule for the range lets every session's helper through. Unset means any port.
	PortMin, PortMax int
	// Listen is the address to listen on; empty means every interface on a random port.
	Listen string
	// Shell lets the client run the user's login shell (the app's terminal).
	Shell bool
	// DetachedFor ends a shell no client has been attached to for this long (default 12 hours).
	DetachedFor time.Duration
}

type Server struct {
	cfg      Config
	cert     tls.Certificate
	certHash string
	udp      *quic.Listener
	udpConn  net.PacketConn
	tcp      net.Listener

	mu       sync.Mutex
	readable map[string]*os.File
	writable map[string]*os.File
	shells   map[string]*shell
	sshGone  bool

	lastActive atomic.Int64
	closed     chan struct{}
	closeOnce  sync.Once
}

func QuicConfig() *quic.Config {
	return &quic.Config{
		MaxIncomingStreams:             1024,
		InitialStreamReceiveWindow:     2 << 20,
		MaxStreamReceiveWindow:         16 << 20,
		InitialConnectionReceiveWindow: 8 << 20,
		MaxConnectionReceiveWindow:     64 << 20,
		KeepAlivePeriod:                10 * time.Second,
		MaxIdleTimeout:                 60 * time.Second,
	}
}

func New(cfg Config) (*Server, error) {
	if len(cfg.Token) != wire.TokenSize {
		return nil, errors.New("token must be 32 bytes")
	}
	if cfg.Idle <= 0 {
		cfg.Idle = 10 * time.Minute
	}
	if cfg.DetachedFor <= 0 {
		cfg.DetachedFor = 12 * time.Hour
	}
	cert, hash, err := selfSigned()
	if err != nil {
		return nil, err
	}
	s := &Server{
		cfg: cfg, cert: cert, certHash: hash,
		readable: map[string]*os.File{}, writable: map[string]*os.File{}, shells: map[string]*shell{},
		closed: make(chan struct{}),
	}
	s.touch()
	listen := cfg.Listen
	if listen == "" {
		listen = ":0"
	}
	tlsConf := &tls.Config{Certificates: []tls.Certificate{cert}, NextProtos: []string{wire.ALPN}, MinVersion: tls.VersionTLS13}
	udpConn, err := listenUDP(listen, cfg.PortMin, cfg.PortMax)
	if err != nil {
		return nil, err
	}
	s.udpConn = udpConn
	s.udp, err = quic.Listen(udpConn, tlsConf, QuicConfig())
	if err != nil {
		udpConn.Close()
		return nil, err
	}
	if cfg.TCP {
		// The TCP port is the UDP one when it's free, so a firewall rule for one port covers both.
		tcpPort := s.UDPPort()
		if cfg.TCPPort > 0 {
			tcpPort = cfg.TCPPort
		}
		tcpListen := fmt.Sprintf("%s:%d", hostOf(listen), tcpPort)
		tcp, err := tls.Listen("tcp", tcpListen, tlsConf)
		if err != nil {
			tcp, err = tls.Listen("tcp", hostOf(listen)+":0", tlsConf)
		}
		if err == nil {
			s.tcp = tcp
		}
	}
	return s, nil
}

// listenUDP listens on listen or, given a port range, on a free port in it. The search starts at a random port so
// helpers starting together don't all try the same ones.
func listenUDP(listen string, min, max int) (net.PacketConn, error) {
	if min <= 0 || max < min {
		return net.ListenPacket("udp", listen)
	}
	n := max - min + 1
	first := mrand.IntN(n)
	var lastErr error
	for i := 0; i < n; i++ {
		port := min + (first+i)%n
		conn, err := net.ListenPacket("udp", net.JoinHostPort(hostOf(listen), strconv.Itoa(port)))
		if err == nil {
			return conn, nil
		}
		lastErr = err
	}
	return nil, fmt.Errorf("no free UDP port in %d-%d: %w", min, max, lastErr)
}

func hostOf(listen string) string {
	host, _, err := net.SplitHostPort(listen)
	if err != nil {
		return ""
	}
	return host
}

func (s *Server) UDPPort() int { return s.udpConn.LocalAddr().(*net.UDPAddr).Port }

func (s *Server) TCPPort() int {
	if s.tcp == nil {
		return 0
	}
	return s.tcp.Addr().(*net.TCPAddr).Port
}

// CertHash is the hex SHA-256 of the certificate's DER encoding, which the client pins.
func (s *Server) CertHash() string { return s.certHash }

func (s *Server) touch() { s.lastActive.Store(time.Now().UnixNano()) }

// Resolve turns a path from the client into the absolute, cleaned path the allowlists are keyed by. Relative paths
// are taken from the helper's working directory, the user's home under SSH.
func Resolve(path string) (string, error) {
	if path == "" {
		path = "."
	}
	abs, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	return filepath.Clean(abs), nil
}

// AllowRead lets the client read [path]'s contents, until the helper ends. It returns the resolved path and size.
func (s *Server) AllowRead(path string) (string, int64, error) {
	s.touch()
	abs, err := Resolve(path)
	if err != nil {
		return "", 0, err
	}
	f, err := os.Open(abs)
	if err != nil {
		return "", 0, err
	}
	info, err := f.Stat()
	if err != nil || !info.Mode().IsRegular() {
		f.Close()
		if err == nil {
			err = errors.New("not a regular file")
		}
		return "", 0, err
	}
	s.mu.Lock()
	if old := s.readable[abs]; old != nil {
		old.Close()
	}
	s.readable[abs] = f
	s.mu.Unlock()
	return abs, info.Size(), nil
}

// AllowWrite lets the client write [path], which it creates (or truncates, with [truncate]) with [perm].
func (s *Server) AllowWrite(path string, truncate bool, perm os.FileMode) (string, error) {
	s.touch()
	abs, err := Resolve(path)
	if err != nil {
		return "", err
	}
	flags := os.O_WRONLY | os.O_CREATE
	if truncate {
		flags |= os.O_TRUNC
	}
	f, err := os.OpenFile(abs, flags, perm)
	if err != nil {
		return "", err
	}
	s.mu.Lock()
	if old := s.writable[abs]; old != nil {
		old.Close()
	}
	s.writable[abs] = f
	s.mu.Unlock()
	return abs, nil
}

// Forget ends access to [path] granted earlier.
func (s *Server) Forget(path string) {
	abs, err := Resolve(path)
	if err != nil {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if f := s.readable[abs]; f != nil {
		f.Close()
		delete(s.readable, abs)
	}
	if f := s.writable[abs]; f != nil {
		f.Sync()
		f.Close()
		delete(s.writable, abs)
	}
}

// Serve accepts clients until Close, or until the helper has been idle for Config.Idle.
func (s *Server) Serve() error {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		<-s.closed
		cancel()
	}()
	go s.acceptQuic(ctx)
	if s.tcp != nil {
		go s.acceptTCP()
	}
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-s.closed:
			return nil
		case <-ticker.C:
			if s.reapShells(true) > 0 {
				// The terminal outlives SSH and idleness: it ends with its shell, or DetachedFor without a client.
				if s.anyAttached() {
					s.touch()
				}
				continue
			}
			s.mu.Lock()
			gone := s.sshGone
			s.mu.Unlock()
			if gone {
				s.Close()
				return nil
			}
			if time.Since(time.Unix(0, s.lastActive.Load())) > s.cfg.Idle {
				s.Close()
				return errors.New("idle")
			}
		}
	}
}

func (s *Server) Close() {
	s.closeOnce.Do(func() {
		close(s.closed)
		s.udp.Close()
		s.udpConn.Close()
		if s.tcp != nil {
			s.tcp.Close()
		}
		s.mu.Lock()
		for _, f := range s.readable {
			f.Close()
		}
		for _, f := range s.writable {
			f.Sync()
			f.Close()
		}
		s.mu.Unlock()
	})
}

// admit says whether a request may be served: it must carry the token.
func (s *Server) admit(token []byte) bool {
	return subtle.ConstantTimeCompare(token, s.cfg.Token) == 1
}

// SSHGone tells the helper the SSH channel that started it ended: it ends now, or once its shells have.
func (s *Server) SSHGone() {
	s.mu.Lock()
	s.sshGone = true
	s.mu.Unlock()
	if s.reapShells(false) == 0 {
		s.Close()
	}
}

func (s *Server) acceptQuic(ctx context.Context) {
	for {
		conn, err := s.udp.Accept(ctx)
		if err != nil {
			return
		}
		go func() {
			for {
				stream, err := conn.AcceptStream(ctx)
				if err != nil {
					return
				}
				go func() {
					defer stream.Close()
					// Only a request without the token cuts the connection; a broken stream (a cancelled
					// request) ends just itself, and the client's other requests carry on.
					if more, refused := s.handle(stream); refused {
						conn.CloseWithError(1, "refused")
					} else if !more {
						stream.CancelRead(0)
					}
				}()
			}
		}()
	}
}

func (s *Server) acceptTCP() {
	for {
		conn, err := s.tcp.Accept()
		if err != nil {
			return
		}
		go func() {
			defer conn.Close()
			for {
				if more, _ := s.handle(conn); !more {
					return
				}
			}
		}()
	}
}

// handle serves one request. more is false when the stream (or TCP connection) can't be used again; refused
// says the request didn't carry the token.
func (s *Server) handle(rw io.ReadWriter) (more, refused bool) {
	token, req, err := wire.ReadRequest(rw)
	if err != nil {
		return false, false
	}
	if !s.admit(token) {
		return false, true
	}
	s.touch()
	return s.serve(rw, req), false
}

func (s *Server) serve(rw io.ReadWriter, req wire.Request) bool {
	switch req.Op {
	case "ping":
		return wire.WriteReply(rw, wire.Reply{}) == nil
	case "list":
		return s.list(rw, req)
	case "read":
		return s.read(rw, req)
	case "write":
		return s.write(rw, req)
	case "shell":
		return s.serveShell(rw, req)
	default:
		return wire.WriteReply(rw, wire.Reply{Error: "unknown request"}) == nil
	}
}

func fail(rw io.Writer, err error) bool {
	return wire.WriteReply(rw, wire.Reply{Error: err.Error()}) == nil
}

func (s *Server) list(rw io.Writer, req wire.Request) bool {
	abs, err := Resolve(req.Path)
	if err != nil {
		return fail(rw, err)
	}
	// Like SFTP's realpath: the folder's own name, links resolved.
	if real, err := filepath.EvalSymlinks(abs); err == nil {
		abs = real
	}
	dir, err := os.ReadDir(abs)
	if err != nil {
		return fail(rw, err)
	}
	listing := wire.Listing{Path: abs, Entries: make([]wire.Entry, 0, len(dir))}
	for _, d := range dir {
		info, err := d.Info()
		if err != nil {
			continue
		}
		listing.Entries = append(listing.Entries, entryOf(abs, d.Name(), info))
	}
	body, err := json.Marshal(listing)
	if err != nil {
		return fail(rw, err)
	}
	if err := wire.WriteReply(rw, wire.Reply{N: int64(len(body))}); err != nil {
		return false
	}
	_, err = rw.Write(body)
	return err == nil
}

func entryOf(dir, name string, info os.FileInfo) wire.Entry {
	mode := info.Mode()
	e := wire.Entry{Name: name, Size: info.Size(), Mtime: info.ModTime().Unix(), Mode: unixMode(mode)}
	switch {
	case mode.IsDir():
		e.Type = "d"
	case mode.IsRegular():
		e.Type = "f"
	case mode&os.ModeSymlink != 0:
		e.Type = "l"
		full := filepath.Join(dir, name)
		e.Target, _ = os.Readlink(full)
		if target, err := os.Stat(full); err == nil {
			e.TargetDir = target.IsDir()
		} else {
			e.Broken = true
		}
	default:
		e.Type = "o"
	}
	return e
}

func unixMode(mode os.FileMode) uint32 {
	bits := uint32(mode.Perm())
	if mode&os.ModeSetuid != 0 {
		bits |= 0o4000
	}
	if mode&os.ModeSetgid != 0 {
		bits |= 0o2000
	}
	if mode&os.ModeSticky != 0 {
		bits |= 0o1000
	}
	return bits
}

func (s *Server) file(m map[string]*os.File, path string) (*os.File, error) {
	abs, err := Resolve(path)
	if err != nil {
		return nil, err
	}
	s.mu.Lock()
	f := m[abs]
	s.mu.Unlock()
	if f == nil {
		return nil, errors.New("not allowed")
	}
	return f, nil
}

func (s *Server) read(rw io.Writer, req wire.Request) bool {
	f, err := s.file(s.readable, req.Path)
	if err != nil {
		return fail(rw, err)
	}
	length := req.Length
	if length <= 0 || length > wire.MaxPiece || req.Offset < 0 {
		return fail(rw, errors.New("bad range"))
	}
	buf := make([]byte, length)
	n, err := f.ReadAt(buf, req.Offset)
	if err != nil && !errors.Is(err, io.EOF) {
		return fail(rw, err)
	}
	if err := wire.WriteReply(rw, wire.Reply{N: int64(n)}); err != nil {
		return false
	}
	_, err = rw.Write(buf[:n])
	return err == nil
}

func (s *Server) write(rw io.ReadWriter, req wire.Request) bool {
	length := req.Length
	if length <= 0 || length > wire.MaxPiece || req.Offset < 0 {
		// The payload can't be skipped safely: drop the connection.
		fail(rw, errors.New("bad range"))
		return false
	}
	buf := make([]byte, length)
	if _, err := io.ReadFull(rw, buf); err != nil {
		return false
	}
	f, err := s.file(s.writable, req.Path)
	if err != nil {
		return fail(rw, err)
	}
	if _, err := f.WriteAt(buf, req.Offset); err != nil {
		return fail(rw, err)
	}
	return wire.WriteReply(rw, wire.Reply{N: length}) == nil
}

func selfSigned() (tls.Certificate, string, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return tls.Certificate{}, "", err
	}
	serial, _ := rand.Int(rand.Reader, big.NewInt(1<<62))
	template := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: "sshpeaches-boost"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(48 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		return tls.Certificate{}, "", err
	}
	sum := sha256.Sum256(der)
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}, hex.EncodeToString(sum[:]), nil
}
