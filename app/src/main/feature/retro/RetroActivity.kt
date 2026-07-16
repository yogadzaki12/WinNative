package com.winlator.cmod.feature.retro

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.RectF
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.ViewportAlignment
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.display.ui.FrameRating
import com.winlator.cmod.runtime.input.controls.ExternalController
import com.winlator.cmod.shared.android.FixedFontScaleAppCompatActivity
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class RetroActivity : FixedFontScaleAppCompatActivity(), RetroInputView.Listener {
    companion object {
        const val EXTRA_ROM_PATH = "retro_rom_path"
        const val EXTRA_SYSTEM_ID = "retro_system_id"
        const val EXTRA_GAME_NAME = "retro_game_name"
        const val EXTRA_SHORTCUT_PATH = "retro_shortcut_path"
        const val EXTRA_CONTAINER_ID = "retro_container_id"
        const val EXTRA_SHADER = "retro_shader"
        const val EXTRA_TOUCH_CONTROLS = "retro_touch_controls"
        const val EXTRA_AUDIO = "retro_audio"
        const val EXTRA_HUD = "retro_hud"
        const val EXTRA_VARIABLES = "retro_variables"

        const val EXTRA_UPSCALE = "retro_upscale"
        const val EXTRA_SGSR = "retro_sgsr"

        private val SHADER_KEYS = listOf("default", "crt", "lcd", "sharp")
        private val SHADER_LABELS = listOf("Default", "CRT", "LCD", "Sharp")
        private val UPSCALE_KEYS = listOf("2x", "4x", "native")
        private val UPSCALE_LABELS = listOf("2x", "4x", "Native")
        private val HUD_ELEMENT_LABELS =
            listOf("FPS", "Console", "GPU", "CPU", "RAM", "Battery", "Temp", "Graph", "CPU Temp")
        private val HUD_ELEMENT_ORDER = listOf(1, 2, 3, 8, 4, 5, 6, 0, 7)
    }

    private lateinit var retroView: GLRetroView
    private var overlay: RetroInputView? = null
    private val menu = RetroMenuController()
    private var retroReady = false
    private var gameName = "game"
    private var fastForward = false
    private var audioEnabledSetting = true
    private var touchControlsSetting = true
    private var currentShaderKey = "default"
    private var sgsrEnabled = false
    private var coreVars = HashMap<String, String>()
    private var diskCount = 0
    private var currentDisk = 0
    private var system: RetroSystem? = null
    private var persistShortcut: Shortcut? = null
    private var playtimePrefs: SharedPreferences? = null
    private var sessionStart = 0L
    private var emulationPaused = false
    private var controllerConnected = false
    private var inputManager: InputManager? = null
    private var hudVisible = false
    private var currentUpscaleKey = "native"
    private var hudAlpha = 1f
    private var hudBgDecoupled = false
    private var hudBgAlpha = 1f
    private var hudScale = 1f
    private var hudElements = booleanArrayOf(true, true, true, true, true, true, true, true, false)
    private var hudFrametimeNumeric = false
    private var hudDualBattery = false
    private var frameRating: FrameRating? = null
    private var rootLayout: FrameLayout? = null
    private var menuComposeView: ComposeView? = null
    private var surfaceReady = false
    private var customColors = RetroCustomColors()

    private val isPortrait: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private val gameAspect: Float
        get() =
            when (system?.id) {
                RetroSystems.GAMEBOY.id, RetroSystems.GAMEBOY_COLOR.id -> 10f / 9f
                RetroSystems.GBA.id -> 3f / 2f
                else -> 4f / 3f
            }

    private fun overlayPush(): Float {
        val rating = frameRating
        val rootHeight = rootLayout?.height ?: 0
        return if (isPortrait && hudVisible && rating != null &&
            rating.visibility == View.VISIBLE && rating.height > 0 && rootHeight > 0
        ) {
            ((rating.y + rating.height + rating.height * 0.15f) / rootHeight).coerceIn(0f, 0.3f)
        } else {
            0f
        }
    }

    private fun updateOverlayArea() {
        val rootWidth = rootLayout?.width ?: 0
        val rootHeight = rootLayout?.height ?: 0
        if (rootWidth <= 0 || rootHeight <= 0) return
        val push = overlayPush()
        val area =
            if (isPortrait) {
                val top = push * rootHeight
                val gameHeight = rootWidth / gameAspect
                RectF(0f, top, rootWidth.toFloat(), (top + gameHeight).coerceAtMost(rootHeight.toFloat()))
            } else {
                val availHeight = rootHeight * (1f - push)
                val gameWidth = (availHeight * gameAspect).coerceAtMost(rootWidth.toFloat())
                val left = (rootWidth - gameWidth) * 0.5f
                val gameHeight = gameWidth / gameAspect
                val top = push * rootHeight + (availHeight - gameHeight) * 0.5f
                RectF(left, top, left + gameWidth, top + gameHeight)
            }
        overlay?.setGameArea(area)
    }

    private fun applyDisplayGeometry() {
        updateOverlayArea()
        if (!surfaceReady || !retroReady) return
        retroView.viewportAlignment = if (isPortrait) ViewportAlignment.TOP else ViewportAlignment.CENTER
        retroView.viewport = RectF(0f, overlayPush(), 1f, 1f)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.releaseAll()
        rootLayout?.post {
            overlay?.relayout()
            applyDisplayGeometry()
        }
    }

    private val inputDeviceListener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refreshControllerPresence()

            override fun onInputDeviceRemoved(deviceId: Int) = refreshControllerPresence()

            override fun onInputDeviceChanged(deviceId: Int) = refreshControllerPresence()
        }

    private val stickIsAnalog: Boolean
        get() = system?.id == RetroSystems.N64.id

    private fun anyGameControllerConnected(): Boolean =
        InputDevice.getDeviceIds().any { id ->
            ExternalController.isGameController(InputDevice.getDevice(id))
        }

    private fun refreshControllerPresence() {
        controllerConnected = anyGameControllerConnected()
        updateOverlayVisibility()
    }

    private fun updateOverlayVisibility() {
        overlay?.visibility =
            if (overlay?.editMode == true || (touchControlsSetting && !controllerConnected)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun pauseEmulation() {
        if (emulationPaused || !retroReady) return
        emulationPaused = true
        retroView.onPause()
        LibretroDroid.pause()
    }

    private fun resumeEmulation() {
        if (!emulationPaused || !retroReady) return
        emulationPaused = false
        LibretroDroid.resume()
        retroView.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val romPath = intent.getStringExtra(EXTRA_ROM_PATH)
        val systemId = intent.getStringExtra(EXTRA_SYSTEM_ID)
        gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: "game"
        val resolvedSystem = RetroSystems.fromId(systemId)
        system = resolvedSystem

        if (romPath.isNullOrBlank() || resolvedSystem == null) {
            Toast.makeText(this, "Invalid retro game", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val romFile = File(romPath)
        if (!romFile.isFile) {
            Toast.makeText(this, "ROM not found: $romPath", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER

        val coreFile = RetroCoreManager.coreFile(this, resolvedSystem)
        if (!coreFile.isFile) {
            Toast.makeText(this, "Core not installed: ${resolvedSystem.coreFileName}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (RetroCoreManager.missingBios(this, resolvedSystem)) {
            Toast.makeText(
                this,
                "${resolvedSystem.shortName} needs a BIOS in ${RetroCoreManager.systemDir(this)}",
                Toast.LENGTH_LONG,
            ).show()
        }

        if (resolvedSystem.id == RetroSystems.N64.id) RetroCoreManager.ensureGlideN64Ini(this)
        val savesDir = RetroCoreManager.savesDir(this)
        val sramFile = File(savesDir, sramName())
        currentShaderKey = intent.getStringExtra(EXTRA_SHADER)?.lowercase() ?: "default"
        sgsrEnabled = intent.getBooleanExtra(EXTRA_SGSR, false)
        if (currentShaderKey == "sgsr") {
            currentShaderKey = "default"
            sgsrEnabled = true
        }
        if (currentShaderKey !in SHADER_KEYS) currentShaderKey = "default"
        currentUpscaleKey = intent.getStringExtra(EXTRA_UPSCALE)?.lowercase() ?: "native"
        if (currentUpscaleKey !in UPSCALE_KEYS) currentUpscaleKey = "native"
        audioEnabledSetting = intent.getBooleanExtra(EXTRA_AUDIO, true)
        touchControlsSetting = intent.getBooleanExtra(EXTRA_TOUCH_CONTROLS, true)
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        coreVars = (intent.getSerializableExtra(EXTRA_VARIABLES) as? HashMap<String, String>) ?: HashMap()
        RetroCoreOptions.defaultVariables(resolvedSystem).forEach { (key, value) ->
            if (!coreVars.containsKey(key)) coreVars[key] = value
        }

        val root = FrameLayout(this)
        rootLayout = root

        val inputView = RetroInputView(this, this, resolvedSystem)
        inputView.hapticStrength =
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getFloat("retro_haptic_strength", 0.4f)
        customColors = RetroControlLayouts.loadColors(this, resolvedSystem.id)
        inputView.setCustomColors(customColors)
        inputView.onEditStateChanged = { editing ->
            runOnUiThread {
                frameRating?.visibility =
                    if (!editing && hudVisible) View.VISIBLE else View.GONE
                updateOverlayVisibility()
                menu.rebuild()
            }
        }
        overlay = inputView
        root.addView(
            inputView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateOverlayArea() }

        menu.entriesProvider = { pane -> buildEntriesFor(pane) }
        menu.bottomProvider = { buildBottomEntries() }
        menu.tabs = RetroDrawerTabs.build(resolvedSystem, RetroCoreOptions.forSystem(resolvedSystem).isNotEmpty())
        hudVisible = intent.getBooleanExtra(EXTRA_HUD, false)
        val menuView =
            ComposeView(this).apply {
                elevation = 2000f
                setContent {
                    WinNativeTheme {
                        RetroDrawerMenu(menu)
                    }
                }
            }
        menuComposeView = menuView
        root.addView(
            menuView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        setContentView(root)
        inputManager = getSystemService(InputManager::class.java)
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)
        refreshControllerPresence()
        retroReady = false
        if (hudVisible) {
            root.post {
                if (!isFinishing && !isDestroyed && hudVisible) showHud()
            }
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (overlay?.editMode == true) {
                        overlay?.finishEdit()
                    } else if (menu.visible) {
                        menu.handleKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP)
                    } else {
                        openMenu()
                    }
                }
            },
        )
        loadHudSettings()
        recordLaunchStats()

        val sixtyRequested = requestSixtyHzDisplayMode()
        var waitAttempts = 0
        lateinit var startWhenReady: Runnable
        startWhenReady =
            Runnable {
                if (isFinishing || isDestroyed) return@Runnable
                val rate =
                    runCatching { windowManager.defaultDisplay.refreshRate }.getOrDefault(60f)
                if (sixtyRequested && abs(rate - 60f) > 2f && waitAttempts < 12) {
                    waitAttempts++
                    root.postDelayed(startWhenReady, 100)
                    return@Runnable
                }
                val data =
                    GLRetroViewData(this).apply {
                        coreFilePath = coreFile.absolutePath
                        gameFilePath = romFile.absolutePath
                        systemDirectory = RetroCoreManager.systemDir(this@RetroActivity).absolutePath
                        savesDirectory = savesDir.absolutePath
                        shader = effectiveShader()
                        variables = coreVars.map { Variable(it.key, it.value) }.toTypedArray()
                        rumbleEventsEnabled = true
                        preferLowLatencyAudio = true
                        skipDuplicateFrames = false
                        viewportAlignment =
                            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                ViewportAlignment.TOP
                            } else {
                                ViewportAlignment.CENTER
                            }
                        if (sramFile.isFile) saveRAMState = runCatching { sramFile.readBytes() }.getOrNull()
                    }
                val view = GLRetroView(this, data)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    view.holder.addCallback(
                        object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                runCatching {
                                    holder.surface.setFrameRate(
                                        60f,
                                        android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                                    )
                                }
                            }

                            override fun surfaceChanged(
                                holder: android.view.SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) {}

                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
                        },
                    )
                }
                retroView = view
                root.addView(
                    view,
                    0,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                lifecycle.addObserver(view)
                retroReady = true
                refreshControllerPresence()
                observeErrors()
                observeEvents()
            }
        root.post(startWhenReady)
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        super.onDestroy()
    }

    private fun showHud() {
        var rating = frameRating
        if (rating == null) {
            val root = rootLayout ?: return
            rating = FrameRating(this, HashMap<String, String>())
            rating.setRenderer(system?.shortName ?: "Retro")
            rating.visibility = View.GONE
            frameRating = rating
            val menuIndex = menuComposeView?.let { root.indexOfChild(it) } ?: -1
            if (menuIndex >= 0) root.addView(rating, menuIndex) else root.addView(rating)
            rating.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) rootLayout?.post { applyDisplayGeometry() }
            }
            applyRetroHudSettings(rating)
        }
        rating.visibility = View.VISIBLE
        rating.reset()
        rating.post { applyDisplayGeometry() }
    }

    private fun applyRetroHudSettings(rating: FrameRating) {
        rating.setHudAlpha(hudAlpha)
        rating.setBackgroundAlphaDecoupled(hudBgDecoupled)
        rating.setHudBackgroundAlpha(hudBgAlpha)
        rating.setHudScale(hudScale)
        rating.setFrametimeNumericMode(hudFrametimeNumeric)
        rating.setDualSeriesBattery(hudDualBattery)
        hudElements.forEachIndexed { index, enabled -> rating.toggleElement(index, enabled) }
    }

    private fun loadHudSettings() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        hudFrametimeNumeric = prefs.getBoolean(FrameRating.PREF_HUD_FRAMETIME_NUMERIC, false)
        hudDualBattery = prefs.getBoolean(FrameRating.PREF_HUD_DUAL_SERIES_BATTERY, false)
        lifecycleScope.launch(Dispatchers.IO) {
            val json =
                runCatching {
                    val containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, 0)
                    if (containerId <= 0) return@runCatching null
                    ContainerManager(this@RetroActivity)
                        .getContainerById(containerId)
                        ?.getExtra("hudSettings")
                }.getOrNull()
            if (json.isNullOrEmpty()) return@launch
            runCatching {
                val obj = org.json.JSONObject(json)
                val transparency = obj.optDouble("transparency", 1.0).toFloat()
                val decoupled = obj.optBoolean("backgroundAlphaDecoupled", false)
                val backgroundTransparency =
                    obj
                        .optDouble(
                            "backgroundTransparency",
                            (transparency * FrameRating.BACKDROP_BASE_ALPHA).toDouble(),
                        ).toFloat()
                val scale = obj.optDouble("scale", 1.0).toFloat()
                val legacyCpuRam = obj.optBoolean("showCpuRam", true)
                val legacyBattTemp = obj.optBoolean("showBattTemp", true)
                val elements =
                    booleanArrayOf(
                        obj.optBoolean("showFPS", true),
                        obj.optBoolean("showRenderer", true),
                        obj.optBoolean("showGPU", true),
                        obj.optBoolean("showCPU", legacyCpuRam),
                        obj.optBoolean("showRAM", legacyCpuRam),
                        obj.optBoolean("showBattery", legacyBattTemp),
                        obj.optBoolean("showTemp", legacyBattTemp),
                        obj.optBoolean("showGraph", true),
                        obj.optBoolean("showCpuTemp", false),
                    )
                runOnUiThread {
                    hudAlpha = transparency
                    hudBgDecoupled = decoupled
                    hudBgAlpha = backgroundTransparency
                    hudScale = scale
                    hudElements = elements
                    frameRating?.let { applyRetroHudSettings(it) }
                    menu.rebuild()
                }
            }
        }
    }

    private fun saveHudSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, 0)
                if (containerId <= 0) return@runCatching
                val container =
                    ContainerManager(this@RetroActivity).getContainerById(containerId) ?: return@runCatching
                val obj = org.json.JSONObject()
                obj.put("transparency", hudAlpha.toDouble())
                obj.put("backgroundAlphaDecoupled", hudBgDecoupled)
                obj.put("backgroundTransparency", hudBgAlpha.toDouble())
                obj.put("scale", hudScale.toDouble())
                obj.put("showFPS", hudElements[0])
                obj.put("showRenderer", hudElements[1])
                obj.put("showGPU", hudElements[2])
                obj.put("showCPU", hudElements[3])
                obj.put("showRAM", hudElements[4])
                obj.put("showBattery", hudElements[5])
                obj.put("showTemp", hudElements[6])
                obj.put("showGraph", hudElements[7])
                obj.put("showCpuTemp", hudElements[8])
                container.putExtra("hudSettings", obj.toString())
                container.saveData()
            }
        }
    }

    private fun recordLaunchStats() {
        val prefs = getSharedPreferences("playtime_stats", MODE_PRIVATE)
        playtimePrefs = prefs
        sessionStart = System.currentTimeMillis()
        prefs
            .edit()
            .putInt("${gameName}_play_count", prefs.getInt("${gameName}_play_count", 0) + 1)
            .putLong("${gameName}_last_played", sessionStart)
            .apply()
    }

    private fun accumulatePlaytime() {
        val prefs = playtimePrefs ?: return
        val now = System.currentTimeMillis()
        val delta = now - sessionStart
        if (delta > 0) {
            prefs
                .edit()
                .putLong("${gameName}_playtime", prefs.getLong("${gameName}_playtime", 0L) + delta)
                .apply()
        }
        sessionStart = now
    }

    private fun observeEvents() {
        retroView
            .getGLRetroEvents()
            .onEach { event ->
                when (event) {
                    is GLRetroView.GLRetroEvents.FrameRendered -> {
                        if (hudVisible) frameRating?.recordGameFrame()
                    }
                    is GLRetroView.GLRetroEvents.SurfaceCreated -> {
                        if (!audioEnabledSetting) retroView.audioEnabled = false
                        surfaceReady = true
                        applyDisplayGeometry()
                        lifecycleScope.launch(Dispatchers.Default) {
                            runCatching {
                                diskCount = retroView.getAvailableDisks()
                                currentDisk = retroView.getCurrentDisk()
                            }
                        }
                    }
                }
            }.launchIn(lifecycleScope)
    }

    private fun observeErrors() {
        retroView
            .getGLRetroErrors()
            .onEach { error ->
                val message =
                    when (error) {
                        GLRetroView.ERROR_LOAD_LIBRARY -> "Failed to load emulator core"
                        GLRetroView.ERROR_LOAD_GAME -> "Failed to load ROM"
                        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "Graphics not supported for this core"
                        else -> "Emulator error"
                    }
                Toast.makeText(this@RetroActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }.launchIn(lifecycleScope)
    }

    private fun sramName(): String {
        val safe = gameName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$safe.srm"
    }

    private fun sgsrPrePasses(): Int =
        when (currentUpscaleKey) {
            "4x" -> 2
            "native" -> 3
            else -> 1
        }

    private fun effectiveShader(): ShaderConfig =
        if (sgsrEnabled) {
            ShaderConfig.SGSR(sgsrPrePasses(), currentShaderKey)
        } else {
            when (currentShaderKey) {
                "crt" -> ShaderConfig.CRT
                "lcd" -> ShaderConfig.LCD
                "sharp" -> ShaderConfig.Sharp
                else -> ShaderConfig.Default
            }
        }

    private fun persistExtra(
        key: String,
        value: String,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val shortcut =
                    persistShortcut ?: run {
                        val containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, 0)
                        val path = intent.getStringExtra(EXTRA_SHORTCUT_PATH)
                        if (containerId <= 0 || path.isNullOrBlank()) return@run null
                        val file = File(path)
                        if (!file.isFile) return@run null
                        ContainerManager(this@RetroActivity)
                            .getContainerById(containerId)
                            ?.let { Shortcut(it, file) }
                    }?.also { persistShortcut = it }
                shortcut?.putExtra(key, value)
                shortcut?.saveData()
            }
        }
    }

    private fun buildEntriesFor(pane: RetroPane?): List<RetroMenuEntry> =
        when (pane) {
            null -> buildMainEntries()
            RetroPane.DISPLAY ->
                buildList {
                    SHADER_KEYS.forEachIndexed { index, key ->
                        add(
                            RetroMenuEntry.Radio(
                                label = SHADER_LABELS[index],
                                selected = currentShaderKey == key,
                            ) {
                                currentShaderKey = key
                                retroView.shader = effectiveShader()
                                persistExtra(RetroShortcuts.KEY_SHADER, key)
                                menu.rebuild()
                            },
                        )
                    }
                    add(
                        RetroMenuEntry.Toggle("SGSR", checked = sgsrEnabled) { value ->
                            sgsrEnabled = value
                            retroView.shader = effectiveShader()
                            persistExtra(RetroShortcuts.KEY_SGSR, if (value) "1" else "0")
                            menu.rebuild()
                        },
                    )
                    val upscaleIndex = UPSCALE_KEYS.indexOf(currentUpscaleKey).coerceAtLeast(0)
                    add(
                        RetroMenuEntry.Choice("SGSR Upscale", UPSCALE_LABELS, upscaleIndex) { next ->
                            currentUpscaleKey = UPSCALE_KEYS[next]
                            persistExtra(RetroShortcuts.KEY_UPSCALE, currentUpscaleKey)
                            if (sgsrEnabled) retroView.shader = effectiveShader()
                            menu.rebuild()
                        },
                    )
                }
            RetroPane.SYSTEM ->
                RetroCoreOptions.forSystem(system).map { option ->
                    val current = coreVars[option.key] ?: option.defaultValue
                    val index = option.values.indexOf(current).coerceAtLeast(0)
                    RetroMenuEntry.Choice(option.label, option.valueLabels, index) { next ->
                        val newValue = option.values[next]
                        coreVars[option.key] = newValue
                        retroView.updateVariables(Variable(option.key, newValue))
                        persistExtra(RetroShortcuts.VAR_PREFIX + option.key, newValue)
                        menu.rebuild()
                    }
                }
            RetroPane.SOUND ->
                listOf(
                    RetroMenuEntry.Toggle("Sound", checked = audioEnabledSetting) { value ->
                        audioEnabledSetting = value
                        retroView.audioEnabled = value
                        persistExtra(RetroShortcuts.KEY_AUDIO, if (value) "1" else "0")
                        menu.rebuild()
                    },
                )
            RetroPane.CONTROLS -> buildControlsEntries()
            RetroPane.HUD -> buildHudEntries()
        }

    private fun persistColors() {
        RetroControlLayouts.saveColors(this, system?.id, customColors)
        overlay?.setCustomColors(customColors)
        menu.rebuild()
    }

    private fun buildControlsEntries(): List<RetroMenuEntry> =
        buildList {
            add(
                RetroMenuEntry.Toggle("On-screen Controls", checked = touchControlsSetting) { value ->
                    touchControlsSetting = value
                    updateOverlayVisibility()
                    persistExtra(RetroShortcuts.KEY_TOUCH_CONTROLS, if (value) "1" else "0")
                    menu.rebuild()
                },
            )
            add(
                RetroMenuEntry.Slider(
                    label = "Haptic Feedback",
                    valueText =
                        overlay?.hapticStrength?.let { "${(it * 100).toInt()}%" } ?: "0%",
                    value = overlay?.hapticStrength ?: 0f,
                    min = 0f,
                    max = 1f,
                    step = 0.05f,
                ) { value ->
                    overlay?.hapticStrength = value
                    androidx.preference.PreferenceManager
                        .getDefaultSharedPreferences(this@RetroActivity)
                        .edit()
                        .putFloat("retro_haptic_strength", value)
                        .apply()
                    menu.rebuild()
                },
            )
            val orientationLabel =
                if ((rootLayout?.height ?: 0) > (rootLayout?.width ?: 0)) "Portrait" else "Landscape"
            add(
                RetroMenuEntry.Action("Edit Layout ($orientationLabel)", RetroDrawerIcons.EditLayout) {
                    menu.close()
                    overlay?.let {
                        it.visibility = View.VISIBLE
                        it.enterEdit()
                    }
                },
            )
            add(
                RetroMenuEntry.Action("Reset $orientationLabel Layout", RetroDrawerIcons.Reset) {
                    overlay?.resetLayout()
                    Toast.makeText(this@RetroActivity, "$orientationLabel layout reset", Toast.LENGTH_SHORT).show()
                },
            )
            add(
                RetroMenuEntry.ColorPick("Button Color", customColors.button) { value ->
                    customColors.button = value
                    persistColors()
                },
            )
            add(
                RetroMenuEntry.ColorPick("Letter Color", customColors.text) { value ->
                    customColors.text = value
                    persistColors()
                },
            )
            add(
                RetroMenuEntry.ColorPick("Shadow Color", customColors.shadow) { value ->
                    customColors.shadow = value
                    persistColors()
                },
            )
            add(
                RetroMenuEntry.ColorPick("Background Color", customColors.body) { value ->
                    customColors.body = value
                    persistColors()
                },
            )
            add(
                RetroMenuEntry.Action("Reset Colors", RetroDrawerIcons.Reset) {
                    customColors = RetroCustomColors()
                    persistColors()
                    Toast.makeText(this@RetroActivity, "Colors reset", Toast.LENGTH_SHORT).show()
                },
            )
        }

    private fun setHudVisible(value: Boolean) {
        hudVisible = value
        if (value) {
            showHud()
        } else {
            frameRating?.visibility = View.GONE
            applyDisplayGeometry()
        }
        persistExtra(RetroShortcuts.KEY_HUD, if (value) "1" else "0")
        menu.rebuild()
    }

    private fun buildHudEntries(): List<RetroMenuEntry> {
        val entries = mutableListOf<RetroMenuEntry>()
        entries +=
            RetroMenuEntry.Toggle("Performance HUD", checked = hudVisible) { value ->
                setHudVisible(value)
            }
        if (!hudVisible) return entries
        entries +=
            RetroMenuEntry.Slider(
                label = "Alpha",
                valueText = "${(hudAlpha * 100).toInt()}%",
                value = hudAlpha,
                min = 0.1f,
                max = 1f,
                step = 0.05f,
            ) { value ->
                hudAlpha = value
                frameRating?.setHudAlpha(value)
                if (!hudBgDecoupled) {
                    hudBgAlpha = (value * FrameRating.BACKDROP_BASE_ALPHA).coerceIn(0.1f, 1f)
                    frameRating?.setHudBackgroundAlpha(hudBgAlpha)
                }
                saveHudSettings()
                menu.rebuild()
            }
        entries +=
            RetroMenuEntry.Toggle("Background Alpha", checked = hudBgDecoupled) { value ->
                hudBgDecoupled = value
                frameRating?.setBackgroundAlphaDecoupled(value)
                if (!value) {
                    hudBgAlpha = (hudAlpha * FrameRating.BACKDROP_BASE_ALPHA).coerceIn(0.1f, 1f)
                    frameRating?.setHudBackgroundAlpha(hudBgAlpha)
                }
                saveHudSettings()
                menu.rebuild()
            }
        if (hudBgDecoupled) {
            entries +=
                RetroMenuEntry.Slider(
                    label = "Background",
                    valueText = "${(hudBgAlpha * 100).toInt()}%",
                    value = hudBgAlpha,
                    min = 0.1f,
                    max = 1f,
                    step = 0.05f,
                ) { value ->
                    hudBgAlpha = value
                    frameRating?.setHudBackgroundAlpha(value)
                    saveHudSettings()
                    menu.rebuild()
                }
        }
        entries +=
            RetroMenuEntry.Slider(
                label = "Scale",
                valueText = "${(hudScale * 100).toInt()}%",
                value = hudScale,
                min = 0.3f,
                max = 2f,
                step = 0.05f,
            ) { value ->
                hudScale = value
                frameRating?.setHudScale(value)
                saveHudSettings()
                menu.rebuild()
            }
        entries +=
            RetroMenuEntry.Toggle("Numeric Frametime", checked = hudFrametimeNumeric) { value ->
                hudFrametimeNumeric = value
                frameRating?.setFrametimeNumericMode(value)
                menu.rebuild()
            }
        entries +=
            RetroMenuEntry.Toggle("Dual-series Battery", checked = hudDualBattery) { value ->
                hudDualBattery = value
                frameRating?.setDualSeriesBattery(value)
                menu.rebuild()
            }
        entries +=
            RetroMenuEntry.Chips(
                label = "HUD ELEMENTS",
                items = HUD_ELEMENT_ORDER.map { HUD_ELEMENT_LABELS[it] },
                states = HUD_ELEMENT_ORDER.map { hudElements[it] },
            ) { position ->
                val index = HUD_ELEMENT_ORDER[position]
                val value = !hudElements[index]
                hudElements[index] = value
                frameRating?.toggleElement(index, value)
                saveHudSettings()
                menu.rebuild()
            }
        return entries
    }

    private fun buildMainEntries(): List<RetroMenuEntry> {
        val entries = mutableListOf<RetroMenuEntry>()
        entries +=
            RetroMenuEntry.Action("Save State", RetroDrawerIcons.Save) {
                menu.close()
                saveState()
            }
        entries +=
            RetroMenuEntry.Action("Load State", RetroDrawerIcons.Load) {
                menu.close()
                loadState()
            }
        entries +=
            RetroMenuEntry.Action("Reset", RetroDrawerIcons.Reset) {
                menu.close()
                retroView.reset()
            }
        entries +=
            RetroMenuEntry.Action("Fast Forward", RetroDrawerIcons.FastForward, active = fastForward) {
                fastForward = !fastForward
                retroView.frameSpeed = if (fastForward) 2 else 1
                menu.rebuild()
            }
        entries +=
            RetroMenuEntry.Action("HUD", RetroDrawerIcons.Hud, active = hudVisible) {
                setHudVisible(!hudVisible)
            }
        if (diskCount > 1) {
            entries +=
                RetroMenuEntry.Action("Disc ${currentDisk + 1}/$diskCount", RetroDrawerIcons.Disc) {
                    val next = (currentDisk + 1) % diskCount
                    lifecycleScope.launch(Dispatchers.Default) {
                        runCatching { retroView.changeDisk(next) }
                        currentDisk = next
                        runOnUiThread { menu.rebuild() }
                    }
                }
        }
        return entries
    }

    private fun buildBottomEntries(): List<RetroMenuEntry.Action> =
        listOf(
            if (emulationPaused) {
                RetroMenuEntry.Action("Resume", RetroDrawerIcons.Resume, active = true) {
                    resumeEmulation()
                    menu.close()
                }
            } else {
                RetroMenuEntry.Action("Pause", RetroDrawerIcons.Pause) {
                    pauseEmulation()
                    menu.close()
                }
            },
            RetroMenuEntry.Action("Exit", RetroDrawerIcons.Exit, danger = true) { finish() },
        )

    private fun requestSixtyHzDisplayMode(): Boolean {
        val display = runCatching { windowManager.defaultDisplay }.getOrNull() ?: return false
        val current = runCatching { display.mode }.getOrNull() ?: return false
        val rate = display.refreshRate
        val multiple = Math.round(rate / 60f)
        if (multiple >= 1 && kotlin.math.abs(rate - multiple * 60f) < 2f) return false
        val sixtyModes = display.supportedModes.filter { abs(it.refreshRate - 60f) < 1f }
        val target =
            sixtyModes.firstOrNull {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            } ?: sixtyModes.firstOrNull() ?: return false
        val attributes = window.attributes
        attributes.preferredDisplayModeId = target.modeId
        window.attributes = attributes
        return true
    }

    private fun openMenu() {
        if (!retroReady) {
            return
        }
        overlay?.releaseAll()
        menu.open()
    }

    private fun mapPhysicalKey(keyCode: Int): Int =
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }

    private fun isGamepadSource(event: KeyEvent): Boolean {
        val source = event.device?.sources ?: return false
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private val forwardedKeys =
        setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (menu.visible && isGamepadSource(event)) {
            menu.handleKey(keyCode, event.action)
            return true
        }
        if (retroReady && isGamepadSource(event)) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                if (event.action == KeyEvent.ACTION_UP) openMenu()
                return true
            }
            if (keyCode in forwardedKeys) {
                retroView.sendKeyEvent(event.action, mapPhysicalKey(keyCode), 0)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (menu.visible && event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            return true
        }
        if (retroReady &&
            event.action == MotionEvent.ACTION_MOVE &&
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        ) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
            if (stickIsAnalog) {
                retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatX, hatY, 0)
                retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, stickX, stickY, 0)
            } else {
                val deadzone = 0.45f
                val dpadX =
                    when {
                        abs(hatX) > 0.5f -> hatX
                        stickX > deadzone -> 1f
                        stickX < -deadzone -> -1f
                        else -> 0f
                    }
                val dpadY =
                    when {
                        abs(hatY) > 0.5f -> hatY
                        stickY > deadzone -> 1f
                        stickY < -deadzone -> -1f
                        else -> 0f
                    }
                retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dpadX, dpadY, 0)
                retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, stickX, stickY, 0)
            }
            retroView.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ),
                0,
            )
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onButton(
        keyCode: Int,
        down: Boolean,
    ) {
        if (!retroReady || menu.visible) return
        retroView.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode, 0)
    }

    override fun onDpad(
        x: Float,
        y: Float,
    ) {
        if (!retroReady || menu.visible) return
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, x, y, 0)
    }

    override fun onStick(
        x: Float,
        y: Float,
    ) {
        if (!retroReady || menu.visible) return
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0)
    }

    override fun onRightStick(
        x: Float,
        y: Float,
    ) {
        if (!retroReady || menu.visible) return
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y, 0)
    }

    override fun onMenu() {
        runOnUiThread { openMenu() }
    }

    private fun saveState() {
        runCatching {
            val bytes = retroView.serializeState()
            RetroCoreManager.stateFile(this, gameName, 0).writeBytes(bytes)
        }.onSuccess {
            Toast.makeText(this, "State saved", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Could not save state", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadState() {
        val file = RetroCoreManager.stateFile(this, gameName, 0)
        if (!file.isFile) {
            Toast.makeText(this, "No saved state", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { retroView.unserializeState(file.readBytes()) }
            .onSuccess { Toast.makeText(this, "State loaded", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Could not load state", Toast.LENGTH_SHORT).show() }
    }

    private fun persistSram() {
        if (!retroReady) return
        runCatching {
            val sram = retroView.serializeSRAM()
            if (sram.isNotEmpty()) {
                File(RetroCoreManager.savesDir(this), sramName()).writeBytes(sram)
            }
        }
    }

    override fun onPause() {
        persistSram()
        accumulatePlaytime()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (emulationPaused && retroReady) {
            window.decorView.post {
                if (emulationPaused && retroReady) {
                    retroView.onPause()
                    LibretroDroid.pause()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}
