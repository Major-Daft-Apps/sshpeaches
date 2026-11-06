#!/bin/bash
# Signs the F-Droid edition's unsigned release APK (from `./gradlew :app:assembleRelease -Pdistribution=fdroid`) with
# the F-Droid edition key in .keystore/sshpeaches-fdroid.jks (git-ignored; back it up, it can't be replaced).
# F-Droid rebuilds the same source, checks its unsigned APK matches this one byte for byte, then ships this signature.
#
# Usage: tools/fdroid/sign.sh <app-release-unsigned.apk> <output.apk>
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
IN=${1:?unsigned APK}
OUT=${2:?output APK}
PROPS=$ROOT/.keystore/fdroid.properties
prop() { sed -n "s/^$1=//p" "$PROPS"; }

SDK=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties")}
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)

"$BUILD_TOOLS/zipalign" -c -P 16 4 "$IN"
KS_PASS=$(prop storePassword) "$BUILD_TOOLS/apksigner" sign \
    --ks "$ROOT/.keystore/$(prop storeFile)" --ks-key-alias "$(prop keyAlias)" --ks-pass env:KS_PASS \
    --out "$OUT" "$IN"
"$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" | grep -E 'SHA-256|DN'
