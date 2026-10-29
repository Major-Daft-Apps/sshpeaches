// Command sshpeaches-boost is the server half of SSHPeaches' file boost. The app starts it over SSH as
// `sshpeaches-boost serve` and talks to it on stdin/stdout:
//
//	app -> helper, first line:   {"token":"<64 hex>","idle":600,"tcp":true,"portMin":60000,"portMax":61000,"shell":true}
//	helper -> app:               {"event":"ready","udp":<port>,"tcp":<port or 0>,"cert":"<sha256 hex>","version":1}
//	app -> helper, later lines:  {"id":1,"op":"allow-read","path":"..."}
//	                             {"id":2,"op":"allow-write","path":"...","truncate":true,"perm":420}
//	                             {"id":3,"op":"forget","path":"..."}
//	helper -> app:               {"id":1,"ok":true,"path":"/abs/path","size":123} or {"id":1,"error":"..."}
//
// The helper ends when stdin closes (the SSH channel ended) or after the idle time; with "shell", a shell it runs
// keeps it going until that shell ends (see server.Config.Shell).
package main

import (
	"bufio"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/majordaftapps/sshpeaches/tools/boost/internal/server"
)

const version = 1

type startup struct {
	Token   string `json:"token"`
	Idle    int    `json:"idle"`
	TCP     bool   `json:"tcp"`
	TCPPort int    `json:"tcpPort"`
	PortMin int    `json:"portMin"`
	PortMax int    `json:"portMax"`
	Shell   bool   `json:"shell"`
}

type control struct {
	ID       int64  `json:"id"`
	Op       string `json:"op"`
	Path     string `json:"path"`
	Truncate bool   `json:"truncate"`
	Perm     uint32 `json:"perm"`
}

type answer struct {
	ID    int64  `json:"id"`
	OK    bool   `json:"ok,omitempty"`
	Path  string `json:"path,omitempty"`
	Size  int64  `json:"size,omitempty"`
	Error string `json:"error,omitempty"`
}

func main() {
	// Once the SSH channel is gone, writing to it or its hangup must not kill a helper that keeps a shell running.
	// Caught, not ignored: an ignored signal would stay ignored in the shell and everything it starts.
	signal.Notify(make(chan os.Signal, 1), syscall.SIGPIPE, syscall.SIGHUP)
	if len(os.Args) == 2 && os.Args[1] == "version" {
		fmt.Println(version)
		return
	}
	if len(os.Args) != 2 || os.Args[1] != "serve" {
		fmt.Fprintln(os.Stderr, "usage: sshpeaches-boost serve | version")
		os.Exit(2)
	}
	in := bufio.NewScanner(os.Stdin)
	in.Buffer(make([]byte, 64<<10), 64<<10)
	out := json.NewEncoder(os.Stdout)
	var outMu sync.Mutex
	say := func(v any) {
		outMu.Lock()
		defer outMu.Unlock()
		_ = out.Encode(v)
	}
	if !in.Scan() {
		os.Exit(1)
	}
	var start startup
	if err := json.Unmarshal(in.Bytes(), &start); err != nil {
		say(map[string]string{"event": "error", "error": "bad startup line"})
		os.Exit(1)
	}
	token, err := hex.DecodeString(start.Token)
	if err != nil {
		say(map[string]string{"event": "error", "error": "bad token"})
		os.Exit(1)
	}
	srv, err := server.New(server.Config{Token: token, Idle: time.Duration(start.Idle) * time.Second, TCP: start.TCP, TCPPort: start.TCPPort,
		PortMin: start.PortMin, PortMax: start.PortMax, Shell: start.Shell})
	if err != nil {
		say(map[string]string{"event": "error", "error": err.Error()})
		os.Exit(1)
	}
	say(map[string]any{"event": "ready", "udp": srv.UDPPort(), "tcp": srv.TCPPort(), "cert": srv.CertHash(), "version": version})
	go func() {
		_ = srv.Serve()
		os.Exit(0)
	}()
	for in.Scan() {
		var c control
		if err := json.Unmarshal(in.Bytes(), &c); err != nil {
			continue
		}
		a := answer{ID: c.ID}
		switch c.Op {
		case "allow-read":
			a.Path, a.Size, err = srv.AllowRead(c.Path)
		case "allow-write":
			perm := os.FileMode(c.Perm & 0o777)
			if perm == 0 {
				perm = 0o644
			}
			a.Path, err = srv.AllowWrite(c.Path, c.Truncate, perm)
		case "forget":
			srv.Forget(c.Path)
			err = nil
		default:
			err = fmt.Errorf("unknown op %q", c.Op)
		}
		if err != nil {
			a.Error = err.Error()
		} else {
			a.OK = true
		}
		say(a)
	}
	// stdin closed: the SSH channel that started us is gone. Shells carry on; Serve exits once they've ended.
	srv.SSHGone()
	select {}
}
