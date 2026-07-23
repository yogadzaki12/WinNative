#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK="${1:-${ANDROID_NDK_HOME:-}}"
SDK_CMAKE_DIR="${2:-}"
OUT="${3:-$HERE/emucore-out/arm64-v8a}"

URL="https://github.com/dolphin-emu/dolphin.git"
SHA="b008142f72a099a9bcb971776209504f085ae0be"
SRC="$HERE/emucore-src"
JOBS="$(nproc 2>/dev/null || echo 4)"

CMAKE="$SDK_CMAKE_DIR/cmake"; NINJA="$SDK_CMAKE_DIR/ninja"
[ -x "$CMAKE" ] || CMAKE="$(command -v cmake)"
[ -x "$NINJA" ] || NINJA="$(command -v ninja)"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"

if [ -z "$NDK" ] || [ ! -x "$NDK/ndk-build" ]; then
  echo "build-emucore: NDK not found (arg1 or ANDROID_NDK_HOME)" >&2; exit 1
fi

if [ ! -d "$SRC/.git" ] || [ "$(git -C "$SRC" rev-parse HEAD 2>/dev/null)" != "$SHA" ]; then
  rm -rf "$SRC"; git init -q "$SRC"
  git -C "$SRC" remote add origin "$URL"
  git -C "$SRC" fetch -q --depth 1 origin "$SHA"
  git -C "$SRC" checkout -q "$SHA"
  git -C "$SRC" submodule update --init --recursive --depth 1
fi

for p in "$HERE/patches"/*.patch; do
  [ -f "$p" ] || continue
  if ! git -C "$SRC" apply --reverse --check "$p" 2>/dev/null; then
    git -C "$SRC" apply "$p"
  fi
done

mkdir -p "$OUT"
BDIR="$SRC/build-android"
if [ ! -f "$BDIR/build.ninja" ]; then
  "$CMAKE" -G Ninja -B "$BDIR" -S "$SRC" \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_static \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_ANALYTICS=OFF \
    -DENABLE_AUTOUPDATE=OFF \
    -DENABLE_TESTS=OFF \
    -DUSE_RETRO_ACHIEVEMENTS=ON
fi

# main = Dolphin core JNI lib; the four hook libs are libadrenotools (Turnip loading).
TARGETS="main hook_impl main_hook gsl_alloc_hook file_redirect_hook"
"$NINJA" -C "$BDIR" $TARGETS

for t in $TARGETS; do
  so="$(find "$BDIR" -name "lib${t}.so" | head -1)"
  [ -n "$so" ] || { echo "build-emucore: lib${t}.so not produced" >&2; exit 1; }
  "$STRIP" --strip-unneeded "$so" -o "$OUT/lib${t}.so"
  echo "   -> $OUT/lib${t}.so"
done
