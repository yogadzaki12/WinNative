package com.armsx2.ui

import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.config.ConfigStore
import com.armsx2.config.Settings
import com.armsx2.config.SettingsScope
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Settings-state holder for the in-game session. The pause-menu UI this object
 * used to open has been removed (the WinNative host renders its own menu); the
 * remaining state is read by the runtime (frame-limit base mode, hardcore gate
 * for slow-down, OSD toggle hotkey) and by TouchControls (currentSerial).
 */
object InGameOverlay {
    val settingsState = mutableStateOf(Settings())
    val settingsScope = mutableStateOf(SettingsScope.Global)
    val currentSerial = mutableStateOf<String?>(null)
    val hardcoreOn = mutableStateOf(false)
    val frameLimitOn = mutableStateOf(true)

    /** Master OSD visibility for the on/off hotkey. A transient, in-game-only hide that does NOT
     *  touch the user's per-stat Settings selection — so toggling it back on restores exactly the
     *  stats they had chosen (not "everything"). Resets to visible each game boot. */
    val osdHidden = mutableStateOf(false)

    fun saveSettings(updated: Settings) {
        val previous = settingsState.value
        settingsState.value = updated
        // `previous` matters: it's how ConfigStore tells a field the user just changed in
        // Game scope from one they never touched, so setting a per-game value that happens
        // to equal global still pins it instead of vanishing.
        ConfigStore.save(settingsScope.value, currentSerial.value, updated, previous)
        frameLimitOn.value = updated.frameLimitEnable

        if (MainActivityRuntime.nativeReady.value) {
            runCatching {
                if (previous.frameLimitEnable != updated.frameLimitEnable) {
                    NativeApp.setSetting("EmuCore/GS", "FrameLimitEnable", "bool", updated.frameLimitEnable.toString())
                    NativeApp.speedhackLimitermode(if (updated.frameLimitEnable) 0 else 3)
                    MainActivityRuntime.fastForwardToggleActive = false
                }
                if (previous.upscaleFloat != updated.upscaleFloat &&
                    MainActivityRuntime.eState.value != EmuState.STOPPED
                ) {
                    NativeApp.renderUpscalemultiplier(updated.upscaleFloat.coerceIn(0.25f, 8.0f))
                    MainActivityRuntime.upscale.value = updated.upscaleFloat.coerceIn(0.25f, 8.0f)
                }
                if (MainActivityRuntime.eState.value != EmuState.STOPPED) updated.applyTo()
            }
        }
    }

    fun toggleOsd() {
        // Toggle OSD *visibility* only: flip a transient master flag and apply live-only —
        // on hide, push all-off; on show, push the user's saved selection back. The saved
        // Settings/store are never mutated, so the selection is preserved.
        val hide = !osdHidden.value
        osdHidden.value = hide
        if (hide) {
            NativeApp.osdApplyFlags(false, false, false, false, false, false, false, false, false, false, false, false)
        } else {
            val s = settingsState.value
            NativeApp.osdApplyFlags(
                s.osdShowFps, s.osdShowVps, s.osdShowSpeed, s.osdShowCpu, s.osdShowGpu,
                s.osdShowResolution, s.osdShowGsStats, s.osdShowFrameTimes, s.osdShowHardwareInfo,
                s.osdShowVersion, s.osdShowSettings, s.osdShowInputs,
            )
        }
    }
}
