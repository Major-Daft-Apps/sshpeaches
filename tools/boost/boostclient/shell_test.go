package boostclient

import (
	"crypto/rand"
	"encoding/hex"
	"net"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/server"
	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
)

type shellEvents struct {
	mu     sync.Mutex
	out    strings.Builder
	states []string
	exit   chan int
}

func newShellEvents() *shellEvents { return &shellEvents{exit: make(chan int, 1)} }

func (e *shellEvents) OnOutput(data []byte) {
	e.mu.Lock()
	e.out.Write(data)
	e.mu.Unlock()
}

func (e *shellEvents) OnState(state, detail string) {
	e.mu.Lock()
	e.states = append(e.states, state)
	e.mu.Unlock()
}

func (e *shellEvents) OnExit(status int) { e.exit <- status }

func (e *shellEvents) output() string {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.out.String()
}

func (e *shellEvents) saw(state string) bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	for _, s := range e.states {
		if s == state {
			return true
		}
	}
	return false
}

func (e *shellEvents) waitFor(t *testing.T, text string, timeout time.Duration) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for !strings.Contains(e.output(), text) {
		if time.Now().After(deadline) {
			t.Fatalf("no %q in the output: %q", text, e.output())
		}
		time.Sleep(20 * time.Millisecond)
	}
}

func shellHelper(t *testing.T, shell bool) (*server.Server, string, chan error) {
	t.Helper()
	t.Setenv("SHELL", "/bin/sh")
	t.Setenv("HOME", t.TempDir())
	t.Setenv("ENV", "")
	token := make([]byte, wire.TokenSize)
	rand.Read(token)
	srv, err := server.New(server.Config{Token: token, Idle: time.Minute, TCP: true, Listen: "127.0.0.1:0", Shell: shell})
	if err != nil {
		t.Fatal(err)
	}
	done := make(chan error, 1)
	go func() { done <- srv.Serve() }()
	t.Cleanup(srv.Close)
	return srv, hex.EncodeToString(token), done
}

func TestShellRunsCommandsAndReportsItsExit(t *testing.T) {
	srv, token, _ := shellHelper(t, true)
	s, err := Dial("127.0.0.1", srv.UDPPort(), srv.TCPPort(), srv.CertHash(), token, true, 3000)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	ev := newShellEvents()
	sh, err := s.StartShell(24, 80, "xterm-256color", ev)
	if err != nil {
		t.Fatal(err)
	}
	sh.Write([]byte("echo \"term=$TERM size=$(stty size)\" hello-$((40+2))\n"))
	ev.waitFor(t, "hello-42", 5*time.Second)
	if !strings.Contains(ev.output(), "term=xterm-256color size=24 80") {
		t.Fatalf("terminal type or size wrong: %q", ev.output())
	}
	sh.Resize(30, 100)
	time.Sleep(200 * time.Millisecond)
	sh.Write([]byte("echo now-$(stty size | tr ' ' x)\n"))
	ev.waitFor(t, "now-30x100", 5*time.Second)
	sh.Write([]byte("exit 3\n"))
	select {
	case status := <-ev.exit:
		if status != 3 {
			t.Fatalf("exit status %d, want 3", status)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("no exit")
	}
	deadline := time.Now().Add(3 * time.Second)
	for srv.Shells() != 0 {
		if time.Now().After(deadline) {
			t.Fatal("the helper kept the ended shell")
		}
		time.Sleep(50 * time.Millisecond)
	}
}

func TestShellsAreOffUnlessTheAppAskedForThem(t *testing.T) {
	srv, token, _ := shellHelper(t, false)
	s, err := Dial("127.0.0.1", srv.UDPPort(), srv.TCPPort(), srv.CertHash(), token, true, 3000)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	if _, err := s.StartShell(24, 80, "xterm", newShellEvents()); err == nil || !strings.Contains(err.Error(), "aren't enabled") {
		t.Fatalf("got %v, want shells aren't enabled", err)
	}
}

func TestShellReattachesWithoutLosingOrRepeatingInput(t *testing.T) {
	for _, transport := range []string{"udp", "tcp"} {
		t.Run(transport, func(t *testing.T) {
			srv, token, _ := shellHelper(t, true)
			udp := srv.UDPPort()
			if transport == "tcp" {
				udp = 0
			}
			s, err := Dial("127.0.0.1", udp, srv.TCPPort(), srv.CertHash(), token, true, 3000)
			if err != nil {
				t.Fatal(err)
			}
			defer s.Close()
			ev := newShellEvents()
			sh, err := s.StartShell(24, 80, "xterm", ev)
			if err != nil {
				t.Fatal(err)
			}
			defer sh.Close(true)
			sh.Write([]byte("stty -echo; echo ready\n"))
			ev.waitFor(t, "ready", 5*time.Second)
			// Output made while no client is attached must arrive after reattaching.
			sh.Write([]byte("sleep 1; echo while-away\n"))
			time.Sleep(100 * time.Millisecond)
			sh.Kick()
			for i := 0; i < 5; i++ {
				sh.Write([]byte("echo line-$((" + strconv.Itoa(i) + "+100))\n"))
			}
			ev.waitFor(t, "while-away", 8*time.Second)
			ev.waitFor(t, "line-104", 8*time.Second)
			out := ev.output()
			for i := 100; i <= 104; i++ {
				if n := strings.Count(out, "line-"+strconv.Itoa(i)); n != 1 {
					t.Fatalf("line-%d appears %d times: %q", i, n, out)
				}
			}
			if !ev.saw("reconnecting") || !ev.saw("connected") {
				t.Fatalf("states %v", ev.states)
			}
		})
	}
}

// relay forwards UDP between the client and the helper and can black-hole it, like a network that went away
// without saying so.
type relay struct {
	conn    *net.UDPConn
	target  *net.UDPAddr
	dropped atomic.Bool
}

func newRelay(t *testing.T, target int) *relay {
	t.Helper()
	conn, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	r := &relay{conn: conn, target: &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: target}}
	t.Cleanup(func() { conn.Close() })
	// One upstream socket per client address, so a reconnect from a new socket looks like a new client.
	var mu sync.Mutex
	upstreams := map[string]*net.UDPConn{}
	go func() {
		buf := make([]byte, 65536)
		for {
			n, from, err := conn.ReadFromUDP(buf)
			if err != nil {
				return
			}
			if r.dropped.Load() {
				continue
			}
			mu.Lock()
			up := upstreams[from.String()]
			if up == nil {
				up, err = net.DialUDP("udp", nil, r.target)
				if err != nil {
					mu.Unlock()
					continue
				}
				upstreams[from.String()] = up
				client := from
				go func() {
					back := make([]byte, 65536)
					for {
						n, err := up.Read(back)
						if err != nil {
							return
						}
						if !r.dropped.Load() {
							conn.WriteToUDP(back[:n], client)
						}
					}
				}()
			}
			mu.Unlock()
			up.Write(buf[:n])
		}
	}()
	return r
}

func (r *relay) port() int { return r.conn.LocalAddr().(*net.UDPAddr).Port }

func TestShellNoticesASilentlyDeadPathAndComesBack(t *testing.T) {
	srv, token, _ := shellHelper(t, true)
	r := newRelay(t, srv.UDPPort())
	s, err := Dial("127.0.0.1", r.port(), 0, srv.CertHash(), token, false, 3000)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	ev := newShellEvents()
	sh, err := s.StartShell(24, 80, "xterm", ev)
	if err != nil {
		t.Fatal(err)
	}
	defer sh.Close(true)
	sh.Write([]byte("stty -echo; echo before\n"))
	ev.waitFor(t, "before", 5*time.Second)
	r.dropped.Store(true)
	sh.Write([]byte("echo typed-$((2*21))-in-the-dark\n"))
	deadline := time.Now().Add(shellDeadAfter + 5*time.Second)
	for !ev.saw("reconnecting") {
		if time.Now().After(deadline) {
			t.Fatal("the dead path wasn't noticed")
		}
		time.Sleep(100 * time.Millisecond)
	}
	r.dropped.Store(false)
	ev.waitFor(t, "typed-42-in-the-dark", 15*time.Second)
	if n := strings.Count(ev.output(), "typed-42"); n != 1 {
		t.Fatalf("the input ran %d times", n)
	}
}

func TestHelperOutlivesSSHWhileItsShellRuns(t *testing.T) {
	srv, token, done := shellHelper(t, true)
	s, err := Dial("127.0.0.1", srv.UDPPort(), srv.TCPPort(), srv.CertHash(), token, true, 3000)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	ev := newShellEvents()
	sh, err := s.StartShell(24, 80, "xterm", ev)
	if err != nil {
		t.Fatal(err)
	}
	srv.SSHGone()
	select {
	case <-done:
		t.Fatal("the helper ended with its shell still running")
	case <-time.After(2500 * time.Millisecond):
	}
	sh.Write([]byte("echo still-here\n"))
	ev.waitFor(t, "still-here", 5*time.Second)
	// Closing the session hangs the shell up, and then the helper ends.
	sh.Close(true)
	select {
	case <-done:
	case <-time.After(10 * time.Second):
		t.Fatalf("the helper is still running with %d shells", srv.Shells())
	}
}

func TestHelperWithoutShellsEndsWithSSH(t *testing.T) {
	srv, _, done := shellHelper(t, true)
	srv.SSHGone()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("the helper outlived SSH with no shell")
	}
}

func TestLotsOfOutputArrivesWhole(t *testing.T) {
	srv, token, _ := shellHelper(t, true)
	s, err := Dial("127.0.0.1", srv.UDPPort(), 0, srv.CertHash(), token, false, 3000)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	ev := newShellEvents()
	sh, err := s.StartShell(24, 80, "xterm", ev)
	if err != nil {
		t.Fatal(err)
	}
	defer sh.Close(true)
	// About 3.4 MB: more than the helper keeps for reattaching, so only backpressure keeps it whole.
	sh.Write([]byte("stty -echo; seq 1 500000; echo seq-$((1+1))-done\n"))
	ev.waitFor(t, "seq-2-done", 30*time.Second)
	out := strings.ReplaceAll(ev.output(), "\r\n", "\n")
	// The prompt may sit in front of the first number.
	start := strings.Index(out, " 1\n2\n")
	if start < 0 {
		start = strings.Index(out, "\n1\n2\n")
	}
	if start < 0 {
		t.Fatalf("no start of the sequence: %q ... (%d bytes)", out[:min(len(out), 300)], len(out))
	}
	lines := strings.Split(out[start+1:strings.Index(out, "seq-2-done")], "\n")
	for i := 0; i < 500000; i++ {
		if lines[i] != strconv.Itoa(i+1) {
			t.Fatalf("line %d is %q", i+1, lines[i])
		}
	}
}
