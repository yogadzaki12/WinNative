#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK="${1:-${ANDROID_NDK_HOME:-}}"
OUT="${2:-$HERE/out/arm64-v8a}"
ABI="arm64-v8a"
PLATFORM="android-26"
JOBS="$(nproc 2>/dev/null || echo 4)"

if [ -z "$NDK" ] || [ ! -x "$NDK/ndk-build" ]; then
  echo "build-cores: NDK not found (arg1 or ANDROID_NDK_HOME); looked at '$NDK'" >&2
  exit 1
fi

mkdir -p "$OUT"

fetch() {
  local dir="$1" url="$2" sha="$3"
  if [ -d "$dir/.git" ] && [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null)" = "$sha" ]; then return 0; fi
  rm -rf "$dir"
  git init -q "$dir"
  git -C "$dir" remote add origin "$url"
  git -C "$dir" fetch -q --depth 1 origin "$sha"
  git -C "$dir" checkout -q "$sha"
}

while IFS='|' read -r name url sha jni out; do
  [ -z "$name" ] && continue
  case "$name" in \#*) continue;; esac
  target="$OUT/lib${out}_libretro_android.so"
  src="$HERE/$name"
  echo "== $name =="
  fetch "$src" "$url" "$sha"
  patch="$HERE/patches/$name.patch"
  if [ -f "$patch" ] && ! git -C "$src" apply --reverse --check "$patch" 2>/dev/null; then
    git -C "$src" apply "$patch"
  fi
  cflags=""
  [ "$name" = "parallel_n64" ] && cflags="-Wno-implicit-function-declaration -Wno-error=implicit-function-declaration"
  "$NDK/ndk-build" -C "$src/$jni" APP_ABI="$ABI" APP_PLATFORM="$PLATFORM" \
    ${cflags:+APP_CFLAGS="$cflags"} -j"$JOBS" >/dev/null
  built="$(find "$src" -path "*libs/$ABI/*.so" | head -1)"
  [ -n "$built" ] || { echo "build-cores: $name produced no .so" >&2; exit 1; }
  cp -f "$built" "$target"
  echo "   -> $target"
done < "$HERE/cores.manifest"
