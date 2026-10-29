package boostclient

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/server"
	"github.com/majordaftapps/sshpeaches/tools/boost/internal/wire"
)

type helper struct {
	srv   *server.Server
	token string
}

func startHelper(t *testing.T, tcp bool) *helper {
	t.Helper()
	token := make([]byte, wire.TokenSize)
	rand.Read(token)
	srv, err := server.New(server.Config{Token: token, Idle: time.Minute, TCP: tcp, Listen: "127.0.0.1:0"})
	if err != nil {
		t.Fatal(err)
	}
	go srv.Serve()
	t.Cleanup(srv.Close)
	return &helper{srv: srv, token: hex.EncodeToString(token)}
}

func (h *helper) dial(t *testing.T, udp bool, tcp bool) *Session {
	t.Helper()
	udpPort := 0
	if udp {
		udpPort = h.srv.UDPPort()
	}
	s, err := Dial("127.0.0.1", udpPort, h.srv.TCPPort(), h.srv.CertHash(), h.token, tcp, 3000)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(s.Close)
	return s
}

type progress struct {
	mu   sync.Mutex
	done map[int]int64
}

func (p *progress) OnChunk(index int, done int64) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.done == nil {
		p.done = map[int]int64{}
	}
	p.done[index] = done
}

func chunksFor(size int64, piece int64) string {
	var c [][3]int64
	for start := int64(0); start < size; start += piece {
		c = append(c, [3]int64{start, min(size, start+piece), start})
	}
	b, _ := json.Marshal(c)
	return string(b)
}

func randomFile(t *testing.T, dir string, size int) (string, []byte) {
	t.Helper()
	data := make([]byte, size)
	rand.Read(data)
	path := filepath.Join(dir, "data.bin")
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
	return path, data
}

func download(t *testing.T, s *Session, path string, size int64, chunks string) []byte {
	t.Helper()
	out, err := os.CreateTemp(t.TempDir(), "down")
	if err != nil {
		t.Fatal(err)
	}
	defer out.Close()
	if err := out.Truncate(size); err != nil {
		t.Fatal(err)
	}
	p := &progress{}
	if err := s.StartDownload(path, int(out.Fd()), chunks, 8, p).Await(); err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(out.Name())
	return got
}

func TestDownloadOverUdpAndTcp(t *testing.T) {
	dir := t.TempDir()
	path, data := randomFile(t, dir, 9*1024*1024+12345)
	for _, transport := range []string{"udp", "tcp"} {
		t.Run(transport, func(t *testing.T) {
			h := startHelper(t, true)
			s := h.dial(t, transport == "udp", transport == "tcp")
			if s.Transport() != transport {
				t.Fatalf("transport %s, want %s", s.Transport(), transport)
			}
			if _, _, err := h.srv.AllowRead(path); err != nil {
				t.Fatal(err)
			}
			got := download(t, s, path, int64(len(data)), chunksFor(int64(len(data)), 2<<20))
			if !bytes.Equal(got, data) {
				t.Fatal("downloaded bytes differ")
			}
		})
	}
}

func TestDownloadResumesFromChunkState(t *testing.T) {
	dir := t.TempDir()
	path, data := randomFile(t, dir, 5*1024*1024)
	h := startHelper(t, false)
	s := h.dial(t, true, false)
	h.srv.AllowRead(path)
	// The first half of every chunk is already there; garbage stands in for the rest.
	piece := int64(1 << 20)
	var c [][3]int64
	out, _ := os.CreateTemp(t.TempDir(), "down")
	defer out.Close()
	out.Write(bytes.Repeat([]byte{0x55}, len(data)))
	for start := int64(0); start < int64(len(data)); start += piece {
		half := start + piece/2
		out.WriteAt(data[start:half], start)
		c = append(c, [3]int64{start, start + piece, half})
	}
	chunks, _ := json.Marshal(c)
	p := &progress{}
	if err := s.StartDownload(path, int(out.Fd()), string(chunks), 4, p).Await(); err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(out.Name())
	if !bytes.Equal(got, data) {
		t.Fatal("resumed download differs")
	}
	if len(p.done) != 5 {
		t.Fatalf("progress for %d chunks, want 5", len(p.done))
	}
}

func TestUploadOnlyToTheDeclaredPath(t *testing.T) {
	dir := t.TempDir()
	local, data := randomFile(t, dir, 3*1024*1024+7)
	h := startHelper(t, true)
	s := h.dial(t, true, false)
	f, _ := os.Open(local)
	defer f.Close()
	remote := filepath.Join(dir, "up.bin")
	undeclared := filepath.Join(dir, "other.bin")
	if err := s.StartUpload(undeclared, int(f.Fd()), chunksFor(int64(len(data)), 1<<20), 4, &progress{}).Await(); err == nil {
		t.Fatal("an undeclared path was written")
	}
	if _, err := os.Stat(undeclared); err == nil {
		t.Fatal("the undeclared file exists")
	}
	if _, err := h.srv.AllowWrite(remote, true, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := s.StartUpload(remote, int(f.Fd()), chunksFor(int64(len(data)), 1<<20), 4, &progress{}).Await(); err != nil {
		t.Fatal(err)
	}
	h.srv.Forget(remote)
	got, _ := os.ReadFile(remote)
	if !bytes.Equal(got, data) {
		t.Fatal("uploaded bytes differ")
	}
}

func TestReadingAnUndeclaredFileIsRefused(t *testing.T) {
	dir := t.TempDir()
	path, data := randomFile(t, dir, 4096)
	h := startHelper(t, false)
	s := h.dial(t, true, false)
	out, _ := os.CreateTemp(t.TempDir(), "down")
	defer out.Close()
	err := s.StartDownload(path, int(out.Fd()), chunksFor(int64(len(data)), 4096), 1, &progress{}).Await()
	if err == nil || !strings.Contains(err.Error(), "not allowed") {
		t.Fatalf("got %v, want not allowed", err)
	}
	h.srv.AllowRead(path)
	h.srv.Forget(path)
	if err := s.StartDownload(path, int(out.Fd()), chunksFor(int64(len(data)), 4096), 1, &progress{}).Await(); err == nil {
		t.Fatal("a forgotten file could still be read")
	}
}

func TestListShowsEntriesAndLinks(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "file.txt"), []byte("hello"), 0o640)
	os.Mkdir(filepath.Join(dir, "sub"), 0o755)
	os.Symlink("sub", filepath.Join(dir, "to-sub"))
	os.Symlink("file.txt", filepath.Join(dir, "to-file"))
	os.Symlink("missing", filepath.Join(dir, "broken"))
	h := startHelper(t, false)
	s := h.dial(t, true, false)
	body, err := s.List(dir)
	if err != nil {
		t.Fatal(err)
	}
	var listing wire.Listing
	if err := json.Unmarshal([]byte(body), &listing); err != nil {
		t.Fatal(err)
	}
	real, _ := filepath.EvalSymlinks(dir)
	if listing.Path != real {
		t.Fatalf("path %s, want %s", listing.Path, real)
	}
	byName := map[string]wire.Entry{}
	for _, e := range listing.Entries {
		byName[e.Name] = e
	}
	check := func(name, typ string, targetDir, broken bool) {
		e, ok := byName[name]
		if !ok || e.Type != typ || e.TargetDir != targetDir || e.Broken != broken {
			t.Errorf("%s: %+v", name, e)
		}
	}
	check("file.txt", "f", false, false)
	check("sub", "d", false, false)
	check("to-sub", "l", true, false)
	check("to-file", "l", false, false)
	check("broken", "l", false, true)
	if byName["file.txt"].Mode != 0o640 || byName["file.txt"].Size != 5 || byName["to-sub"].Target != "sub" {
		t.Errorf("details: %+v %+v", byName["file.txt"], byName["to-sub"])
	}
	if _, err := s.List(filepath.Join(dir, "nope")); err == nil {
		t.Error("listing a missing folder must fail")
	}
}

func TestAWrongTokenOrCertificateIsRefused(t *testing.T) {
	h := startHelper(t, true)
	wrong := make([]byte, wire.TokenSize)
	if _, err := Dial("127.0.0.1", h.srv.UDPPort(), h.srv.TCPPort(), h.srv.CertHash(), hex.EncodeToString(wrong), true, 2000); err == nil {
		t.Fatal("a wrong token was accepted")
	}
	otherCert := strings.Repeat("ab", 32)
	if _, err := Dial("127.0.0.1", h.srv.UDPPort(), h.srv.TCPPort(), otherCert, h.token, true, 2000); err == nil ||
		!strings.Contains(err.Error(), "certificate") {
		t.Fatalf("a wrong certificate was accepted: %v", err)
	}
	// The right one still works afterwards.
	h.dial(t, true, true)
}

func TestTcpFallbackWhenUdpIsBlocked(t *testing.T) {
	h := startHelper(t, true)
	// A UDP port nobody answers on stands in for a firewall that drops UDP.
	s, err := Dial("127.0.0.1", 9, h.srv.TCPPort(), h.srv.CertHash(), h.token, true, 1500)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	if s.Transport() != "tcp" {
		t.Fatalf("transport %s", s.Transport())
	}
	if _, err := Dial("127.0.0.1", 9, h.srv.TCPPort(), h.srv.CertHash(), h.token, false, 1500); err == nil {
		t.Fatal("TCP was used though the fallback is off")
	}
}

func TestIdleHelperEnds(t *testing.T) {
	token := make([]byte, wire.TokenSize)
	srv, err := server.New(server.Config{Token: token, Idle: 1500 * time.Millisecond, Listen: "127.0.0.1:0"})
	if err != nil {
		t.Fatal(err)
	}
	done := make(chan error, 1)
	go func() { done <- srv.Serve() }()
	select {
	case <-done:
	case <-time.After(10 * time.Second):
		t.Fatal("the idle helper is still running")
	}
	if _, err := Dial("127.0.0.1", srv.UDPPort(), 0, srv.CertHash(), hex.EncodeToString(token), false, 1000); err == nil {
		t.Fatal("an ended helper answered")
	}
}

func BenchmarkDownload(b *testing.B) {
	dir := b.TempDir()
	data := make([]byte, 64<<20)
	rand.Read(data)
	path := filepath.Join(dir, "big.bin")
	os.WriteFile(path, data, 0o644)
	token := make([]byte, wire.TokenSize)
	srv, _ := server.New(server.Config{Token: token, Idle: time.Minute, TCP: true, Listen: "127.0.0.1:0"})
	go srv.Serve()
	defer srv.Close()
	srv.AllowRead(path)
	for _, transport := range []string{"udp", "tcp"} {
		b.Run(transport, func(b *testing.B) {
			udp := srv.UDPPort()
			if transport == "tcp" {
				udp = 0
			}
			s, err := Dial("127.0.0.1", udp, srv.TCPPort(), srv.CertHash(), hex.EncodeToString(token), true, 2000)
			if err != nil {
				b.Fatal(err)
			}
			defer s.Close()
			b.SetBytes(int64(len(data)))
			for i := 0; i < b.N; i++ {
				out, _ := os.CreateTemp(b.TempDir(), "down")
				if err := s.StartDownload(path, int(out.Fd()), chunksFor(int64(len(data)), 4<<20), 16, &progress{}).Await(); err != nil {
					b.Fatal(err)
				}
				out.Close()
			}
		})
	}
	_ = fmt.Sprint
}

func TestHelpersTakeFreePortsFromTheRange(t *testing.T) {
	// Two adjacent free ports make the range.
	var low int
	for attempt := 0; attempt < 50 && low == 0; attempt++ {
		probe, err := net.ListenPacket("udp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		p := probe.LocalAddr().(*net.UDPAddr).Port
		probe.Close()
		if p >= 65535 {
			continue
		}
		if next, err := net.ListenPacket("udp", fmt.Sprintf("127.0.0.1:%d", p+1)); err == nil {
			next.Close()
			low = p
		}
	}
	if low == 0 {
		t.Skip("no two adjacent free UDP ports")
	}
	token := make([]byte, wire.TokenSize)
	ports := map[int]bool{}
	for i := 0; i < 2; i++ {
		srv, err := server.New(server.Config{Token: token, Idle: time.Minute, TCP: true, Listen: "127.0.0.1:0", PortMin: low, PortMax: low + 1})
		if err != nil {
			t.Fatal(err)
		}
		defer srv.Close()
		if p := srv.UDPPort(); p != low && p != low+1 {
			t.Fatalf("port %d is outside %d-%d", p, low, low+1)
		}
		ports[srv.UDPPort()] = true
	}
	if len(ports) != 2 {
		t.Fatalf("both helpers took %v", ports)
	}
	if _, err := server.New(server.Config{Token: token, Listen: "127.0.0.1:0", PortMin: low, PortMax: low + 1}); err == nil ||
		!strings.Contains(err.Error(), "no free UDP port") {
		t.Fatalf("a third helper in a full range: %v", err)
	}
}
