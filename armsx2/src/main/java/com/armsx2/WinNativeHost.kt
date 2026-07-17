package com.armsx2

import androidx.activity.ComponentActivity

/**
 * Bridge for the WinNative host app. The app module (which depends on :armsx2)
 * installs an overlay factory here before launching the embedded PS2 activity;
 * MainActivityRuntime invokes it after its content is up so WinNative can attach
 * its own on-screen controls and retro drawer menu over the emulator surface.
 */
object WinNativeHost {
    @Volatile
    var attachOverlay: ((ComponentActivity) -> Unit)? = null

    fun enabled(): Boolean =
        attachOverlay != null &&
            runCatching {
                com.armsx2.runtime.MainActivityRuntime.prefs.getBoolean("wn.controls", false)
            }.getOrDefault(false)
}
