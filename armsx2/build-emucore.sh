#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK="${1:-${ANDROID_NDK_HOME:-}}"
SDK_CMAKE_DIR="${2:-}"
OUT="${3:-$HERE/emucore-out/arm64-v8a}"

URL="https://github.com/ARMSX2/ARMSX2.git"
SHA="51fc721a8321d305d6cf81518f7190b0587fb7cc"
SRC="$HERE/emucore-src"
CPP="$SRC/platforms/android/app/src/main/cpp"
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
fi

for p in "$HERE/patches"/*.patch; do
  [ -f "$p" ] || continue
  if ! git -C "$SRC" apply --reverse --check "$p" 2>/dev/null; then
    git -C "$SRC" apply "$p"
  fi
done

python3 "$CPP/3rdparty/shaderc/utils/git-sync-deps"

mkdir -p "$OUT"
build_variant() {
  local name="$1"
  local page="$2"
  local bdir="$SRC/build-$name"
  rm -rf "$bdir"; mkdir -p "$bdir"
  "$CMAKE" -G Ninja -B "$bdir" -S "$CPP" \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DANDROID=true -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
    -DLTO_PCSX2_CORE=ON \
    -DARMSX2_EMUCORE_LIBRARY_NAME="$name" \
    -DARMSX2_ANDROID_HOST_PAGE_SIZE="$page" >/dev/null
  "$NINJA" -C "$bdir" "$name" >/dev/null
  local so; so="$(find "$bdir" -name "lib${name}.so" | head -1)"
  [ -n "$so" ] || { echo "build-emucore: $name produced no .so" >&2; exit 1; }
  "$STRIP" --strip-unneeded "$so" -o "$OUT/lib${name}.so"
  echo "   -> $OUT/lib${name}.so"
}

echo "== emucore_4k =="; build_variant emucore_4k 4096
echo "== emucore_16k =="; build_variant emucore_16k 16384
