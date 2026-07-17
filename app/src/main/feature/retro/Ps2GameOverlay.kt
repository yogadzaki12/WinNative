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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
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
        var wnPaused = false

        fun persistColors() {
            RetroControlLayouts.saveColors(activity, RetroSystems.PS2.id, customColors)
            pad?.setCustomColors(customColors)
            menu.rebuild()
        }

        val prefs = activity.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE)

        fun gsSet(key: String, type: String, value: String) {
            runCatching {
                NativeApp.setSetting("EmuCore/GS", key, type, value)
                NativeApp.commitSettings()
                NativeApp.applyGSSettingsLive()
            }
        }

        fun bg(block: () -> Unit) {
            Thread { runCatching { block() } }.start()
        }

        fun gsSetAsync(key: String, type: String, value: String) = bg { gsSet(key, type, value) }

        fun spSet(key: String, type: String, value: String) {
            bg {
                NativeApp.setSetting("EmuCore/Speedhacks", key, type, value)
                NativeApp.commitSettings()
            }
        }

        bg {
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
            NativeApp.setAspectRatio(prefs.getInt("wn.ps2.aspect", 1).coerceIn(0, 3))
            NativeApp.setFrameSkip(prefs.getInt("wn.ps2.frameskip", 0).coerceIn(0, 3))
            NativeApp.speedhackEecyclerate(prefs.getInt("wn.ps2.eeRate", 0).coerceIn(-3, 3))
            NativeApp.speedhackEecycleskip(prefs.getInt("wn.ps2.eeSkip", 0).coerceIn(0, 3))
            NativeApp.setInstantVU1(prefs.getBoolean("wn.ps2.instantVu1", true))
            NativeApp.renderTvShader(prefs.getInt("wn.ps2.tvshader", 0).coerceIn(0, 7))
        }
        fun osd(key: String) = prefs.getBoolean("wn.osd.$key", false)
        fun setOsd(key: String, value: Boolean, apply: (Boolean) -> Unit) {
            prefs.edit().putBoolean("wn.osd.$key", value).apply()
            bg { apply(value) }
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
                add(RetroMenuEntry.Action("Memory Cards", RetroDrawerIcons.Save) { menu.showPane(RetroPane.MEMCARDS) })
                add(
                    RetroMenuEntry.Toggle("Fast Forward", checked = MainActivityRuntime.fastForwardToggleActive) {
                        (activity as? MainActivityRuntime)?.toggleFastForward()
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Action("Reset", RetroDrawerIcons.Reset) {
                        menu.close()
                        MainActivityRuntime.restart()
                    },
                )
                add(
                    RetroMenuEntry.Action("Swap Disc", RetroDrawerIcons.Disc) {
                        menu.close()
                        MainActivityRuntime.promptSwapDisc()
                    },
                )
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
                            Thread {
                                val ok = runCatching { NativeApp.loadStateFromSlot(slot) }.getOrDefault(false)
                                activity.runOnUiThread {
                                    Toast.makeText(activity, if (ok) "Loaded slot $ui" else "Slot $ui is empty", Toast.LENGTH_SHORT).show()
                                    if (ok) { wnPaused = false; MainActivityRuntime.resume() }
                                }
                            }.start()
                        } else {
                            Thread {
                                val ok = runCatching { NativeApp.saveStateToSlot(slot) }.getOrDefault(false)
                                activity.runOnUiThread {
                                    Toast.makeText(activity, if (ok) "Saved to slot $ui" else "Save failed", Toast.LENGTH_SHORT).show()
                                    menu.rebuild()
                                }
                            }.start()
                        }
                    },
                    onRename = {},
                )
            }

        var launchMemcardImport: (() -> Unit)? = null

        fun applyMemSlot(slot: Int, name: String?) {
            prefs.edit().apply {
                if (name == null) remove("wn.ps2.mc.slot$slot") else putString("wn.ps2.mc.slot$slot", name)
            }.apply()
            bg {
                NativeApp.setSetting("MemoryCards", "Slot${slot}_Enable", "bool", "false")
                if (name != null) {
                    NativeApp.setSetting("MemoryCards", "Slot${slot}_Filename", "string", name)
                    NativeApp.setSetting("MemoryCards", "Slot${slot}_Enable", "bool", "true")
                }
                NativeApp.commitSettings()
            }
        }

        fun memcardEntries(): List<RetroMenuEntry> =
            buildList {
                val cards = runCatching { listMemcards(activity) }.getOrDefault(emptyList())
                val slot1 = prefs.getString("wn.ps2.mc.slot1", "").orEmpty()
                val slot2 = prefs.getString("wn.ps2.mc.slot2", "").orEmpty()
                cards.forEach { card ->
                    val assigned = when (card.name) { slot1 -> 1; slot2 -> 2; else -> 0 }
                    add(
                        RetroMenuEntry.SaveSlot(
                            slot = assigned,
                            title = card.name.removeSuffix(".ps2"),
                            subtitle = humanSize(card.length()) +
                                when (assigned) { 1 -> "  •  Slot 1"; 2 -> "  •  Slot 2"; else -> "  •  Tap to use" },
                            filled = assigned != 0,
                            onClick = {
                                when (assigned) {
                                    0 -> applyMemSlot(1, card.name)
                                    1 -> { applyMemSlot(1, null); applyMemSlot(2, card.name) }
                                    else -> applyMemSlot(2, null)
                                }
                                menu.rebuild()
                            },
                            onRename = {},
                        ),
                    )
                }
                add(
                    RetroMenuEntry.Action("New Memory Card", RetroDrawerIcons.Save) {
                        bg {
                            val existing = runCatching { listMemcards(activity).map { it.name }.toHashSet() }.getOrDefault(hashSetOf())
                            var n = 1
                            var name = "Mcd%03d.ps2".format(n)
                            while (name in existing) { n++; name = "Mcd%03d.ps2".format(n) }
                            runCatching { NativeApp.createMemoryCard(name, 1, 1) }
                            activity.runOnUiThread { menu.rebuild() }
                        }
                    },
                )
                add(RetroMenuEntry.Action("Import Card", RetroDrawerIcons.Load) { launchMemcardImport?.invoke() })
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
                val rendererKeys = listOf("vulkan", "opengl", "software")
                val rendererLabels = listOf("Vulkan", "OpenGL", "Software")
                add(
                    RetroMenuEntry.Choice(
                        "Renderer",
                        rendererLabels,
                        rendererKeys.indexOf(prefs.getString("wn.ps2.renderer", "vulkan")).coerceAtLeast(0),
                    ) { next ->
                        prefs.edit().putString("wn.ps2.renderer", rendererKeys[next]).apply()
                        bg {
                            when (rendererKeys[next]) {
                                "opengl" -> NativeApp.renderOpenGL()
                                "software" -> NativeApp.renderSoftware()
                                else -> NativeApp.renderVulkan()
                            }
                        }
                        menu.rebuild()
                    },
                )
                val scales = listOf(1f, 1.5f, 2f, 3f, 4f)
                val scaleLabels = listOf("1x (Native)", "1.5x", "2x", "3x", "4x")
                val scaleIdx = scales.indexOfFirst { kotlin.math.abs(it - prefs.getFloat("wn.ps2.upscale", 1f)) < 0.01f }.coerceAtLeast(0)
                add(
                    RetroMenuEntry.Choice("Resolution Scale", scaleLabels, scaleIdx) { next ->
                        prefs.edit().putFloat("wn.ps2.upscale", scales[next]).apply()
                        bg { NativeApp.renderUpscalemultiplier(scales[next]) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        "Aspect Ratio",
                        listOf("Stretch", "Auto (Standard)", "4:3", "16:9"),
                        prefs.getInt("wn.ps2.aspect", 1).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.aspect", next).apply()
                        bg { NativeApp.setAspectRatio(next) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        "Display Filter",
                        listOf("Nearest", "Bilinear (Smooth)", "Bilinear (Sharp)"),
                        prefs.getInt("wn.ps2.displayfilter", 1).coerceIn(0, 2),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.displayfilter", next).apply()
                        gsSetAsync("linear_present_mode", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        "Texture Filter",
                        listOf("Nearest", "Bilinear (Forced)", "Bilinear (PS2)", "Bilinear (Sprites)"),
                        prefs.getInt("wn.ps2.filter", 2).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.filter", next).apply()
                        gsSetAsync("filter", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        "Blending Accuracy",
                        listOf("Minimum", "Basic", "Medium", "High", "Full", "Maximum"),
                        prefs.getInt("wn.ps2.blend", 1).coerceIn(0, 5),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.blend", next).apply()
                        gsSetAsync("accurate_blending_unit", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        "CRT / TV Shader",
                        listOf("Off", "Scanline", "Diagonal", "Triangular", "Wave", "Lottes", "4xRGSS", "NxAGSS"),
                        prefs.getInt("wn.ps2.tvshader", 0).coerceIn(0, 7),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.tvshader", next).apply()
                        bg { NativeApp.renderTvShader(next) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        "Frame Skip",
                        listOf("Off", "Skip 1", "Skip 2", "Skip 3"),
                        prefs.getInt("wn.ps2.frameskip", 0).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.frameskip", next).apply()
                        bg { NativeApp.setFrameSkip(next) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Mipmapping", checked = prefs.getBoolean("wn.ps2.mipmap", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.mipmap", value).apply()
                        gsSetAsync("hw_mipmap", "bool", value.toString())
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
                        bg { NativeApp.setAudioVolume(v) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Mute", checked = muted) { value ->
                        prefs.edit().putBoolean("wn.ps2.muted", value).apply()
                        bg { NativeApp.setAudioMuted(value) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Swap Stereo Channels", checked = swap) { value ->
                        prefs.edit().putBoolean("wn.ps2.swap", value).apply()
                        bg { NativeApp.setAudioSwapChannels(value) }
                        menu.rebuild()
                    },
                )
            }

        fun performanceEntries(): List<RetroMenuEntry> =
            buildList {
                val rateValues = listOf(-3, -2, -1, 0, 1, 2, 3)
                val rateLabels = listOf("50%", "60%", "75%", "100% (Default)", "130%", "180%", "300%")
                val curRate = prefs.getInt("wn.ps2.eeRate", 0).coerceIn(-3, 3)
                add(
                    RetroMenuEntry.Choice("EE Cycle Rate", rateLabels, rateValues.indexOf(curRate).coerceAtLeast(0)) { next ->
                        prefs.edit().putInt("wn.ps2.eeRate", rateValues[next]).apply()
                        bg { NativeApp.speedhackEecyclerate(rateValues[next]) }
                        spSet("EECycleRate", "int", rateValues[next].toString())
                        menu.rebuild()
                    },
                )
                val skipLabels = listOf("Off", "1", "2", "3")
                add(
                    RetroMenuEntry.Choice("EE Cycle Skip", skipLabels, prefs.getInt("wn.ps2.eeSkip", 0).coerceIn(0, 3)) { next ->
                        prefs.edit().putInt("wn.ps2.eeSkip", next).apply()
                        bg { NativeApp.speedhackEecycleskip(next) }
                        spSet("EECycleSkip", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Instant VU1", checked = prefs.getBoolean("wn.ps2.instantVu1", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.instantVu1", value).apply()
                        bg { NativeApp.setInstantVU1(value) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Multi-Threaded VU (MTVU)", checked = prefs.getBoolean("wn.ps2.mtvu", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.mtvu", value).apply()
                        spSet("vuThread", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle("Fast CDVD", checked = prefs.getBoolean("wn.ps2.fastCdvd", false)) { value ->
                        prefs.edit().putBoolean("wn.ps2.fastCdvd", value).apply()
                        spSet("fastCDVD", "bool", value.toString())
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
                RetroTabSpec(RetroPane.PERFORMANCE, Icons.Outlined.Bolt, "Performance"),
                RetroTabSpec(RetroPane.HUD, Icons.Outlined.Speed, "HUD"),
                RetroTabSpec(RetroPane.SOUND, Icons.AutoMirrored.Outlined.VolumeUp, "Sound"),
                RetroTabSpec(RetroPane.CONTROLS, Icons.Outlined.SportsEsports, "Controls"),
            )
        menu.entriesProvider = { pane ->
            when (pane) {
                null -> mainEntries()
                RetroPane.DISPLAY -> displayEntries()
                RetroPane.PERFORMANCE -> performanceEntries()
                RetroPane.SOUND -> soundEntries()
                RetroPane.SAVES -> saveSlotEntries()
                RetroPane.MEMCARDS -> memcardEntries()
                RetroPane.CONTROLS -> controlsEntries()
                RetroPane.HUD -> hudEntries()
            }
        }
        menu.bottomProvider = {
            listOf(
                if (wnPaused) {
                    RetroMenuEntry.Action("Resume", RetroDrawerIcons.Resume, active = true) {
                        wnPaused = false
                        MainActivityRuntime.resume()
                        menu.close()
                    }
                } else {
                    RetroMenuEntry.Action("Pause", RetroDrawerIcons.Pause) {
                        wnPaused = true
                        menu.rebuild()
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
                        val memImport = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                        ) { uri ->
                            if (uri != null) {
                                Thread {
                                    runCatching {
                                        val name = queryName(activity, uri) ?: "Imported.ps2"
                                        val fileName = if (name.endsWith(".ps2", true)) name else "$name.ps2"
                                        val target = uniqueMemcard(memcardDir(activity), fileName)
                                        activity.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
                                    }
                                    activity.runOnUiThread { menu.rebuild() }
                                }.start()
                            }
                        }
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            launchMemcardImport = { memImport.launch(arrayOf("*/*")) }
                        }
                        val menuVisible = menu.visible
                        androidx.compose.runtime.LaunchedEffect(menuVisible) {
                            if (menuVisible) {
                                MainActivityRuntime.pauseForOverlay()
                            } else if (!wnPaused && ps2Screen.value == null) {
                                MainActivityRuntime.resume()
                            }
                        }
                        val screen by ps2Screen
                        if (screen != null) {
                            val dismiss = {
                                ps2Screen.value = null
                                wnPaused = false
                                MainActivityRuntime.resume()
                            }
                            BackHandler(enabled = true) { dismiss() }
                            when (screen) {
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

        val prevCallback = activity.window.callback
        if (prevCallback != null) {
            activity.window.callback =
                object : android.view.Window.Callback by prevCallback {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (ps2Screen.value == null && menu.visible &&
                            menu.handleKey(event.keyCode, event.action)
                        ) {
                            return true
                        }
                        return prevCallback.dispatchKeyEvent(event)
                    }
                }
        }
    }
}
