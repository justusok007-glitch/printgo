#!/usr/bin/env bash
# Builds PrintGo's printer drivers from the pinned source packages in sources.json.
#
#   native/build.sh TARGET OUT
#     TARGET  host | arm64-v8a | armeabi-v7a | x86_64
#     OUT     output directory:
#               host     -> OUT/host/bin/pg_*            (used on CI to make the catalog and data packs)
#               Android  -> OUT/jniLibs/<abi>/libpg_*.so (executables Android installs with the app)
#
# Android builds need the NDK (ANDROID_NDK_HOME or ANDROID_NDK_LATEST_HOME, r27 or newer).
# Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

TARGET=${1:?target}
OUT=$(realpath -m "${2:?output dir}")
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${PG_WORK:-$OUT/work}
DL=${PG_DL:-$WORK/dl}
SRC=$WORK/src
BLD=$WORK/build-$TARGET
JOBS=$(nproc 2>/dev/null || echo 4)
API=26
mkdir -p "$DL" "$SRC" "$BLD"

log() { echo "==> [$TARGET] $*"; }

# ---- sources ---------------------------------------------------------------------------------------
python3 "$HERE/fetch_sources.py" "$DL" >&2
unpack() { # name -> $SRC/name (unpacked once, then patched)
  local name=$1 file
  file=$(python3 -c "import json,sys;print(json.load(open('$DL/found.json'))['$name']['file'])")
  if [ ! -f "$SRC/$name/.unpacked" ]; then
    rm -rf "${SRC:?}/$name"; mkdir -p "$SRC/$name"
    tar -xf "$DL/$file" -C "$SRC/$name" --strip-components=1
    if [ -d "$HERE/patches/$name" ]; then
      for p in "$HERE"/patches/"$name"/*.patch; do [ -e "$p" ] && patch -d "$SRC/$name" -p1 < "$p"; done
    fi
    touch "$SRC/$name/.unpacked"
  fi
}
for n in escpr gutenprint hplip splix cups libjpeg-turbo jbigkit; do unpack $n; done

# ---- toolchain -------------------------------------------------------------------------------------
CMAKE_ARGS=(-G Ninja -DCMAKE_BUILD_TYPE=Release)
if [ "$TARGET" = host ]; then
  CONF_HOST=()
  export CC=${CC:-cc} CXX=${CXX:-c++}
  BIN_OUT=$OUT/host/bin
else
  NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}
  [ -d "$NDK" ] || { echo "Android NDK not found (set ANDROID_NDK_HOME)" >&2; exit 2; }
  TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
  case $TARGET in
    arm64-v8a)   TRIPLE=aarch64-linux-android;     HOSTT=aarch64-linux-android ;;
    armeabi-v7a) TRIPLE=armv7a-linux-androideabi;  HOSTT=arm-linux-androideabi ;;
    x86_64)      TRIPLE=x86_64-linux-android;      HOSTT=x86_64-linux-android ;;
    *) echo "unknown target $TARGET" >&2; exit 2 ;;
  esac
  export CC="$TC/bin/${TRIPLE}${API}-clang" CXX="$TC/bin/${TRIPLE}${API}-clang++"
  export AR="$TC/bin/llvm-ar" RANLIB="$TC/bin/llvm-ranlib" STRIP="$TC/bin/llvm-strip" NM="$TC/bin/llvm-nm"
  export CFLAGS="-O2 -fPIC" CXXFLAGS="-O2 -fPIC" LDFLAGS="-Wl,-z,max-page-size=16384"
  CONF_HOST=(--host="$HOSTT")
  CMAKE_ARGS+=(-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI="$TARGET"
               -DANDROID_PLATFORM="android-$API" -DANDROID_STL=c++_static)
  BIN_OUT=$OUT/jniLibs/$TARGET
fi
mkdir -p "$BIN_OUT"

# ---- libcups (static; raster + PPD support for hpcups, SpliX and pg_cupsraster) --------------------
if [ ! -f "$BLD/cups/cups/libcups.a" ]; then
  log "libcups"
  rm -rf "$BLD/cups"; cp -a "$SRC/cups" "$BLD/cups"
  ( cd "$BLD/cups"
    # iconv only exists from Android 9 (API 28); CUPS works without it.
    ac_cv_header_iconv_h=no ./configure "${CONF_HOST[@]}" --with-components=libcups --enable-static --disable-shared \
      --with-tls=no --with-dnssd=no --disable-dbus --disable-gssapi --disable-pam --disable-libusb --disable-acl \
      --with-ondemand=no --with-container=none --with-languages=none > configure.log 2>&1 || { tail -60 configure.log; exit 1; }
    make -k -C cups -j"$JOBS" libcups.a > make.log 2>&1 || { grep -E "error|Error" make.log | head -60; tail -30 make.log; exit 1; } )
fi
CUPS_ARGS=(-DCUPS_INCLUDE="$BLD/cups" -DCUPS_LIB="$BLD/cups/cups/libcups.a")

# ---- libgutenprint (static, all drivers built in) ----------------------------------------------------
if [ ! -f "$BLD/gutenprint/src/main/.libs/libgutenprint.a" ]; then
  log "libgutenprint"
  rm -rf "$BLD/gutenprint"; mkdir -p "$BLD/gutenprint"
  ( cd "$BLD/gutenprint"
    "$SRC/gutenprint/configure" "${CONF_HOST[@]}" --disable-shared --enable-static --with-modules=no --without-cups \
      --without-gimp2 --disable-libgutenprintui2 --disable-nls --without-doc --disable-samples --disable-escputil \
      --disable-test --disable-testpattern --without-readline --prefix=/printgo > configure.log 2>&1 || { tail -60 configure.log; exit 1; }
    make -C include -j"$JOBS" > make.log 2>&1 && make -k -C src/main -j"$JOBS" >> make.log 2>&1 || { grep -E "error|Error" make.log | head -60; tail -30 make.log; exit 1; } )
fi

# ---- libjpeg-turbo (static, for hpcups) --------------------------------------------------------------
if [ ! -f "$BLD/jpeg/libjpeg.a" ]; then
  log "libjpeg-turbo"
  cmake -S "$SRC/libjpeg-turbo" -B "$BLD/jpeg" "${CMAKE_ARGS[@]}" -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DWITH_SIMD=OFF \
    -DWITH_TURBOJPEG=OFF -DWITH_TOOLS=OFF -DWITH_TESTS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON > "$BLD/jpeg.log" 2>&1 \
    || { tail -40 "$BLD/jpeg.log"; exit 1; }
  ninja -C "$BLD/jpeg" jpeg-static >> "$BLD/jpeg.log" 2>&1 || { tail -40 "$BLD/jpeg.log"; exit 1; }
fi

# ---- driver programs -------------------------------------------------------------------------------
build_cmake() { # dir name args...
  local dir=$1 name=$2; shift 2
  log "$name"
  cmake -S "$HERE/cmake/$dir" -B "$BLD/$name" "${CMAKE_ARGS[@]}" "$@" -DCMAKE_INSTALL_PREFIX="$BIN_OUT" > "$BLD/$name.log" 2>&1 \
    || { tail -40 "$BLD/$name.log"; exit 1; }
  ninja -k 0 -C "$BLD/$name" install >> "$BLD/$name.log" 2>&1 || { grep -E "error|undefined" "$BLD/$name.log" | head -60; tail -20 "$BLD/$name.log"; exit 1; }
}
build_cmake escpr escpr -DESCPR_SRC="$SRC/escpr"
build_cmake hpcups hpcups -DHPLIP_SRC="$SRC/hplip" "${CUPS_ARGS[@]}" -DJPEG_SRC="$SRC/libjpeg-turbo" -DJPEG_BUILD="$BLD/jpeg"
build_cmake splix splix -DSPLIX_SRC="$SRC/splix" -DJBIG_SRC="$SRC/jbigkit" "${CUPS_ARGS[@]}"
build_cmake tools tools -DGUTENPRINT_SRC="$SRC/gutenprint" -DGUTENPRINT_BUILD="$BLD/gutenprint" "${CUPS_ARGS[@]}"

if [ "$TARGET" = host ]; then
  # Gutenprint's printer data (installed layout) for the downloadable data pack.
  log "gutenprint data"
  make -C "$BLD/gutenprint/src/xml" -j"$JOBS" > "$BLD/gutenprint-xml.log" 2>&1 || { tail -40 "$BLD/gutenprint-xml.log"; exit 1; }
  rm -rf "$OUT/host/gutenprint-stage"
  make -C "$BLD/gutenprint/src/xml" install DESTDIR="$OUT/host/gutenprint-stage" >> "$BLD/gutenprint-xml.log" 2>&1 \
    || { tail -40 "$BLD/gutenprint-xml.log"; exit 1; }
  rm -rf "$OUT/host/gutenprint-xml"
  mv "$OUT/host/gutenprint-stage/printgo/share/gutenprint/"*/xml "$OUT/host/gutenprint-xml"
  rm -rf "$OUT/host/gutenprint-stage"
else
  for f in "$BIN_OUT"/libpg_*.so; do "$STRIP" "$f"; done
fi
ls -la "$BIN_OUT"
log "done"
