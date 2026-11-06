#!/bin/bash
# Builds the app's mosh-client from source for Android: mosh 1.4.0's client with protobuf-lite 3.21, OpenSSL 3.0's
# libcrypto, ncurses 6.5 and libc++ linked in statically, so it loads only Android's own libc, libm, libdl and libz.
# The app runs it through /system/bin/linker64, which is why it stays a dynamically linked PIE rather than a fully
# static binary. Also compiles the terminfo entries the app ships (xterm, xterm-256color, vt100) with ncurses' tic.
#
# Output: app/src/main/assets/mosh/<abi>/{bin/mosh-client,share/terminfo,NOTICE.txt} for arm64-v8a and x86_64.
# Needs: the Android NDK r27 (ANDROID_NDK_HOME), cmake, perl, make, a host C/C++ compiler, curl (or the sources in
# $MOSH_SOURCES). The result is reproducible: pinned sources, fixed SOURCE_DATE_EPOCH, file-prefix maps, stripped.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd ../.. && pwd)
WORK=${MOSH_BUILD_DIR:-$PWD/build}
SOURCES=${MOSH_SOURCES:-$WORK/sources}
NDK=${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the Android NDK r27}
API=26
JOBS=$(nproc)
ASSETS=$ROOT/app/src/main/assets/mosh
export SOURCE_DATE_EPOCH=1700000000 LC_ALL=C TZ=UTC ZERO_AR_DATE=1

MOSH_VERSION=1.4.0
PROTOBUF_VERSION=3.21.12
OPENSSL_VERSION=3.0.22
NCURSES_VERSION=6.5

fetch() { # name url sha256
    local file=$SOURCES/$1
    mkdir -p "$SOURCES"
    if [ ! -f "$file" ]; then
        curl -fsSL -o "$file.part" "$2"
        mv "$file.part" "$file"
    fi
    echo "$3  $file" | sha256sum -c --quiet - || { echo "checksum mismatch: $1" >&2; exit 1; }
}
fetch mosh-$MOSH_VERSION.tar.gz \
    https://github.com/mobile-shell/mosh/releases/download/mosh-$MOSH_VERSION/mosh-$MOSH_VERSION.tar.gz \
    872e4b134e5df29c8933dff12350785054d2fd2839b5ae6b5587b14db1465ddd
fetch protobuf-cpp-$PROTOBUF_VERSION.tar.gz \
    https://github.com/protocolbuffers/protobuf/releases/download/v21.12/protobuf-cpp-$PROTOBUF_VERSION.tar.gz \
    4eab9b524aa5913c6fffb20b2a8abf5ef7f95a80bc0701f3a6dbb4c607f73460
fetch openssl-$OPENSSL_VERSION.tar.gz \
    https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz \
    67ebca7e50d17383028045486653492195b83db95f8558709701bb47b5c1ef81
fetch ncurses-$NCURSES_VERSION.tar.gz \
    https://ftp.gnu.org/gnu/ncurses/ncurses-$NCURSES_VERSION.tar.gz \
    136d91bc269a9a5785e5f9e980bc76ab57428f604ce3e5a5a90cebc767971cc6

unpack() { # tarball dir
    rm -rf "$2"; mkdir -p "$2"
    tar -xzf "$SOURCES/$1" -C "$2" --strip-components=1
}

TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
# Paths baked into the binaries would differ between machines: map them to fixed ones.
PREFIX_MAPS="-ffile-prefix-map=$WORK=/build -ffile-prefix-map=$NDK=/ndk -ffile-prefix-map=$ROOT=/src"

# --- Host tools: protoc (same protobuf version as the target library) and tic -----------------------------------
HOST=$WORK/host
if [ ! -x "$HOST/bin/protoc" ]; then
    unpack protobuf-cpp-$PROTOBUF_VERSION.tar.gz "$WORK/host-protobuf"
    cmake -S "$WORK/host-protobuf" -B "$WORK/host-protobuf/out" -DCMAKE_BUILD_TYPE=Release \
        -Dprotobuf_BUILD_TESTS=OFF -DCMAKE_INSTALL_PREFIX="$HOST" >/dev/null
    cmake --build "$WORK/host-protobuf/out" -j "$JOBS" --target protoc >/dev/null
    mkdir -p "$HOST/bin" && cp "$WORK/host-protobuf/out/protoc" "$HOST/bin/protoc"
fi
if [ ! -x "$HOST/bin/tic" ]; then
    unpack ncurses-$NCURSES_VERSION.tar.gz "$WORK/host-ncurses"
    (cd "$WORK/host-ncurses" && ./configure --prefix="$HOST" --without-shared --without-debug --without-ada \
        --without-cxx-binding --without-manpages --without-tests --enable-widec >/dev/null && \
        make -j "$JOBS" >/dev/null && make install.progs >/dev/null)
fi

build_abi() { # abi target openssl-target
    local abi=$1 target=$2 sslTarget=$3
    # Libraries install under a fixed prefix (via DESTDIR) because configure bakes it into strings in the binary
    # (OpenSSL's directories, ncurses' terminfo path): a prefix under $WORK would make each build dir's bytes differ.
    local inst=/opt/sshpeaches-mosh/$abi
    local root=$WORK/$abi-root
    local prefix=$root$inst
    local cc=$TOOLCHAIN/bin/$target$API-clang
    local cxx=$TOOLCHAIN/bin/$target$API-clang++
    local cflags="-O2 -fPIC $PREFIX_MAPS"
    rm -rf "$root"; mkdir -p "$prefix"
    export PATH=$TOOLCHAIN/bin:$PATH

    # OpenSSL: libcrypto only (mosh uses its AES).
    unpack openssl-$OPENSSL_VERSION.tar.gz "$WORK/$abi-openssl"
    (cd "$WORK/$abi-openssl" && ANDROID_NDK_ROOT=$NDK CC=clang CFLAGS="$cflags" ./Configure "$sslTarget" \
        -D__ANDROID_API__=$API no-shared no-tests no-module no-engine --prefix="$inst" --libdir=lib >/dev/null && \
        make -j "$JOBS" build_libs >/dev/null && make install_dev DESTDIR="$root" >/dev/null)

    # protobuf-lite (mosh's messages are LITE_RUNTIME).
    unpack protobuf-cpp-$PROTOBUF_VERSION.tar.gz "$WORK/$abi-protobuf"
    cmake -S "$WORK/$abi-protobuf" -B "$WORK/$abi-protobuf/out" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_static -Dprotobuf_BUILD_TESTS=OFF \
        -Dprotobuf_BUILD_PROTOC_BINARIES=OFF -Dprotobuf_BUILD_SHARED_LIBS=OFF -Dprotobuf_WITH_ZLIB=OFF \
        -DCMAKE_C_FLAGS="$PREFIX_MAPS" -DCMAKE_CXX_FLAGS="$PREFIX_MAPS" \
        -DCMAKE_INSTALL_PREFIX="$prefix" >/dev/null
    cmake --build "$WORK/$abi-protobuf/out" -j "$JOBS" --target libprotobuf-lite >/dev/null
    mkdir -p "$prefix/lib" "$prefix/include"
    cp "$WORK/$abi-protobuf/out/libprotobuf-lite.a" "$prefix/lib/"
    cp -r "$WORK/$abi-protobuf/src/google" "$prefix/include/"

    # ncurses: the terminfo reader mosh-client uses (setupterm, tigetstr); the database comes from $TERMINFO.
    unpack ncurses-$NCURSES_VERSION.tar.gz "$WORK/$abi-ncurses"
    (cd "$WORK/$abi-ncurses" && CC=$cc CFLAGS="$cflags" ./configure --host="$target" --prefix="$inst" \
        --without-shared --without-debug --without-ada --without-cxx-binding --without-manpages --without-progs \
        --without-tests --enable-widec --disable-db-install --with-fallbacks= \
        --with-default-terminfo-dir=/system/etc/terminfo >/dev/null && \
        make -j "$JOBS" libs >/dev/null && make install.libs install.includes DESTDIR="$root" >/dev/null)

    # mosh-client.
    unpack mosh-$MOSH_VERSION.tar.gz "$WORK/$abi-mosh"
    (cd "$WORK/$abi-mosh" && \
        CC=$cc CXX=$cxx AR=llvm-ar RANLIB=llvm-ranlib \
        CPPFLAGS="-I$prefix/include -I$prefix/include/ncursesw" \
        CFLAGS="$cflags" CXXFLAGS="$cflags -std=c++17" \
        LDFLAGS="-L$prefix/lib -static-libstdc++ -Wl,--build-id=none -Wl,-z,max-page-size=16384" \
        LIBS="-lz -llog" \
        PROTOC="$HOST/bin/protoc" \
        protobuf_CFLAGS="-I$prefix/include" protobuf_LIBS="$prefix/lib/libprotobuf-lite.a" \
        OpenSSL_CFLAGS="-I$prefix/include" OpenSSL_LIBS="-L$prefix/lib" \
        TINFO_CFLAGS="-I$prefix/include/ncursesw" TINFO_LIBS="$prefix/lib/libncursesw.a" \
        PKG_CONFIG_LIBDIR=/nonexistent \
        ./configure --host="$target" --prefix="$inst" --disable-server --enable-client \
            --with-crypto-library=openssl --disable-completion >/dev/null && \
        make -j "$JOBS" >/dev/null)

    local out=$ASSETS/$abi
    rm -rf "$out"; mkdir -p "$out/bin" "$out/share/terminfo"
    llvm-strip --strip-all -o "$out/bin/mosh-client" "$WORK/$abi-mosh/src/frontend/mosh-client"
    TERMINFO="$out/share/terminfo" "$HOST/bin/tic" -x -o "$out/share/terminfo" -e xterm,xterm-256color,vt100 \
        "$WORK/host-ncurses/misc/terminfo.src"
    # Only Android's own libraries at run time.
    local needed
    needed=$(llvm-readelf -d "$out/bin/mosh-client" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' | sort | tr '\n' ' ')
    echo "$abi mosh-client needs: $needed"
    for lib in $needed; do
        case $lib in libc.so|libm.so|libdl.so|libz.so|liblog.so) ;; *) echo "unexpected dependency $lib" >&2; exit 1 ;; esac
    done
    # 16 KB-aligned segments, or it won't load on devices with 16 KB memory pages.
    if llvm-readelf -lW "$out/bin/mosh-client" | awk '$1=="LOAD" && $NF!="0x4000"{bad=1} END{exit !bad}'; then
        echo "$abi mosh-client is not 16 KB-aligned" >&2; exit 1
    fi
}

build_abi arm64-v8a aarch64-linux-android android-arm64
build_abi x86_64 x86_64-linux-android android-x86_64

cat > "$ASSETS/NOTICE.txt" <<EOF
The bundled mosh-client is built from source by tools/mosh-client/build.sh:
- mosh $MOSH_VERSION (GPL-3.0-or-later, with an OpenSSL linking exception) https://mosh.org
- protobuf $PROTOBUF_VERSION, lite runtime (BSD-3-Clause) https://github.com/protocolbuffers/protobuf
- OpenSSL $OPENSSL_VERSION, libcrypto (Apache-2.0) https://www.openssl.org
- ncurses $NCURSES_VERSION and its terminfo entries (MIT-style X11 license) https://invisible-island.net/ncurses/
- libc++ from the Android NDK (Apache-2.0 with LLVM exceptions)
EOF
find "$ASSETS" -type f | sort | xargs sha256sum | sed "s#$ROOT/##"
