package com.armsx2.ui

import androidx.compose.runtime.mutableStateOf

/**
 * Residual state holder for the (removed) armsx2 in-app UI. The WinNative host
 * renders ALL menus/overlays now; these flags remain because the emulation
 * lifecycle (MainActivityRuntime.start/stop) and the host's Ps2GameOverlay
 * (frontendCovers) still read/write them. While hosted they stay false, which
 * means the emulator surface always owns focus/input.
 */
object WindowImpl {
    val toolbarVisible = mutableStateOf(true)
    val showLibrary = mutableStateOf(false)
    val overlayVisible = mutableStateOf(false)

    /** True when a Compose frontend surface would cover a running game. With the
     *  armsx2 UI removed, nothing sets these while hosted, so this is always
     *  false — kept because the host app reads it. */
    val frontendCovers: Boolean
        get() = overlayVisible.value || showLibrary.value
}
