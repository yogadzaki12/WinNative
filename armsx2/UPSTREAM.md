# ARMSX2 — vendored source

This module embeds the Android front-end of **ARMSX2**, a GPL-3.0 PlayStation 2
emulator (a fork of PCSX2) that brings PS2 emulation to ARM64.

- Upstream: https://github.com/ARMSX2/ARMSX2
- Upstream of upstream (PCSX2): https://github.com/pcsx2/pcsx2
- License: GPL-3.0 (`COPYING.GPLv3` in the upstream tree)
- Vendored from commit: `767540212740bcba2274b671738ed1cd1c3164be`
- Vendored path: `platforms/android/app/src/main`

The native emulator core (`libemucore_4k.so` / `libemucore_16k.so`) and its
supporting libraries (ANGLE, librashader, shaderc, SPIRV-Tools, adrenotools
hooks) under `src/main/jniLibs/arm64-v8a` are the official prebuilt binaries
extracted from the `ARMSX2-Refresh-2.6.1.1` release APK. The JNI contract of
those binaries matches the vendored `kr/co/iefriends/pcsx2/NativeApp.java`.

All upstream copyright and license headers are preserved. This module and the
binaries it contains remain under GPL-3.0. WinNative as a whole is GPL-3.0, so
this integration is license-compatible. Modifications made to adapt the code to
WinNative's build (toolchain, Application ownership, launch entry) are noted in
the affected files.
