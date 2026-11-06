#!/bin/bash
# Mimics fdroiddata's recipe for com.majordaftapps.sshpeaches.fdroid (tools/fdroid/*.yml: rm, prebuild, build,
# gradle) in a clean debian:bookworm container at F-Droid's build path, and writes the unsigned APK to /out/sim.apk.
# Compare it with a clean `./gradlew -Pdistribution=fdroid :app:assembleRelease` of the same source: they must match.
# Mounts: /srcpkg/src.tar (the source, e.g. `git archive`), /opt/android-sdk (read-only), /out, and optionally
# /cache with the mosh-client source tarballs. Example (podman on enzu):
#   podman run --rm --security-opt label=disable -v /opt/android-sdk:/opt/android-sdk:ro -v $PWD/pkg:/srcpkg:ro \
#     -v $PWD/out:/out debian:bookworm bash /srcpkg/recipe-sim.sh
set -euo pipefail
apt-get update -qq >/dev/null
apt-get install -y -qq build-essential cmake perl curl ca-certificates pkg-config xz-utils unzip openjdk-17-jdk-headless \
    >/dev/null 2>&1
B=/home/vagrant/build/com.majordaftapps.sshpeaches.fdroid
mkdir -p "$B"
tar -xf /srcpkg/src.tar -C "$B"
cd "$B"
# rm:
rm -rf app/libs/boostclient.aar app/src/main/assets/boost/sshpeaches-boost-linux-amd64 \
    app/src/main/assets/boost/sshpeaches-boost-linux-arm64 app/src/main/assets/mosh/arm64-v8a/bin/mosh-client \
    app/src/main/assets/mosh/x86_64/bin/mosh-client app/src/play app/src/release
echo "sdk.dir=/opt/android-sdk" > local.properties
cd app
# prebuild:
sed -i -e '/com.google.gms.google-services/d' -e '/com.google.firebase/d' ../build.gradle.kts
sed -i -e '/com.google.gms.google-services/d' -e '/com.google.firebase/d' -e '/firebaseBom/d' -e \
    '/com.google.android.play/d' build.gradle.kts
# build:
export ANDROID_HOME=/opt/android-sdk NDK=/opt/android-sdk/ndk/27.0.11902837
curl -fsSL -o /tmp/go.tgz https://go.dev/dl/go1.22.5.linux-amd64.tar.gz
echo "904b924d435eaea086515bc63235b192ea441bd8c9b198c507e85009e6e4c7f0  /tmp/go.tgz" | sha256sum -c
tar -C /tmp -xzf /tmp/go.tgz
export PATH=/tmp/go/bin:$PATH GOPATH=/tmp/gopath
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20240806205939-81131f6468ab golang.org/x/mobile/cmd/gobind@v0.0.0-20240806205939-81131f6468ab
ANDROID_NDK_HOME=$NDK ../tools/boost/build.sh
ANDROID_NDK_HOME=$NDK MOSH_SOURCES=$([ -d /cache ] && echo /cache || echo /tmp/mosh-sources) ../tools/mosh-client/build.sh
# gradle: yes, gradleprops distribution=fdroid
cd ..
./gradlew --no-daemon --console=plain -q -Pdistribution=fdroid :app:assembleRelease > /out/gradle.log 2>&1 || { tail -40 /out/gradle.log; exit 1; }
mkdir -p /out
cp app/build/outputs/apk/release/app-release-unsigned.apk /out/sim.apk
sha256sum /out/sim.apk
