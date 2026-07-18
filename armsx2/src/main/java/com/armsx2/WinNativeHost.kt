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

    /**
     * Applied by MainActivityRuntime BEFORE the VM boots (runVMThread), so
     * config that the PS2 reads while enumerating hardware at power-on — most
     * importantly the DEV9 network adapter (EthEnable) and virtual HDD — is
     * already in place. The overlay's own settings pass runs post-attach, which
     * is a frame too late for the boot-time hardware probe; a game like NFS
     * Underground 2 would then report "no network adapter detected".
     */
    @Volatile
    var applyBootSettings: ((android.content.Context) -> Unit)? = null

    fun enabled(): Boolean =
        attachOverlay != null &&
            runCatching {
                com.armsx2.runtime.MainActivityRuntime.prefs.getBoolean("wn.controls", false)
            }.getOrDefault(false)
}
