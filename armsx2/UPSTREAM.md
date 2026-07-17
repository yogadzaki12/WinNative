# ARMSX2 — vendored source

This module embeds the Android front-end of **ARMSX2**, a GPL-3.0 PlayStation 2
emulator (a fork of PCSX2) that brings PS2 emulation to ARM64.

- Upstream: https://github.com/ARMSX2/ARMSX2
- Upstream of upstream (PCSX2): https://github.com/pcsx2/pcsx2
- License: GPL-3.0 (`COPYING.GPLv3` in the upstream tree)
- Vendored from commit: `767540212740bcba2274b671738ed1cd1c3164be`
- Vendored path: `platforms/android/app/src/main`

## Native emulator core

The core (`libemucore_4k.so` / `libemucore_16k.so`) is built **from source** by
default during `assembleDebug`. `build-emucore.sh` fetches ARMSX2 at a pinned
commit (`51fc721a8321d305d6cf81518f7190b0587fb7cc`), applies the tracked patches
under `patches/`, syncs shaderc's third-party deps, and compiles both the 4K and
16K page-size variants with CMake + the NDK, stripped to release size. Fetched
source (`emucore-src/`) and build outputs stay out of git; only the script and
patches are tracked. The build reproduces the full JNI surface of
`kr/co/iefriends/pcsx2/NativeApp.java`.

Pass `-Parmsx2.buildNative=false` to fall back to the prebuilt core under
`prebuilt/emucore/arm64-v8a` (the official binaries from the
`ARMSX2-Refresh-2.6.1.1` release), which is the opt-out safety net.

The supporting libraries under `src/main/jniLibs/arm64-v8a` (ANGLE, `c++_shared`,
`androidx.graphics.path`) remain prebuilt — ANGLE in particular is a separate
Google project, not part of this tree.

### Patches (`patches/`)

- `0001-suppress-memcard-saved-osd.patch` — removes the native "Memory Card was
  saved to storage" OSD, which rendered outside WinNative's integrated display
  region.

All upstream copyright and license headers are preserved. This module and the
binaries it contains remain under GPL-3.0. WinNative as a whole is GPL-3.0, so
this integration is license-compatible. Modifications made to adapt the code to
WinNative's build (toolchain, Application ownership, launch entry) are noted in
the affected files.
