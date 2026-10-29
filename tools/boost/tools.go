//go:build tools

// Package tools pins golang.org/x/mobile, which gomobile bind needs in this module, so go mod tidy keeps it.
package tools

import _ "golang.org/x/mobile/bind"
