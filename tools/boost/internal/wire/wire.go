// Package wire is the request format the SSHPeaches boost helper and client share.
//
// A request is: the 32-byte session token, a 4-byte big-endian length, a JSON Request of that length, and for a
// write the payload. The reply is a 4-byte length, a JSON Reply, and the payload Reply.N says (file data, or the
// listing's JSON). Over QUIC each request has a stream of its own; over TCP a connection carries requests one after
// another.
//
// A shell request keeps its stream (or TCP connection) for the terminal: after the reply, both sides send shell
// frames (a type byte, a 4-byte big-endian length, the payload) until the shell ends or the stream breaks.
package wire

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
)

const (
	TokenSize = 32
	// MaxHeader caps a request or reply header; listings travel as payload, not in the header.
	MaxHeader = 64 << 10
	// MaxPiece caps the data one request reads or writes.
	MaxPiece = 4 << 20
	ALPN     = "sshpeaches-boost/1"
)

type Request struct {
	Op     string `json:"op"` // ping, list, read, write, shell
	Path   string `json:"path,omitempty"`
	Offset int64  `json:"offset,omitempty"` // for shell: the output offset to resume from
	Length int64  `json:"length,omitempty"`
	// Shell: ID is empty to start a shell, or the one to reattach to; Rows, Cols and Term size a new one.
	ID   string `json:"id,omitempty"`
	Rows int    `json:"rows,omitempty"`
	Cols int    `json:"cols,omitempty"`
	Term string `json:"term,omitempty"`
}

type Reply struct {
	Error string `json:"error,omitempty"`
	N     int64  `json:"n"`
	// Shell: its ID, the output offset the frames that follow start at (later than asked when that output is no
	// longer kept), and how many input bytes the shell has been given so far.
	ID    string `json:"id,omitempty"`
	Start int64  `json:"start,omitempty"`
	In    int64  `json:"in,omitempty"`
}

// Shell frame types.
const (
	FrameInput  = 'i' // client: typed bytes
	FrameResize = 'r' // client: rows and columns, 2 bytes each
	FrameHangup = 'h' // client: end the shell (the session was closed)
	FramePing   = 'p' // either way: liveness; a ping is answered with a ping
	FrameOutput = 'o' // helper: the terminal's output
	FrameAck    = 'a' // helper: input bytes given to the shell so far, 8 bytes
	FrameExit   = 'x' // helper: the shell ended; its exit status, 4 bytes (signed)
	// MaxFrame caps one frame's payload.
	MaxFrame = 64 << 10
)

// WriteShellFrame writes one frame in a single Write; callers serialize writers.
func WriteShellFrame(w io.Writer, kind byte, payload []byte) error {
	if len(payload) > MaxFrame {
		return errors.New("frame too large")
	}
	frame := make([]byte, 5+len(payload))
	frame[0] = kind
	binary.BigEndian.PutUint32(frame[1:5], uint32(len(payload)))
	copy(frame[5:], payload)
	_, err := w.Write(frame)
	return err
}

// ReadShellFrame reads one frame into buf (MaxFrame bytes at least) and returns its type and payload.
func ReadShellFrame(r io.Reader, buf []byte) (byte, []byte, error) {
	var head [5]byte
	if _, err := io.ReadFull(r, head[:]); err != nil {
		return 0, nil, err
	}
	n := binary.BigEndian.Uint32(head[1:])
	if n > MaxFrame || int(n) > len(buf) {
		return 0, nil, fmt.Errorf("frame of %d bytes is too large", n)
	}
	if _, err := io.ReadFull(r, buf[:n]); err != nil {
		return 0, nil, err
	}
	return head[0], buf[:n], nil
}

// Entry is one directory entry of a listing. Type is d (folder), f (regular file), l (symbolic link) or o (other);
// for links, Target is the link's text and TargetDir / Broken say what it points at.
type Entry struct {
	Name      string `json:"n"`
	Type      string `json:"t"`
	Size      int64  `json:"s"`
	Mtime     int64  `json:"m"`
	Mode      uint32 `json:"p"`
	Target    string `json:"lt,omitempty"`
	TargetDir bool   `json:"ld,omitempty"`
	Broken    bool   `json:"lb,omitempty"`
}

type Listing struct {
	Path    string  `json:"path"`
	Entries []Entry `json:"entries"`
}

func writeFrame(w io.Writer, v any) error {
	body, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if len(body) > MaxHeader {
		return errors.New("header too large")
	}
	var size [4]byte
	binary.BigEndian.PutUint32(size[:], uint32(len(body)))
	if _, err := w.Write(size[:]); err != nil {
		return err
	}
	_, err = w.Write(body)
	return err
}

func readFrame(r io.Reader, v any) error {
	var size [4]byte
	if _, err := io.ReadFull(r, size[:]); err != nil {
		return err
	}
	n := binary.BigEndian.Uint32(size[:])
	if n > MaxHeader {
		return fmt.Errorf("header of %d bytes is too large", n)
	}
	body := make([]byte, n)
	if _, err := io.ReadFull(r, body); err != nil {
		return err
	}
	return json.Unmarshal(body, v)
}

func WriteRequest(w io.Writer, token []byte, req Request) error {
	if len(token) != TokenSize {
		return errors.New("bad token size")
	}
	if _, err := w.Write(token); err != nil {
		return err
	}
	return writeFrame(w, req)
}

// ReadRequest reads a request's token and header; the caller reads a write's payload.
func ReadRequest(r io.Reader) (token []byte, req Request, err error) {
	if token, err = ReadToken(r); err != nil {
		return nil, req, err
	}
	req, err = ReadHeader(r)
	return token, req, err
}

// ReadToken reads the token a request starts with, so a server can refuse a peer before reading (and allocating
// for) the rest.
func ReadToken(r io.Reader) ([]byte, error) {
	token := make([]byte, TokenSize)
	if _, err := io.ReadFull(r, token); err != nil {
		return nil, err
	}
	return token, nil
}

// ReadHeader reads the header that follows a request's token.
func ReadHeader(r io.Reader) (Request, error) {
	var req Request
	err := readFrame(r, &req)
	return req, err
}

func WriteReply(w io.Writer, rep Reply) error { return writeFrame(w, rep) }

func ReadReply(r io.Reader) (Reply, error) {
	var rep Reply
	err := readFrame(r, &rep)
	return rep, err
}
