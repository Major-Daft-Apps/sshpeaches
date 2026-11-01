package server

import (
	"crypto/tls"
	"net"
	"strconv"
	"testing"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
)

func startForTest(t *testing.T) (*Server, []byte) {
	t.Helper()
	token := make([]byte, wire.TokenSize)
	token[0] = 7
	srv, err := New(Config{Token: token, Idle: time.Minute, TCP: true, Listen: "127.0.0.1:0"})
	if err != nil {
		t.Fatal(err)
	}
	go srv.Serve()
	t.Cleanup(srv.Close)
	return srv, token
}

func tcpAddr(srv *Server) string { return net.JoinHostPort("127.0.0.1", strconv.Itoa(srv.TCPPort())) }

// closedWithin says whether the server hung up on conn within d.
func closedWithin(conn net.Conn, d time.Duration) bool {
	conn.SetReadDeadline(time.Now().Add(d))
	buf := make([]byte, 64)
	for {
		if _, err := conn.Read(buf); err != nil {
			ne, ok := err.(net.Error)
			return !(ok && ne.Timeout())
		}
	}
}

func pingOverTCP(t *testing.T, srv *Server, token []byte) error {
	t.Helper()
	conn, err := tls.DialWithDialer(&net.Dialer{Timeout: 2 * time.Second}, "tcp", tcpAddr(srv),
		&tls.Config{InsecureSkipVerify: true, NextProtos: []string{wire.ALPN}})
	if err != nil {
		return err
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(3 * time.Second))
	if err := wire.WriteRequest(conn, token, wire.Request{Op: "ping"}); err != nil {
		return err
	}
	_, err = wire.ReadReply(conn)
	return err
}

func TestASilentPeerIsCutOffAndDoesNotBlockTheClient(t *testing.T) {
	old := authTimeout
	authTimeout = 300 * time.Millisecond
	defer func() { authTimeout = old }()
	srv, token := startForTest(t)

	// Never finishes the TLS handshake.
	raw, err := net.Dial("tcp", tcpAddr(srv))
	if err != nil {
		t.Fatal(err)
	}
	defer raw.Close()
	if !closedWithin(raw, 3*time.Second) {
		t.Fatal("a connection that never sent anything was kept open")
	}
	if err := pingOverTCP(t, srv, token); err != nil {
		t.Fatalf("the client was refused after a silent peer: %v", err)
	}
}

func TestUnauthenticatedConnectionsAreCapped(t *testing.T) {
	old := authTimeout
	authTimeout = 2 * time.Second
	defer func() { authTimeout = old }()
	srv, token := startForTest(t)

	var silent []net.Conn
	defer func() {
		for _, c := range silent {
			c.Close()
		}
	}()
	for i := 0; i < maxUnauthenticated; i++ {
		c, err := net.Dial("tcp", tcpAddr(srv))
		if err != nil {
			t.Fatal(err)
		}
		silent = append(silent, c)
	}
	time.Sleep(200 * time.Millisecond)
	extra, err := net.Dial("tcp", tcpAddr(srv))
	if err != nil {
		t.Fatal(err)
	}
	defer extra.Close()
	if !closedWithin(extra, time.Second) {
		t.Fatal("a connection past the cap was kept")
	}
	// Once the silent ones time out, the real client gets in.
	time.Sleep(authTimeout + 500*time.Millisecond)
	if err := pingOverTCP(t, srv, token); err != nil {
		t.Fatalf("the client was refused after the silent connections timed out: %v", err)
	}
}

func TestAWrongTokenIsRefusedBeforeAnyHeaderIsRead(t *testing.T) {
	srv, _ := startForTest(t)
	conn, err := tls.Dial("tcp", tcpAddr(srv), &tls.Config{InsecureSkipVerify: true, NextProtos: []string{wire.ALPN}})
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	// A wrong token, then a header length announcing 64 KiB that never comes.
	conn.Write(make([]byte, wire.TokenSize))
	conn.Write([]byte{0, 1, 0, 0})
	if !closedWithin(conn, 2*time.Second) {
		t.Fatal("the server waited for the header of a request with a wrong token")
	}
}
