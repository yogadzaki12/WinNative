package com.winlator.cmod.feature.retro

import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.compose.ui.platform.ComposeView
import com.winlator.cmod.R
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.display.ui.FrameRating
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.dolphinemu.dolphinemu.wn.DolphinEmulationActivity
import org.dolphinemu.dolphinemu.wn.DolphinHost
import java.io.File

/** WinNative in-game overlay for embedded Dolphin, built from the shared
 *  drawer/controls/HUD components and installed via the DolphinHost hook. */
object DolphinGameOverlay {
    private const val SLOT_COUNT = 4

    fun install() {
        DolphinHost.attachOverlay = { activity ->
            if (activity is DolphinEmulationActivity) attach(activity)
        }
        DolphinHost.onStageSaves = { activity -> DolphinCloudSync.stageAndQueue(activity) }
    }

    private fun loadShortcut(activity: DolphinEmulationActivity): Shortcut? {
        val containerId = activity.intent.getIntExtra(DolphinEmulationActivity.EXTRA_CONTAINER_ID, -1)
        val path = activity.intent.getStringExtra(DolphinEmulationActivity.EXTRA_SHORTCUT_PATH)
        if (containerId < 0 || path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            val cm = ContainerManager(activity)
            val container =
                if (containerId == ContainerManager.RETRO_CONTAINER_ID) cm.retroContainer else cm.getContainerById(containerId)
            container?.let { Shortcut(it, file) }
        }.getOrNull()
    }

    private fun attach(activity: DolphinEmulationActivity) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val system =
            RetroSystems.fromId(activity.intent.getStringExtra(DolphinEmulationActivity.EXTRA_SYSTEM_ID))
                ?: RetroSystems.GAMECUBE
        var shortcut: Shortcut? = null
        val pendingExtras = linkedMapOf<String, String>()
        val menu = RetroMenuController()
        lateinit var input: RetroInputView
        val vars = DolphinEmulationActivity.currentVariables
        var touchControls = vars["wn_touch"] != "0"
        var adaptiveSticks = vars["wn_adaptive"] == "1"
        var audioOn = vars["wn_audio"] != "0"
        var hudOn = vars["wn_hud"] == "1"
        var savesLoadMode = false
        var frameRating: FrameRating? = null
        var hudStyle = RetroHudSupport.loadGlobalHudStyle(activity)
        var hudElements = RetroHudSupport.loadGlobalHudElements(activity)
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            val sc = loadShortcut(activity)
            activity.runOnUiThread {
                shortcut = sc
                if (sc != null && pendingExtras.isNotEmpty()) {
                    val queued = pendingExtras.toMap()
                    pendingExtras.clear()
                    GlobalScope.launch(Dispatchers.IO) {
                        runCatching {
                            queued.forEach { (k, v) -> sc.putExtra(k, v) }
                            sc.saveData()
                        }
                    }
                }
            }
        }

        val gameName =
            activity.intent.getStringExtra(DolphinEmulationActivity.EXTRA_GAME_NAME) ?: "game"
        val stageKey = RetroSaveStates.cloudGameId(system.id, gameName)
        val cloudId =
            activity.intent.getStringExtra(DolphinEmulationActivity.EXTRA_SHORTCUT_PATH)?.let { path ->
                com.winlator.cmod.feature.sync.google.GameSaveBackupManager.customGameId(
                    activity.intent.getIntExtra(DolphinEmulationActivity.EXTRA_CONTAINER_ID, 0),
                    java.io.File(path).name,
                )
            } ?: stageKey

        fun cloudSyncEnabled(): Boolean = shortcut?.getExtra("cloud_sync_enabled", "1") != "0"

        fun stageCloudBackup(onDone: (() -> Unit)? = null) {
            if (!cloudSyncEnabled()) {
                onDone?.let { activity.runOnUiThread(it) }
                return
            }
            val gameId = DolphinEmulationActivity.currentGameId
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                if (DolphinCloudSync.stage(activity, stageKey, gameId)) {
                    val fp = DolphinCloudSync.stagedFingerprint(activity, stageKey)
                    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                    if (fp != prefs.getString("retro_cloud_fp_$cloudId", null)) {
                        DolphinCloudSync.writePending(activity, cloudId, gameName, fp)
                    }
                }
                onDone?.let { activity.runOnUiThread(it) }
            }
        }

        fun persistExtra(key: String, value: String) {
            val sc = shortcut
            if (sc == null) {
                pendingExtras[key] = value
                return
            }
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    sc.putExtra(key, value)
                    sc.saveData()
                }
            }
        }

        fun applyVar(key: String, value: String) {
            activity.applyVariable(key, value)
            persistExtra(RetroShortcuts.VAR_PREFIX + key, value)
        }

        fun showHud() {
            var rating = frameRating
            if (rating == null) {
                rating = RetroHudSupport.createFrameRating(activity, "Vulkan")
                frameRating = rating
                RetroHudSupport.attachFrameRating(root, rating)
                RetroHudSupport.applyStyle(rating, hudStyle, hudElements)
                org.dolphinemu.dolphinemu.NativeLibrary.frameListener =
                    Runnable { frameRating?.recordGameFrame() }
            }
            rating.visibility = android.view.View.VISIBLE
            rating.reset()
        }

        fun setHudVisible(value: Boolean) {
            hudOn = value
            activity.applyVariable("wn_hud", if (value) "1" else "0")
            RetroDefaults.setHud(activity, system.id, value)
            if (value) showHud() else frameRating?.visibility = android.view.View.GONE
        }

        val listener =
            object : RetroInputView.Listener {
                override fun onButton(keyCode: Int, down: Boolean) {
                    if (!menu.visible) activity.sendOverlayButton(keyCode, down)
                }

                override fun onDpad(x: Float, y: Float) {
                    if (!menu.visible) activity.sendOverlayDpad(x, y)
                }

                override fun onStick(x: Float, y: Float) {
                    if (!menu.visible) activity.sendOverlayLeftStick(x, y)
                }

                override fun onRightStick(x: Float, y: Float) {
                    if (!menu.visible) activity.sendOverlayRightStick(x, y)
                }

                override fun onMenu() {
                    activity.runOnUiThread {
                        input.releaseAll()
                        menu.open()
                    }
                }
            }

        input = RetroInputView(activity, listener, system)
        input.hapticStrength =
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getFloat("retro_haptic_strength", 0.4f)
        input.adaptiveSticks = adaptiveSticks
        input.showL3R3 =
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getBoolean(RetroControlsMenu.l3r3PrefKey(system.id), true)
        input.setCustomColors(RetroControlLayouts.loadColors(activity, system.id))
        input.loadStickInversion()
        input.visibility = if (touchControls) android.view.View.VISIBLE else android.view.View.GONE
        fun updateGameArea() {
            val w = root.width.toFloat()
            val h = root.height.toFloat()
            if (w <= 0f || h <= 0f) return
            val gameWidth = (h * 4f / 3f).coerceAtMost(w)
            val left = (w - gameWidth) * 0.5f
            val box = android.graphics.RectF(left, 0f, left + gameWidth, h)
            input.setGameArea(box)
            activity.setSurfaceBounds(if (touchControls) box else null)
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateGameArea() }
        root.post { updateGameArea() }
        root.addView(
            input,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        menu.tabs = RetroDrawerTabs.build(activity, includeNetplay = false)
        menu.entriesProvider = { pane ->
            when (pane) {
                null ->
                    buildMain(activity, menu, hudOn, ::setHudVisible) { load ->
                        savesLoadMode = load
                        menu.showPane(RetroPane.SAVES)
                    }
                RetroPane.SAVES -> buildMemoryCards(activity, menu, savesLoadMode) { stageCloudBackup() }
                RetroPane.DISPLAY -> buildDisplay(activity, menu, system, ::applyVar)
                RetroPane.HUD ->
                    RetroHudSupport.buildHudEntries(
                        context = activity,
                        hudVisible = hudOn,
                        style = hudStyle,
                        elements = hudElements,
                        onMaster = { setHudVisible(it) },
                        onStyle = { next ->
                            hudStyle = next
                            frameRating?.let { RetroHudSupport.applyStyle(it, next, hudElements) }
                            RetroHudSupport.saveGlobalHudStyle(activity, next)
                        },
                        onElements = { next ->
                            hudElements = next
                            frameRating?.let { RetroHudSupport.applyStyle(it, hudStyle, next) }
                            RetroHudSupport.saveGlobalHudElements(activity, next)
                        },
                        onRebuild = { menu.rebuild() },
                    )
                RetroPane.SOUND ->
                    listOf(
                        RetroMenuEntry.Toggle(activity.getString(R.string.retro_lr_sound), checked = audioOn) { value ->
                            audioOn = value
                            applyVar("wn_audio", if (value) "1" else "0")
                            persistExtra(RetroShortcuts.KEY_AUDIO, if (value) "1" else "0")
                            menu.rebuild()
                        },
                    )
                RetroPane.CONTROLS ->
                    RetroControlsMenu.build(
                        RetroControlsMenu.Host(
                            context = activity,
                            overlay = input,
                            menu = menu,
                            systemId = system.id,
                            touchControls = { touchControls },
                            onTouchControls = { value ->
                                touchControls = value
                                input.visibility =
                                    if (value) android.view.View.VISIBLE else android.view.View.GONE
                                updateGameArea()
                                persistExtra(RetroShortcuts.KEY_TOUCH_CONTROLS, if (value) "1" else "0")
                            },
                            adaptiveSticks = { adaptiveSticks },
                            onAdaptiveSticks = { value ->
                                adaptiveSticks = value
                                persistExtra(RetroShortcuts.KEY_ADAPTIVE_STICKS, if (value) "1" else "0")
                            },
                            orientationLabel = { activity.getString(R.string.retro_lr_landscape) },
                            onCloseMenu = { menu.close() },
                            showStickInversion = true,
                        ),
                    )
                else -> emptyList()
            }
        }
        menu.bottomProvider = { buildBottom(activity, menu) { activity.stopGame() } }

        val compose =
            ComposeView(activity).apply {
                elevation = 2000f
                setContent { WinNativeTheme { RetroDrawerMenu(menu) } }
            }
        root.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        if (hudOn) root.post { showHud() }

        activity.onBackPressedDispatcher.addCallback(activity) {
            when {
                input.editMode -> input.finishEdit()
                menu.visible -> menu.close()
                else -> menu.open()
            }
        }
    }

    private fun buildMain(
        activity: DolphinEmulationActivity,
        menu: RetroMenuController,
        hudOn: Boolean,
        onHud: (Boolean) -> Unit,
        openMemoryCards: (load: Boolean) -> Unit,
    ): List<RetroMenuEntry> =
        buildList {
            add(
                RetroMenuEntry.Action(activity.getString(R.string.retro_lr_save_state), RetroDrawerIcons.Save) {
                    openMemoryCards(false)
                },
            )
            add(
                RetroMenuEntry.Action(activity.getString(R.string.retro_lr_load_save_state), RetroDrawerIcons.Load) {
                    openMemoryCards(true)
                },
            )
            add(
                RetroMenuEntry.Action(activity.getString(R.string.retro_lr_screenshot), RetroDrawerIcons.Hud) {
                    runCatching { org.dolphinemu.dolphinemu.NativeLibrary.SaveScreenShot() }
                    menu.close()
                },
            )
            add(
                RetroMenuEntry.Toggle(activity.getString(R.string.retro_lr_hud), checked = hudOn) { value ->
                    onHud(value)
                    menu.rebuild()
                },
            )
        }

    // Native state slots presented as the shared Memory Card slot cards.
    private fun buildMemoryCards(
        activity: DolphinEmulationActivity,
        menu: RetroMenuController,
        loadMode: Boolean,
        onSaved: () -> Unit,
    ): List<RetroMenuEntry> =
        (1..SLOT_COUNT).map { slot ->
            val ts = activity.stateSlotTime(slot)
            RetroMenuEntry.SaveSlot(
                slot = slot,
                title = activity.getString(R.string.retro_lr_memory_card_slot, slot),
                subtitle = RetroSaveStates.relativeTime(ts * 1000),
                filled = ts > 0L,
                onClick = {
                    if (loadMode) {
                        if (ts > 0L) {
                            activity.loadStateSlot(slot)
                            menu.close()
                            Toast
                                .makeText(activity, activity.getString(R.string.retro_lr_loaded_slot, slot), Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
                        activity.saveStateSlot(slot)
                        onSaved()
                        Toast
                            .makeText(activity, activity.getString(R.string.retro_lr_saved_to_slot, slot), Toast.LENGTH_SHORT)
                            .show()
                        menu.rebuild()
                    }
                },
                onRename = {},
            )
        }

    private fun buildDisplay(
        activity: DolphinEmulationActivity,
        menu: RetroMenuController,
        system: RetroSystem,
        applyVar: (String, String) -> Unit,
    ): List<RetroMenuEntry> =
        buildList {
            val vars = DolphinEmulationActivity.currentVariables
            RetroCoreOptions.forSystem(system).forEach { option ->
                val current = vars[option.key] ?: option.defaultValue
                val index = option.values.indexOf(current).coerceAtLeast(0)
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(option.label),
                        option.valueLabels.map { activity.getString(it) },
                        index,
                    ) { next ->
                        applyVar(option.key, option.values[next])
                        menu.rebuild()
                    },
                )
            }
        }

    private fun buildBottom(
        activity: DolphinEmulationActivity,
        menu: RetroMenuController,
        onExit: () -> Unit,
    ): List<RetroMenuEntry.Action> =
        listOf(
            if (activity.gamePaused) {
                RetroMenuEntry.Action(activity.getString(R.string.retro_lr_resume), RetroDrawerIcons.Resume, active = true) {
                    activity.resumeGame()
                    menu.close()
                }
            } else {
                RetroMenuEntry.Action(activity.getString(R.string.retro_lr_pause), RetroDrawerIcons.Pause) {
                    activity.pauseGame()
                    menu.close()
                }
            },
            RetroMenuEntry.Action(activity.getString(R.string.retro_lr_exit), RetroDrawerIcons.Exit, danger = true) {
                onExit()
            },
        )
}
