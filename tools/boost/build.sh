#!/bin/bash
# Builds SSHPeaches' file boost: the server helper (static Linux binaries, shipped as app assets, which the APK
# compresses, with a manifest of their SHA-256; Android's packager would unzip a .gz asset and drop the extension) and the app's client library (an AAR bound with gomobile). Needs Go 1.22, gomobile and
# gobind (golang.org/x/mobile v0.0.0-20240806205939-81131f6468ab), the Android SDK and NDK 27.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd ../.. && pwd)
ASSETS=$ROOT/app/src/main/assets/boost
export GOTOOLCHAIN=local GOFLAGS=-buildvcs=false CGO_ENABLED=0
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
CGO_ENABLED=1 gomobile bind -target=android/arm64,android/amd64 -androidapi 26 \
  -javapkg com.majordaftapps.sshpeaches.boost -trimpath -ldflags "-s -w" \
  -o "$ROOT/app/libs/boostclient.aar" ./boostclient
rm -f "$ROOT/app/libs/boostclient-sources.jar"
cat "$ASSETS/manifest.json"
ls -la "$ASSETS" "$ROOT/app/libs/boostclient.aar"
