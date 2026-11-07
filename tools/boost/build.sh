#!/bin/bash
# Builds SSHPeaches' file boost: the server helper (static Linux binaries, shipped as app assets, which the APK
# compresses, with a manifest of their SHA-256; Android's packager would unzip a .gz asset and drop the extension) and the app's client library (an AAR bound with gomobile). Needs Go 1.26, gomobile and
# gobind (golang.org/x/mobile v0.0.0-20261011034528-197770444867), the Android SDK and NDK 27.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd ../.. && pwd)
ASSETS=$ROOT/app/src/main/assets/boost
export GOTOOLCHAIN=local GOFLAGS=-buildvcs=false CGO_ENABLED=0
# Go 1.26 or later: older cgo could put an exported function's argument block on an unaligned stack address, and the
# runtime then aborted ("bulkBarrierPreWrite: unaligned arguments", golang/go#46893) whenever a call returned a string
# while the garbage collector was running, as Session.List did after a cancelled download.
case "$(go env GOVERSION)" in
  go1.2[6-9]*|go1.[3-9][0-9]*) ;;
  *) echo "error: needs Go 1.26 or later, found $(go env GOVERSION)" >&2; exit 1 ;;
esac
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.0.11902837}
export PATH=$PATH:$(go env GOPATH)/bin
go test ./...
mkdir -p build "$ASSETS" "$ROOT/app/libs"
rm -f "$ASSETS"/*.gz
manifest="{\"version\":1"
for arch in amd64 arm64; do
  GOOS=linux GOARCH=$arch go build -trimpath -ldflags "-s -w -buildid=" -o build/sshpeaches-boost-linux-$arch ./cmd/sshpeaches-boost
  cp build/sshpeaches-boost-linux-$arch "$ASSETS/sshpeaches-boost-linux-$arch"
  sum=$(sha256sum build/sshpeaches-boost-linux-$arch | cut -d' ' -f1)
  size=$(stat -c %s build/sshpeaches-boost-linux-$arch)
  manifest="$manifest,\"$arch\":{\"sha256\":\"$sum\",\"size\":$size}"
done
echo "$manifest}" > "$ASSETS/manifest.json"
# max-page-size: 16 KB-aligned segments, which Play requires (devices with 16 KB memory pages can't load the
# library otherwise); NDK 27's linker still defaults to 4 KB.
# The library embeds the module's directory (gomobile's go.mod replaces it by absolute path, which -trimpath leaves),
# so bind from a fixed copy: the F-Droid edition must come out byte for byte the same on F-Droid's build server.
STAGE=/tmp/sshpeaches-boost-src
rm -rf "$STAGE"; mkdir -p "$STAGE"
tar --exclude=./build -cf - . | tar -xf - -C "$STAGE"
(cd "$STAGE" && CGO_ENABLED=1 gomobile bind -target=android/arm64,android/amd64 -androidapi 26 \
  -javapkg com.majordaftapps.sshpeaches.boost -trimpath \
  -ldflags "-s -w -buildid= -extldflags=-Wl,-z,max-page-size=16384" \
  -o "$ROOT/app/libs/boostclient.aar" ./boostclient)
rm -rf "$STAGE"
rm -f "$ROOT/app/libs/boostclient-sources.jar"
# Refuse a library Play would flag: every loadable segment must be 16 KB-aligned.
check=$(mktemp -d)
unzip -q -o "$ROOT/app/libs/boostclient.aar" 'jni/*' -d "$check"
for so in "$check"/jni/*/libgojni.so; do
  if readelf -lW "$so" | awk '$1=="LOAD" && $NF!="0x4000"{bad=1} END{exit !bad}'; then
    echo "error: $so is not 16 KB-aligned" >&2; rm -rf "$check"; exit 1
  fi
done
rm -rf "$check"
cat "$ASSETS/manifest.json"
ls -la "$ASSETS" "$ROOT/app/libs/boostclient.aar"
