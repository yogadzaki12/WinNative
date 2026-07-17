package com.winlator.cmod.feature.retro

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.armsx2.EmuState
import com.armsx2.WinNativeHost
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.WindowImpl
import com.winlator.cmod.shared.theme.WinNativeTheme
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Attaches WinNative's standard retro in-game UI — the 3D on-screen pad
 * (RetroInputView) and the retro drawer menu (RetroDrawerMenu) — over the
 * embedded ARMSX2 PS2 activity, wiring every action to ARMSX2's native
 * emulator functions so PS2 behaves exactly like the other retro consoles.
 */
object Ps2GameOverlay {
    private const val FULL = 32767

    fun install() {
        WinNativeHost.attachOverlay = { activity -> attach(activity) }
    }

    private fun mapFace(keyCode: Int): Int =
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            else -> keyCode
        }

    private fun emitAxis(pos: Int, neg: Int, v: Float) {
        val mag = (kotlin.math.abs(v) * FULL).toInt().coerceAtMost(FULL)
        NativeApp.setPadButton(pos, if (v > 0f) mag else 0, v > 0f)
        NativeApp.setPadButton(neg, if (v < 0f) mag else 0, v < 0f)
    }

    private fun attach(activity: ComponentActivity) {
        val menu = RetroMenuController()
        val ps2Screen = mutableStateOf<String?>(null)
        var savesLoadMode = false
        var pad: RetroInputView? = null
        val touchVisible = mutableStateOf(RetroDefaults.touchControls(activity, RetroSystems.PS2.id))
        var customColors = RetroControlLayouts.loadColors(activity, RetroSystems.PS2.id)
        val paused = { MainActivityRuntime.eState.value == EmuState.PAUSED }

        fun persistColors() {
            RetroControlLayouts.saveColors(activity, RetroSystems.PS2.id, customColors)
            pad?.setCustomColors(customColors)
            menu.rebuild()
        }

        val prefs = activity.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE)
        runCatching {
            NativeApp.setAudioVolume(prefs.getInt("wn.ps2.volume", 100))
            NativeApp.setAudioMuted(prefs.getBoolean("wn.ps2.muted", false))
            NativeApp.setAudioSwapChannels(prefs.getBoolean("wn.ps2.swap", false))
            when (prefs.getString("wn.ps2.renderer", "vulkan")) {
                "opengl" -> NativeApp.renderOpenGL()
                "software" -> NativeApp.renderSoftware()
                else -> NativeApp.renderVulkan()
            }
            NativeApp.renderUpscalemultiplier(prefs.getFloat("wn.ps2.upscale", 1f))
            NativeApp.osdShowFPS(prefs.getBoolean("wn.osd.fps", false))
        }
        fun osd(key: String) = prefs.getBoolean("wn.osd.$key", false)
        fun setOsd(key: String, value: Boolean, apply: (Boolean) -> Unit) {
            prefs.edit().putBoolean("wn.osd.$key", value).apply()
            runCatching { apply(value) }
            menu.rebuild()
        }

        fun openWinNativeScreen(screen: String) {
            menu.close()
            MainActivityRuntime.pauseForOverlay()
            ps2Screen.value = screen
        }

        fun mainEntries(): List<RetroMenuEntry> =
            buildList {
                add(
                    RetroMenuEntry.Action("Save State", RetroDrawerIcons.Save) {
                        savesLoadMode = false
                        menu.showPane(RetroPane.SAVES)
                    },
                )
                add(
                    RetroMenuEntry.Action("Load Save State", RetroDrawerIcons.Load) {
                        savesLoadMode = true
                        menu.showPane(RetroPane.SAVES)
                    },
                )
                add(RetroMenuEntry.Action("Achievements", RetroDrawerIcons.Achievements) { openWinNativeScreen("achievements") })
                add(RetroMenuEntry.Action("Cheats", RetroDrawerIcons.Cheats) { openWinNativeScreen("cheats") })
                add(RetroMenuEntry.Action("Memory Cards", RetroDrawerIcons.Save) { openWinNativeScreen("memcards") })
            }

        fun saveSlotEntries(): List<RetroMenuEntry> =
            (1..8).map { ui ->
                val slot = ui - 1
                RetroMenuEntry.SaveSlot(
                    slot = ui,
                    title = "Slot $ui",
                    subtitle = if (savesLoadMode) "Tap to load" else "Tap to save",
                    filled = true,
                    onClick = {
                        if (savesLoadMode) {
                            menu.close()
                            val ok = runCatching { NativeApp.loadStateFromSlot(slot) }.getOrDefault(false)
                            Toast.makeText(activity, if (ok) "Loaded slot $ui" else "Slot $ui is empty", Toast.LENGTH_SHORT).show()
                            if (ok) MainActivityRuntime.resume()
                        } else {
                            val ok = runCatching { NativeApp.saveStateToSlot(slot) }.getOrDefault(false)
                            Toast.makeText(activity, if (ok) "Saved to slot $ui" else "Save failed", Toast.LENGTH_SHORT).show()
                            menu.rebuild()
                        }
                    },
                    onRename = {},
                )
            }

        fun controlsEntries(): List<RetroMenuEntry> =
            buildList {
                add(
                    RetroMenuEntry.Toggle("On-screen Controls", checked = touchVisible.value) { value ->
                        touchVisible.value = value
                        RetroDefaults.setTouchControls(activity, RetroSystems.PS2.id, value)
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Action("Edit Layout", RetroDrawerIcons.EditLayout) {
                        menu.close()
                        touchVisible.value = true
                        pad?.enterEdit()
                    },
                )
                add(
                    RetroMenuEntry.Action("Reset Layout", RetroDrawerIcons.Reset) {
                        pad?.resetLayout()
                        Toast.makeText(activity, "Layout reset", Toast.LENGTH_SHORT).show()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick("Button Color", customColors.button) { value ->
                        customColors = customColors.copy(button = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick("Letter Color", customColors.text) { value ->
                        customColors = customColors.copy(text = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick("Shadow Color", customColors.shadow) { value ->
                        customColors = customColors.copy(shadow = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick("Background Color", customColors.body) { value ->
                        customColors = customColors.copy(body = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.Action("Reset Colors", RetroDrawerIcons.Reset) {
                        customColors = RetroCustomColors()
                        persistColors()
                    },
                )
            }

        fun displayEntries(): List<RetroMenuEntry> =
            buildList {
                val renderer = prefs.getString("wn.ps2.renderer", "vulkan")
                listOf("vulkan" to "Vulkan", "opengl" to "OpenGL", "software" to "Software").forEach { (key, label) ->
                    add(
                        RetroMenuEntry.Radio(label, selected = renderer == key) {
                            prefs.edit().putString("wn.ps2.renderer", key).apply()
                            runCatching {
                                when (key) {
                                    "vulkan" -> NativeApp.renderVulkan()
                                    "opengl" -> NativeApp.renderOpenGL()
                                    else -> NativeApp.renderSoftware()
                                }
                            }
                            menu.rebuild()
                        },
                    )
                }
                val scales = listOf(1f, 1.5f, 2f, 3f, 4f)
                val labels = listOf("1x (Native)", "1.5x", "2x", "3x", "4x")
                val current = prefs.getFloat("wn.ps2.upscale", 1f)
                val idx = scales.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
                add(
                    RetroMenuEntry.Choice("Upscale", labels, idx) { next ->
                        prefs.edit().putFloat("wn.ps2.upscale", scales[next]).apply()
                        runCatching { NativeApp.renderUpscalemultiplier(scales[next]) }
                        menu.rebuild()
                    },
                )
            }

        fun soundEntries(): List<RetroMenuEntry> =
            buildList {
                val muted = prefs.getBoolean("wn.ps2.muted", false)
                val volume = prefs.getInt("wn.ps2.volume", 100)
                val swap = prefs.getBoolean("wn.ps2.swap", false)
                add(
                    RetroMenuEntry.Slider(
                        label = "Volume",
                        valueText = "$volume%",
                        value = volume.toFloat(),
                        min = 0f,
                        max = 200f,
                        step = 5f,
                    ) { value ->
                        val v = value.toInt()
                        prefs.edit().putInt("wn.ps2.volume", v).apply()
                        runCatching { NativeApp.setAudioVolume(v) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Mute", checked = muted) { value ->
                        prefs.edit().putBoolean("wn.ps2.muted", value).apply()
                        runCatching { NativeApp.setAudioMuted(value) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Swap Stereo Channels", checked = swap) { value ->
                        prefs.edit().putBoolean("wn.ps2.swap", value).apply()
                        runCatching { NativeApp.setAudioSwapChannels(value) }
                        menu.rebuild()
                    },
                )
            }

        fun hudEntries(): List<RetroMenuEntry> =
            buildList {
                add(RetroMenuEntry.Toggle("FPS", checked = osd("fps")) { v -> setOsd("fps", v) { NativeApp.osdShowFPS(it) } })
                add(RetroMenuEntry.Toggle("Emulation Speed", checked = osd("speed")) { v -> setOsd("speed", v) { NativeApp.osdShowSpeed(it) } })
                add(RetroMenuEntry.Toggle("CPU Usage", checked = osd("cpu")) { v -> setOsd("cpu", v) { NativeApp.osdShowCPU(it) } })
                add(RetroMenuEntry.Toggle("GPU Usage", checked = osd("gpu")) { v -> setOsd("gpu", v) { NativeApp.osdShowGPU(it) } })
                add(
                    RetroMenuEntry.Toggle("Internal Resolution", checked = osd("res")) { v ->
                        setOsd("res", v) { NativeApp.osdShowResolution(it) }
                    },
                )
            }

        menu.tabs =
            listOf(
                RetroTabSpec(null, Icons.Outlined.Apps, "Menu"),
                RetroTabSpec(RetroPane.DISPLAY, Icons.Outlined.Monitor, "Display"),
                RetroTabSpec(RetroPane.HUD, Icons.Outlined.Speed, "HUD"),
                RetroTabSpec(RetroPane.SOUND, Icons.AutoMirrored.Outlined.VolumeUp, "Sound"),
                RetroTabSpec(RetroPane.CONTROLS, Icons.Outlined.SportsEsports, "Controls"),
            )
        menu.entriesProvider = { pane ->
            when (pane) {
                null -> mainEntries()
                RetroPane.DISPLAY -> displayEntries()
                RetroPane.SOUND -> soundEntries()
                RetroPane.SAVES -> saveSlotEntries()
                RetroPane.CONTROLS -> controlsEntries()
                RetroPane.HUD -> hudEntries()
            }
        }
        menu.bottomProvider = {
            listOf(
                if (paused()) {
                    RetroMenuEntry.Action("Resume", RetroDrawerIcons.Resume, active = true) {
                        MainActivityRuntime.resume()
                        menu.close()
                    }
                } else {
                    RetroMenuEntry.Action("Pause", RetroDrawerIcons.Pause) {
                        MainActivityRuntime.pauseForOverlay()
                        menu.close()
                    }
                },
                RetroMenuEntry.Action("Exit", RetroDrawerIcons.Exit, danger = true) {
                    menu.close()
                    runCatching { NativeApp.shutdown() }
                    activity.finish()
                },
            )
        }

        val listener =
            object : RetroInputView.Listener {
                override fun onButton(keyCode: Int, down: Boolean) {
                    NativeApp.setPadButton(mapFace(keyCode), if (down) FULL else 0, down)
                }

                override fun onDpad(x: Float, y: Float) {
                    NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_LEFT, if (x < -0.3f) FULL else 0, x < -0.3f)
                    NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_RIGHT, if (x > 0.3f) FULL else 0, x > 0.3f)
                    NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_UP, if (y < -0.3f) FULL else 0, y < -0.3f)
                    NativeApp.setPadButton(KeyEvent.KEYCODE_DPAD_DOWN, if (y > 0.3f) FULL else 0, y > 0.3f)
                }

                override fun onStick(x: Float, y: Float) {
                    emitAxis(111, 113, x)
                    emitAxis(112, 110, y)
                }

                override fun onRightStick(x: Float, y: Float) {
                    emitAxis(121, 123, x)
                    emitAxis(122, 120, y)
                }

                override fun onMenu() {
                    activity.runOnUiThread {
                        pad?.releaseAll()
                        menu.rebuild()
                        menu.open()
                    }
                }
            }

        val overlayView =
            ComposeView(activity).apply {
                setContent {
                    WinNativeTheme {
                        val screen by ps2Screen
                        if (screen != null) {
                            val dismiss = {
                                ps2Screen.value = null
                                MainActivityRuntime.resume()
                            }
                            BackHandler(enabled = true) { dismiss() }
                            when (screen) {
                                "memcards" -> Ps2MemoryCardsScreen(activity, dismiss)
                                "cheats" -> Ps2CheatsScreen(activity, dismiss)
                                "achievements" -> Ps2AchievementsScreen(activity, dismiss)
                            }
                            return@WinNativeTheme
                        }
                        val covered = WindowImpl.frontendCovers
                        if (!covered && touchVisible.value) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    RetroInputView(ctx, listener, RetroSystems.PS2).also { view ->
                                        view.hapticStrength =
                                            androidx.preference.PreferenceManager
                                                .getDefaultSharedPreferences(ctx)
                                                .getFloat("retro_haptic_strength", 0.4f)
                                        view.setCustomColors(customColors)
                                        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                            val w = v.width.toFloat()
                                            val h = v.height.toFloat()
                                            if (w > 0f && h > 0f) {
                                                val aspect = 4f / 3f
                                                val gw = (h * aspect).coerceAtMost(w)
                                                val gh = gw / aspect
                                                val left = (w - gw) * 0.5f
                                                val top = (h - gh) * 0.5f
                                                view.setGameArea(android.graphics.RectF(left, top, left + gw, top + gh))
                                            }
                                        }
                                        pad = view
                                    }
                                },
                            )
                        }
                        if (!covered) {
                            RetroDrawerMenu(menu)
                            BackHandler(enabled = true) {
                                when {
                                    pad?.editMode == true -> pad?.finishEdit()
                                    menu.visible -> menu.handleKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP)
                                    else -> {
                                        pad?.releaseAll()
                                        menu.rebuild()
                                        menu.open()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        activity.addContentView(
            overlayView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }
}
