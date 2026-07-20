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
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.armsx2.WinNativeHost
import com.winlator.cmod.R
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.WindowImpl
import com.winlator.cmod.shared.theme.WinNativeTheme
import kr.co.iefriends.pcsx2.NativeApp

@Composable
private fun Ps2NetEditDialog(state: MutableState<Ps2NetEdit?>) {
    val edit = state.value ?: return
    var draft by remember(edit) { mutableStateOf(edit.value) }
    AlertDialog(
        onDismissRequest = { state.value = null },
        title = { Text(edit.title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text(edit.placeholder) },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                edit.onSave(draft)
                state.value = null
            }) { Text(stringResource(R.string.retro_ps2_save)) }
        },
        dismissButton = {
            TextButton(onClick = { state.value = null }) { Text(stringResource(R.string.retro_ps2_cancel)) }
        },
    )
}

const val PS2_DEFAULT_DNS = "45.7.228.197"

data class Ps2NetHost(val url: String, val ip: String)

class Ps2NetEdit(
    val title: String,
    val value: String,
    val placeholder: String,
    val onSave: (String) -> Unit,
)

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
        WinNativeHost.applyBootSettings = { ctx -> applyBootConfig(ctx) }
    }

    private fun ps2Prefs(ctx: android.content.Context) =
        ctx.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE)

    /** EmuCore/GS FMVAspectRatioSwitch is a STRING key — mirror the exact names
     *  armsx2 Settings.writeGsToNative writes for each index (0 Off · 1 Auto
     *  4:3/3:2 · 2 4:3 · 3 16:9). */
    private fun fmvAspectName(value: Int): String =
        when (value) {
            1 -> "Auto 4:3/3:2"
            2 -> "4:3"
            3 -> "16:9"
            else -> "Off"
        }

    /** EmuCore patch/boot flags (widescreen, no-interlace, fast boot). Written at
     *  boot (applyBootConfig) so per-game shortcut settings take effect on launch;
     *  the drawer toggles push the same keys live followed by reloadPatches(). */
    private fun writePatchSettings(ctx: android.content.Context) {
        val prefs = ps2Prefs(ctx)
        NativeApp.setSetting("EmuCore", "EnableWideScreenPatches", "bool", prefs.getBoolean("wn.ps2.widescreen", false).toString())
        NativeApp.setSetting("EmuCore", "EnableNoInterlacingPatches", "bool", prefs.getBoolean("wn.ps2.nointerlace", false).toString())
        NativeApp.setSetting("EmuCore", "EnableFastBoot", "bool", prefs.getBoolean("wn.ps2.fastboot", true).toString())
    }

    // The virtual PS2 HDD image (DEV9). The emucore never auto-creates it —
    // ATA::Open just fails on a missing file — so we make a sparse 8 GiB image
    // (ftruncate: near-zero real bytes until written) on internal storage, which
    // the game formats itself like a fresh physical HDD.
    private fun hddImageFile(ctx: android.content.Context): java.io.File =
        java.io.File(ctx.filesDir, "hdd/DEV9hdd.raw")

    private fun ensureHddImage(ctx: android.content.Context) {
        if (!ps2Prefs(ctx).getBoolean("wn.ps2.hdd", false)) return
        val img = hddImageFile(ctx)
        if (img.exists() && img.length() > 0L) return
        runCatching {
            img.parentFile?.mkdirs()
            java.io.RandomAccessFile(img, "rw").use { it.setLength(8L * 1024 * 1024 * 1024) }
        }
    }

    private fun readHosts(prefs: android.content.SharedPreferences): List<Ps2NetHost> {
        val raw = prefs.getString("wn.ps2.net.hosts", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Ps2NetHost(o.optString("url"), o.optString("ip", "0.0.0.0"))
            }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    /** Write the full DEV9 config (Ethernet NIC + virtual HDD) into EmuConfig. */
    private fun writeDev9Settings(ctx: android.content.Context) {
        val prefs = ps2Prefs(ctx)
        val on = prefs.getBoolean("wn.ps2.net.enable", false)
        NativeApp.setSetting("DEV9/Eth", "EthEnable", "bool", on.toString())
        NativeApp.setSetting("DEV9/Eth", "EthApi", "string", "Sockets")
        NativeApp.setSetting("DEV9/Eth", "EthDevice", "string", (prefs.getString("wn.ps2.net.ethdevice", "Auto") ?: "Auto").ifBlank { "Auto" })
        NativeApp.setSetting("DEV9/Eth", "InterceptDHCP", "bool", prefs.getBoolean("wn.ps2.net.dhcp", true).toString())
        NativeApp.setSetting("DEV9/Eth", "AutoMask", "bool", "true")
        NativeApp.setSetting("DEV9/Eth", "AutoGateway", "bool", "true")
        // Force ALL PS2 TCP/UDP traffic (not just DNS lookups) to the configured
        // DNS1 server — for all-in-one revival servers that answer every request.
        NativeApp.setSetting("DEV9/Eth", "EthForceAllTraffic", "bool", prefs.getBoolean("wn.ps2.net.forceall", false).toString())
        val hosts = readHosts(prefs)
        // These DNS settings are the source of truth: the emucore patches redirect
        // EVERY PS2 DNS query here, regardless of the DNS baked into the game's
        // memory-card network profile. Manual → all queries go to DNS1 (DNS2
        // fallback). Internal → all queries go to the internal DNS server (device
        // resolver + Server Hosts overrides). The user-facing "Auto" also maps to
        // Internal: the emulator's real Auto mode reads the host adapter's DNS,
        // which Android blocks (SELinux), while Internal resolves through the
        // device's own resolver — i.e. what "Auto" means to the user. Host
        // overrides additionally force Internal since only the internal server
        // consults the EthHosts table.
        val chosen = prefs.getString("wn.ps2.net.dnsmode", "Manual") ?: "Manual"
        val mode =
            when {
                hosts.isNotEmpty() -> "Internal"
                chosen.equals("Manual", ignoreCase = true) -> "Manual"
                else -> "Internal"
            }
        NativeApp.setSetting("DEV9/Eth", "ModeDNS1", "string", mode)
        NativeApp.setSetting("DEV9/Eth", "ModeDNS2", "string", "Auto")
        NativeApp.setSetting("DEV9/Eth", "DNS1", "string", (prefs.getString("wn.ps2.net.dns1", PS2_DEFAULT_DNS) ?: PS2_DEFAULT_DNS).ifBlank { PS2_DEFAULT_DNS })
        NativeApp.setSetting("DEV9/Eth", "DNS2", "string", (prefs.getString("wn.ps2.net.dns2", "") ?: "").ifBlank { "0.0.0.0" })
        NativeApp.setSetting("DEV9/Eth/Hosts", "Count", "int", hosts.size.toString())
        hosts.forEachIndexed { i, h ->
            NativeApp.setSetting("DEV9/Eth/Hosts/Host$i", "Url", "string", h.url)
            NativeApp.setSetting("DEV9/Eth/Hosts/Host$i", "Desc", "string", "WinNative")
            NativeApp.setSetting("DEV9/Eth/Hosts/Host$i", "Address", "string", h.ip.ifBlank { "0.0.0.0" })
            NativeApp.setSetting("DEV9/Eth/Hosts/Host$i", "Enabled", "bool", "true")
        }
        // Virtual PS2 HDD (DEV9/Hdd). Independent of Ethernet — DEV9 activates if
        // either the NIC or the HDD is enabled.
        val hddOn = prefs.getBoolean("wn.ps2.hdd", false)
        NativeApp.setSetting("DEV9/Hdd", "HddEnable", "bool", hddOn.toString())
        NativeApp.setSetting("DEV9/Hdd", "HddFile", "string", hddImageFile(ctx).absolutePath)
    }

    /** Pre-boot hook (see WinNativeHost.applyBootSettings): pin the DEV9 NIC/HDD
     *  into EmuConfig before the VM enumerates hardware, so online play and the
     *  virtual HDD are detected on the very first boot rather than a frame late. */
    fun applyBootConfig(ctx: android.content.Context) {
        runCatching {
            ensureHddImage(ctx)
            writeDev9Settings(ctx)
            writePatchSettings(ctx)
            NativeApp.commitSettings()
        }
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

        fun spu2Set(key: String, type: String, value: String) {
            bg {
                NativeApp.setSetting("SPU2/Output", key, type, value)
                NativeApp.commitSettings()
            }
        }

        val netEdit = mutableStateOf<Ps2NetEdit?>(null)

        fun readHosts(): List<Ps2NetHost> = readHosts(prefs)

        fun writeHosts(hosts: List<Ps2NetHost>) {
            val arr = org.json.JSONArray()
            hosts.forEach { arr.put(org.json.JSONObject().put("url", it.url).put("ip", it.ip)) }
            prefs.edit().putString("wn.ps2.net.hosts", arr.toString()).apply()
        }

        fun ensureHddImage() = ensureHddImage(activity)

        fun writeNetworkSettings() = writeDev9Settings(activity)

        fun applyNetwork() = bg {
            writeNetworkSettings()
            NativeApp.commitSettings()
        }

        // Auto-apply the per-game DNAS bypass once the disc serial/CRC are known,
        // so online-revival games get past Sony's dead DNAS check by default.
        kotlin.concurrent.thread(name = "ps2-dnas-bypass") {
            Ps2DnasBypass.applyWhenReady(activity)
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
            NativeApp.setSetting("EmuCore/GS", "linear_present_mode", "int", prefs.getInt("wn.ps2.displayfilter", 1).coerceIn(0, 2).toString())
            NativeApp.setSetting("EmuCore/GS", "filter", "int", prefs.getInt("wn.ps2.filter", 2).coerceIn(0, 3).toString())
            NativeApp.setSetting("EmuCore/GS", "accurate_blending_unit", "int", prefs.getInt("wn.ps2.blend", 1).coerceIn(0, 5).toString())
            NativeApp.setSetting("EmuCore/GS", "hw_mipmap", "bool", prefs.getBoolean("wn.ps2.mipmap", true).toString())
            NativeApp.setSetting("EmuCore/Speedhacks", "vuThread", "bool", prefs.getBoolean("wn.ps2.mtvu", true).toString())
            NativeApp.setSetting("EmuCore/Speedhacks", "fastCDVD", "bool", prefs.getBoolean("wn.ps2.fastCdvd", false).toString())
            NativeApp.setSetting("EmuCore/GS", "deinterlace_mode", "int", prefs.getInt("wn.ps2.deinterlace", 0).coerceIn(0, 9).toString())
            NativeApp.setSetting("EmuCore/GS", "FMVAspectRatioSwitch", "string", fmvAspectName(prefs.getInt("wn.ps2.fmvaspect", 0)))
            NativeApp.setSetting("EmuCore/GS", "pcrtc_antiblur", "bool", prefs.getBoolean("wn.ps2.antiblur", true).toString())
            NativeApp.setSetting("EmuCore/Speedhacks", "vuFlagHack", "bool", prefs.getBoolean("wn.ps2.vuFlagHack", true).toString())
            NativeApp.setSetting("EmuCore/Speedhacks", "IntcStat", "bool", prefs.getBoolean("wn.ps2.intc", true).toString())
            NativeApp.setSetting("EmuCore/Speedhacks", "WaitLoop", "bool", prefs.getBoolean("wn.ps2.waitloop", true).toString())
            NativeApp.setSetting("SPU2/Output", "SyncMode", "string", if (prefs.getBoolean("wn.ps2.timestretch", true)) "TimeStretch" else "Disabled")
            NativeApp.setSetting("SPU2/Output", "BufferMS", "int", prefs.getInt("wn.ps2.audiobuffer", 50).coerceIn(10, 200).toString())
            NativeApp.setSetting("SPU2/Output", "OutputLatencyMS", "int", prefs.getInt("wn.ps2.audiolatency", 20).coerceIn(5, 200).toString())
            NativeApp.osdShowSpeed(prefs.getBoolean("wn.osd.speed", false))
            NativeApp.osdShowCPU(prefs.getBoolean("wn.osd.cpu", false))
            NativeApp.osdShowGPU(prefs.getBoolean("wn.osd.gpu", false))
            NativeApp.osdShowResolution(prefs.getBoolean("wn.osd.res", false))
            NativeApp.osdShowFrameTimes(prefs.getBoolean("wn.osd.frametimes", false))
            NativeApp.osdShowGSStats(prefs.getBoolean("wn.osd.gsstats", false))
            NativeApp.osdShowHardwareInfo(prefs.getBoolean("wn.osd.hwinfo", false))
            NativeApp.osdShowVersion(prefs.getBoolean("wn.osd.version", false))
            NativeApp.osdShowInputs(prefs.getBoolean("wn.osd.inputs", false))
            prefs.getString("wn.ps2.mc.slot1", null)?.takeIf { it.isNotBlank() }?.let { name ->
                NativeApp.setSetting("MemoryCards", "Slot1_Filename", "string", name)
                NativeApp.setSetting("MemoryCards", "Slot1_Enable", "bool", "true")
            }
            prefs.getString("wn.ps2.mc.slot2", null)?.takeIf { it.isNotBlank() }?.let { name ->
                NativeApp.setSetting("MemoryCards", "Slot2_Filename", "string", name)
                NativeApp.setSetting("MemoryCards", "Slot2_Enable", "bool", "true")
            }
            ensureHddImage()
            writeNetworkSettings()
            NativeApp.commitSettings()
        }
        fun osd(key: String) = prefs.getBoolean("wn.osd.$key", false)
        fun setOsd(key: String, value: Boolean, apply: (Boolean) -> Unit) {
            prefs.edit().putBoolean("wn.osd.$key", value).apply()
            bg { apply(value) }
            menu.rebuild()
        }

        fun openWinNativeScreen(screen: String) {
            menu.close()
            // No auto-pause: online games must keep their connection alive while
            // the cheats/achievements screen covers the game.
            ps2Screen.value = screen
        }

        fun mainEntries(): List<RetroMenuEntry> =
            buildList {
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_save_state), RetroDrawerIcons.Save) {
                        savesLoadMode = false
                        menu.showPane(RetroPane.SAVES)
                    },
                )
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_load_save_state), RetroDrawerIcons.Load) {
                        savesLoadMode = true
                        menu.showPane(RetroPane.SAVES)
                    },
                )
                add(RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_achievements), RetroDrawerIcons.Achievements) { openWinNativeScreen("achievements") })
                add(RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_cheats), RetroDrawerIcons.Cheats) { openWinNativeScreen("cheats") })
                add(RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_memory_cards), RetroDrawerIcons.Save) { menu.showPane(RetroPane.MEMCARDS) })
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_fast_forward), checked = MainActivityRuntime.fastForwardToggleActive) {
                        (activity as? MainActivityRuntime)?.toggleFastForward()
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_reset), RetroDrawerIcons.Reset) {
                        menu.close()
                        MainActivityRuntime.restart()
                    },
                )
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_swap_disc), RetroDrawerIcons.Disc) {
                        menu.close()
                        MainActivityRuntime.promptSwapDisc()
                    },
                )
            }

        fun saveSlotEntries(): List<RetroMenuEntry> =
            (1..8).map { ui ->
                val slot = ui - 1
                // getGamePathSlot only formats the slot's file name — it never checks the
                // file exists, so every slot would read as filled. Check the disk.
                val filled = runCatching {
                    NativeApp.getGamePathSlot(slot)?.takeIf { it.isNotBlank() }?.let { java.io.File(it).exists() }
                }.getOrNull() == true
                RetroMenuEntry.SaveSlot(
                    slot = ui,
                    title = activity.getString(R.string.retro_ps2_slot, ui),
                    subtitle =
                        when {
                            savesLoadMode && filled -> activity.getString(R.string.retro_ps2_slot_tap_to_load)
                            savesLoadMode -> activity.getString(R.string.retro_ps2_slot_empty)
                            filled -> activity.getString(R.string.retro_ps2_slot_tap_to_overwrite)
                            else -> activity.getString(R.string.retro_ps2_slot_empty_tap_to_save)
                        },
                    filled = filled,
                    onClick = {
                        if (savesLoadMode) {
                            if (!filled) {
                                Toast.makeText(activity, activity.getString(R.string.retro_ps2_slot_is_empty, ui), Toast.LENGTH_SHORT).show()
                            } else {
                                menu.close()
                                Thread {
                                    val ok = runCatching { NativeApp.loadStateFromSlot(slot) }.getOrDefault(false)
                                    activity.runOnUiThread {
                                        Toast.makeText(activity, if (ok) activity.getString(R.string.retro_ps2_loaded_slot, ui) else activity.getString(R.string.retro_ps2_could_not_load_slot, ui), Toast.LENGTH_SHORT).show()
                                        if (ok) { wnPaused = false; MainActivityRuntime.resume() }
                                    }
                                }.start()
                            }
                        } else {
                            Thread {
                                val ok = runCatching { NativeApp.saveStateToSlot(slot) }.getOrDefault(false)
                                activity.runOnUiThread {
                                    Toast.makeText(activity, if (ok) activity.getString(R.string.retro_ps2_saved_to_slot, ui) else activity.getString(R.string.retro_ps2_save_failed), Toast.LENGTH_SHORT).show()
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
                                when (assigned) { 1 -> activity.getString(R.string.retro_ps2_slot_1_suffix); 2 -> activity.getString(R.string.retro_ps2_slot_2_suffix); else -> activity.getString(R.string.retro_ps2_tap_to_use_suffix) },
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
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_new_memory_card), RetroDrawerIcons.Save) {
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
                add(RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_import_card), RetroDrawerIcons.Load) { launchMemcardImport?.invoke() })
            }

        fun networkEntries(): List<RetroMenuEntry> =
            buildList {
                val enabled = prefs.getBoolean("wn.ps2.net.enable", false)
                add(
                    RetroMenuEntry.Toggle(
                        activity.getString(R.string.retro_ps2_enable_online_dev9),
                        subtitle = activity.getString(R.string.retro_ps2_enable_online_subtitle),
                        checked = enabled,
                    ) { value ->
                        prefs.edit().putBoolean("wn.ps2.net.enable", value).apply()
                        bg {
                            writeNetworkSettings()
                            NativeApp.commitSettings()
                            activity.runOnUiThread {
                                menu.close()
                                MainActivityRuntime.restart()
                            }
                        }
                    },
                )
                add(
                    RetroMenuEntry.Toggle(
                        activity.getString(R.string.retro_ps2_hdd),
                        subtitle = activity.getString(R.string.retro_ps2_hdd_subtitle),
                        checked = prefs.getBoolean("wn.ps2.hdd", false),
                    ) { value ->
                        prefs.edit().putBoolean("wn.ps2.hdd", value).apply()
                        bg {
                            ensureHddImage()
                            writeNetworkSettings()
                            NativeApp.commitSettings()
                            activity.runOnUiThread {
                                menu.close()
                                MainActivityRuntime.restart()
                            }
                        }
                    },
                )
                if (!enabled) return@buildList
                val devices = listOf("Auto", "Wi-Fi")
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_ethernet_device),
                        devices,
                        devices.indexOf(prefs.getString("wn.ps2.net.ethdevice", "Auto")).coerceAtLeast(0),
                    ) { next ->
                        prefs.edit().putString("wn.ps2.net.ethdevice", devices[next]).apply()
                        applyNetwork()
                        menu.rebuild()
                    },
                )
                val dnsModes = listOf("Manual", "Auto", "Internal")
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_dns_mode),
                        dnsModes,
                        dnsModes.indexOf(prefs.getString("wn.ps2.net.dnsmode", "Manual")).coerceAtLeast(0),
                    ) { next ->
                        prefs.edit().putString("wn.ps2.net.dnsmode", dnsModes[next]).apply()
                        applyNetwork()
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.TextInput(activity.getString(R.string.retro_ps2_primary_dns), prefs.getString("wn.ps2.net.dns1", PS2_DEFAULT_DNS).orEmpty(), PS2_DEFAULT_DNS) {
                        netEdit.value = Ps2NetEdit(activity.getString(R.string.retro_ps2_primary_dns), prefs.getString("wn.ps2.net.dns1", PS2_DEFAULT_DNS).orEmpty(), PS2_DEFAULT_DNS) { v ->
                            prefs.edit().putString("wn.ps2.net.dns1", v.trim()).apply()
                            applyNetwork()
                            menu.rebuild()
                        }
                    },
                )
                add(
                    RetroMenuEntry.TextInput(activity.getString(R.string.retro_ps2_secondary_dns), prefs.getString("wn.ps2.net.dns2", "").orEmpty(), activity.getString(R.string.retro_ps2_optional)) {
                        netEdit.value = Ps2NetEdit(activity.getString(R.string.retro_ps2_secondary_dns), prefs.getString("wn.ps2.net.dns2", "").orEmpty(), "0.0.0.0") { v ->
                            prefs.edit().putString("wn.ps2.net.dns2", v.trim()).apply()
                            applyNetwork()
                            menu.rebuild()
                        }
                    },
                )
                add(
                    RetroMenuEntry.Toggle(
                        activity.getString(R.string.retro_ps2_force_all_dns),
                        subtitle = activity.getString(R.string.retro_ps2_force_all_dns_subtitle),
                        checked = prefs.getBoolean("wn.ps2.net.forceall", false),
                    ) { value ->
                        prefs.edit().putBoolean("wn.ps2.net.forceall", value).apply()
                        applyNetwork()
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(
                        activity.getString(R.string.retro_ps2_dnas_bypass),
                        subtitle = activity.getString(R.string.retro_ps2_dnas_bypass_subtitle),
                        checked = prefs.getBoolean(Ps2DnasBypass.PREF, true),
                    ) { value ->
                        prefs.edit().putBoolean(Ps2DnasBypass.PREF, value).apply()
                        kotlin.concurrent.thread(name = "ps2-dnas-bypass") { Ps2DnasBypass.applyWhenReady(activity) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_auto_ip_dhcp), checked = prefs.getBoolean("wn.ps2.net.dhcp", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.net.dhcp", value).apply()
                        applyNetwork()
                        menu.rebuild()
                    },
                )
                val hosts = readHosts()
                hosts.forEachIndexed { i, host ->
                    add(
                        RetroMenuEntry.TextInput(activity.getString(R.string.retro_ps2_server_host, host.url), host.ip, activity.getString(R.string.retro_ps2_tap_to_set_ip)) {
                            netEdit.value = Ps2NetEdit(activity.getString(R.string.retro_ps2_host_to_ip, host.url), host.ip, "0.0.0.0") { v ->
                                val list = readHosts().toMutableList()
                                if (i < list.size) {
                                    if (v.isBlank()) list.removeAt(i) else list[i] = list[i].copy(ip = v.trim())
                                    writeHosts(list)
                                    applyNetwork()
                                    menu.rebuild()
                                }
                            }
                        },
                    )
                }
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_add_server_host), RetroDrawerIcons.Add) {
                        netEdit.value = Ps2NetEdit(activity.getString(R.string.retro_ps2_new_server_hostname), "", activity.getString(R.string.retro_ps2_server_hostname_hint)) { v ->
                            if (v.isNotBlank()) {
                                writeHosts(readHosts() + Ps2NetHost(v.trim(), "0.0.0.0"))
                                // Host overrides only resolve via the Internal DNS server —
                                // flip the mode so the new host actually takes effect, and
                                // tell the user why their choice changed.
                                if ((prefs.getString("wn.ps2.net.dnsmode", "Manual") ?: "Manual") != "Internal") {
                                    prefs.edit().putString("wn.ps2.net.dnsmode", "Internal").apply()
                                    Toast.makeText(activity, activity.getString(R.string.retro_ps2_dns_auto_internal), Toast.LENGTH_LONG).show()
                                }
                                applyNetwork()
                                menu.rebuild()
                            }
                        }
                    },
                )
            }

        fun controlsEntries(): List<RetroMenuEntry> =
            buildList {
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_onscreen_controls), checked = touchVisible.value) { value ->
                        touchVisible.value = value
                        RetroDefaults.setTouchControls(activity, RetroSystems.PS2.id, value)
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_edit_layout), RetroDrawerIcons.EditLayout) {
                        menu.close()
                        touchVisible.value = true
                        pad?.enterEdit()
                    },
                )
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_reset_layout), RetroDrawerIcons.Reset) {
                        pad?.resetLayout()
                        Toast.makeText(activity, activity.getString(R.string.retro_ps2_layout_reset), Toast.LENGTH_SHORT).show()
                    },
                )
                val invPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                fun invToggle(label: String, key: String) =
                    RetroMenuEntry.Toggle(label, checked = invPrefs.getBoolean(key, false)) { value ->
                        invPrefs.edit().putBoolean(key, value).apply()
                        pad?.loadStickInversion()
                        menu.rebuild()
                    }
                add(invToggle(activity.getString(R.string.retro_ps2_left_stick_invert_x), "retro_inv_lx_ps2"))
                add(invToggle(activity.getString(R.string.retro_ps2_left_stick_invert_y), "retro_inv_ly_ps2"))
                add(invToggle(activity.getString(R.string.retro_ps2_right_stick_invert_x), "retro_inv_rx_ps2"))
                add(invToggle(activity.getString(R.string.retro_ps2_right_stick_invert_y), "retro_inv_ry_ps2"))
                add(
                    RetroMenuEntry.ColorPick(activity.getString(R.string.retro_ps2_button_color), customColors.button) { value ->
                        customColors = customColors.copy(button = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick(activity.getString(R.string.retro_ps2_letter_color), customColors.text) { value ->
                        customColors = customColors.copy(text = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick(activity.getString(R.string.retro_ps2_shadow_color), customColors.shadow) { value ->
                        customColors = customColors.copy(shadow = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.ColorPick(activity.getString(R.string.retro_ps2_background_color), customColors.body) { value ->
                        customColors = customColors.copy(body = value)
                        persistColors()
                    },
                )
                add(
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_reset_colors), RetroDrawerIcons.Reset) {
                        customColors = RetroCustomColors()
                        persistColors()
                    },
                )
            }

        fun displayEntries(): List<RetroMenuEntry> =
            buildList {
                val rendererKeys = listOf("vulkan", "opengl", "software")
                val rendererLabels = listOf(
                    activity.getString(R.string.retro_ps2_renderer_vulkan),
                    activity.getString(R.string.retro_ps2_renderer_opengl),
                    activity.getString(R.string.retro_ps2_renderer_software),
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_renderer),
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
                val scaleLabels = listOf(
                    activity.getString(R.string.retro_ps2_scale_1x_native),
                    activity.getString(R.string.retro_ps2_scale_1_5x),
                    activity.getString(R.string.retro_ps2_scale_2x),
                    activity.getString(R.string.retro_ps2_scale_3x),
                    activity.getString(R.string.retro_ps2_scale_4x),
                )
                val scaleIdx = scales.indexOfFirst { kotlin.math.abs(it - prefs.getFloat("wn.ps2.upscale", 1f)) < 0.01f }.coerceAtLeast(0)
                add(
                    RetroMenuEntry.Choice(activity.getString(R.string.retro_ps2_resolution_scale), scaleLabels, scaleIdx) { next ->
                        prefs.edit().putFloat("wn.ps2.upscale", scales[next]).apply()
                        bg { NativeApp.renderUpscalemultiplier(scales[next]) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_aspect_ratio),
                        listOf(
                            activity.getString(R.string.retro_ps2_aspect_stretch),
                            activity.getString(R.string.retro_ps2_aspect_auto_standard),
                            activity.getString(R.string.retro_ps2_aspect_4_3),
                            activity.getString(R.string.retro_ps2_aspect_16_9),
                        ),
                        prefs.getInt("wn.ps2.aspect", 1).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.aspect", next).apply()
                        bg { NativeApp.setAspectRatio(next) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_fmv_aspect_ratio),
                        listOf(
                            activity.getString(R.string.retro_ps2_shader_off),
                            activity.getString(R.string.retro_ps2_aspect_auto_standard),
                            activity.getString(R.string.retro_ps2_aspect_4_3),
                            activity.getString(R.string.retro_ps2_aspect_16_9),
                        ),
                        prefs.getInt("wn.ps2.fmvaspect", 0).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.fmvaspect", next).apply()
                        gsSetAsync("FMVAspectRatioSwitch", "string", fmvAspectName(next))
                        menu.rebuild()
                    },
                )
                val deintLabels = listOf(
                    activity.getString(R.string.retro_ps2_deint_auto),
                    activity.getString(R.string.retro_ps2_deint_off),
                    activity.getString(R.string.retro_ps2_deint_weave_tff),
                    activity.getString(R.string.retro_ps2_deint_weave_bff),
                    activity.getString(R.string.retro_ps2_deint_bob_tff),
                    activity.getString(R.string.retro_ps2_deint_bob_bff),
                    activity.getString(R.string.retro_ps2_deint_blend_tff),
                    activity.getString(R.string.retro_ps2_deint_blend_bff),
                    activity.getString(R.string.retro_ps2_deint_adaptive_tff),
                    activity.getString(R.string.retro_ps2_deint_adaptive_bff),
                )
                add(
                    RetroMenuEntry.Choice(activity.getString(R.string.retro_ps2_deinterlace_mode), deintLabels, prefs.getInt("wn.ps2.deinterlace", 0).coerceIn(0, 9)) { next ->
                        prefs.edit().putInt("wn.ps2.deinterlace", next).apply()
                        gsSetAsync("deinterlace_mode", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_display_filter),
                        listOf(
                            activity.getString(R.string.retro_ps2_filter_nearest),
                            activity.getString(R.string.retro_ps2_filter_bilinear_smooth),
                            activity.getString(R.string.retro_ps2_filter_bilinear_sharp),
                        ),
                        prefs.getInt("wn.ps2.displayfilter", 1).coerceIn(0, 2),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.displayfilter", next).apply()
                        gsSetAsync("linear_present_mode", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_texture_filter),
                        listOf(
                            activity.getString(R.string.retro_ps2_filter_nearest),
                            activity.getString(R.string.retro_ps2_filter_bilinear_forced),
                            activity.getString(R.string.retro_ps2_filter_bilinear_ps2),
                            activity.getString(R.string.retro_ps2_filter_bilinear_sprites),
                        ),
                        prefs.getInt("wn.ps2.filter", 2).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.filter", next).apply()
                        gsSetAsync("filter", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_mipmapping), checked = prefs.getBoolean("wn.ps2.mipmap", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.mipmap", value).apply()
                        gsSetAsync("hw_mipmap", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_blending_accuracy),
                        listOf(
                            activity.getString(R.string.retro_ps2_blend_minimum),
                            activity.getString(R.string.retro_ps2_blend_basic),
                            activity.getString(R.string.retro_ps2_blend_medium),
                            activity.getString(R.string.retro_ps2_blend_high),
                            activity.getString(R.string.retro_ps2_blend_full),
                            activity.getString(R.string.retro_ps2_blend_maximum),
                        ),
                        prefs.getInt("wn.ps2.blend", 1).coerceIn(0, 5),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.blend", next).apply()
                        gsSetAsync("accurate_blending_unit", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_anti_blur), checked = prefs.getBoolean("wn.ps2.antiblur", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.antiblur", value).apply()
                        gsSetAsync("pcrtc_antiblur", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_crt_tv_shader),
                        listOf(
                            activity.getString(R.string.retro_ps2_shader_off),
                            activity.getString(R.string.retro_ps2_shader_scanline),
                            activity.getString(R.string.retro_ps2_shader_diagonal),
                            activity.getString(R.string.retro_ps2_shader_triangular),
                            activity.getString(R.string.retro_ps2_shader_wave),
                            activity.getString(R.string.retro_ps2_shader_lottes),
                            activity.getString(R.string.retro_ps2_shader_4xrgss),
                            activity.getString(R.string.retro_ps2_shader_nxagss),
                        ),
                        prefs.getInt("wn.ps2.tvshader", 0).coerceIn(0, 7),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.tvshader", next).apply()
                        bg { NativeApp.renderTvShader(next) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_frame_skip),
                        listOf(
                            activity.getString(R.string.retro_ps2_frameskip_off),
                            activity.getString(R.string.retro_ps2_frameskip_1),
                            activity.getString(R.string.retro_ps2_frameskip_2),
                            activity.getString(R.string.retro_ps2_frameskip_3),
                        ),
                        prefs.getInt("wn.ps2.frameskip", 0).coerceIn(0, 3),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.frameskip", next).apply()
                        bg { NativeApp.setFrameSkip(next) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_widescreen_patches), checked = prefs.getBoolean("wn.ps2.widescreen", false)) { value ->
                        prefs.edit().putBoolean("wn.ps2.widescreen", value).apply()
                        bg {
                            NativeApp.setSetting("EmuCore", "EnableWideScreenPatches", "bool", value.toString())
                            NativeApp.commitSettings()
                            NativeApp.reloadPatches()
                        }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_no_interlace_patches), checked = prefs.getBoolean("wn.ps2.nointerlace", false)) { value ->
                        prefs.edit().putBoolean("wn.ps2.nointerlace", value).apply()
                        bg {
                            NativeApp.setSetting("EmuCore", "EnableNoInterlacingPatches", "bool", value.toString())
                            NativeApp.commitSettings()
                            NativeApp.reloadPatches()
                        }
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
                        label = activity.getString(R.string.retro_ps2_volume),
                        valueText = activity.getString(R.string.retro_ps2_volume_percent, volume),
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
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_mute), checked = muted) { value ->
                        prefs.edit().putBoolean("wn.ps2.muted", value).apply()
                        bg { NativeApp.setAudioMuted(value) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_time_stretch), checked = prefs.getBoolean("wn.ps2.timestretch", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.timestretch", value).apply()
                        spu2Set("SyncMode", "string", if (value) "TimeStretch" else "Disabled")
                        menu.rebuild()
                    },
                )
                val bufferValues = listOf(40, 50, 60, 80, 100, 120, 160, 200)
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_audio_buffer),
                        bufferValues.map { activity.getString(R.string.retro_ps2_ms, it) },
                        bufferValues.indexOf(prefs.getInt("wn.ps2.audiobuffer", 50)).coerceAtLeast(0),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.audiobuffer", bufferValues[next]).apply()
                        spu2Set("BufferMS", "int", bufferValues[next].toString())
                        menu.rebuild()
                    },
                )
                val latencyValues = listOf(10, 15, 20, 30, 40, 60, 80, 100)
                add(
                    RetroMenuEntry.Choice(
                        activity.getString(R.string.retro_ps2_audio_latency),
                        latencyValues.map { activity.getString(R.string.retro_ps2_ms, it) },
                        latencyValues.indexOf(prefs.getInt("wn.ps2.audiolatency", 20)).coerceAtLeast(0),
                    ) { next ->
                        prefs.edit().putInt("wn.ps2.audiolatency", latencyValues[next]).apply()
                        spu2Set("OutputLatencyMS", "int", latencyValues[next].toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_swap_stereo_channels), checked = swap) { value ->
                        prefs.edit().putBoolean("wn.ps2.swap", value).apply()
                        bg { NativeApp.setAudioSwapChannels(value) }
                        menu.rebuild()
                    },
                )
            }

        fun performanceEntries(): List<RetroMenuEntry> =
            buildList {
                val rateValues = listOf(-3, -2, -1, 0, 1, 2, 3)
                val rateLabels = listOf(
                    activity.getString(R.string.retro_ps2_rate_50),
                    activity.getString(R.string.retro_ps2_rate_60),
                    activity.getString(R.string.retro_ps2_rate_75),
                    activity.getString(R.string.retro_ps2_rate_100_default),
                    activity.getString(R.string.retro_ps2_rate_130),
                    activity.getString(R.string.retro_ps2_rate_180),
                    activity.getString(R.string.retro_ps2_rate_300),
                )
                val curRate = prefs.getInt("wn.ps2.eeRate", 0).coerceIn(-3, 3)
                add(
                    RetroMenuEntry.Choice(activity.getString(R.string.retro_ps2_ee_cycle_rate), rateLabels, rateValues.indexOf(curRate).coerceAtLeast(0)) { next ->
                        prefs.edit().putInt("wn.ps2.eeRate", rateValues[next]).apply()
                        bg { NativeApp.speedhackEecyclerate(rateValues[next]) }
                        spSet("EECycleRate", "int", rateValues[next].toString())
                        menu.rebuild()
                    },
                )
                val skipLabels = listOf(
                    activity.getString(R.string.retro_ps2_skip_off),
                    activity.getString(R.string.retro_ps2_skip_1),
                    activity.getString(R.string.retro_ps2_skip_2),
                    activity.getString(R.string.retro_ps2_skip_3),
                )
                add(
                    RetroMenuEntry.Choice(activity.getString(R.string.retro_ps2_ee_cycle_skip), skipLabels, prefs.getInt("wn.ps2.eeSkip", 0).coerceIn(0, 3)) { next ->
                        prefs.edit().putInt("wn.ps2.eeSkip", next).apply()
                        bg { NativeApp.speedhackEecycleskip(next) }
                        spSet("EECycleSkip", "int", next.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_mtvu), checked = prefs.getBoolean("wn.ps2.mtvu", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.mtvu", value).apply()
                        spSet("vuThread", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_instant_vu1), checked = prefs.getBoolean("wn.ps2.instantVu1", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.instantVu1", value).apply()
                        bg { NativeApp.setInstantVU1(value) }
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_vu_flag_hack), checked = prefs.getBoolean("wn.ps2.vuFlagHack", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.vuFlagHack", value).apply()
                        spSet("vuFlagHack", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_intc_spin), checked = prefs.getBoolean("wn.ps2.intc", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.intc", value).apply()
                        spSet("IntcStat", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_wait_loop), checked = prefs.getBoolean("wn.ps2.waitloop", true)) { value ->
                        prefs.edit().putBoolean("wn.ps2.waitloop", value).apply()
                        spSet("WaitLoop", "bool", value.toString())
                        menu.rebuild()
                    },
                )
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_fast_cdvd), checked = prefs.getBoolean("wn.ps2.fastCdvd", false)) { value ->
                        prefs.edit().putBoolean("wn.ps2.fastCdvd", value).apply()
                        spSet("fastCDVD", "bool", value.toString())
                        menu.rebuild()
                    },
                )
            }

        fun hudEntries(): List<RetroMenuEntry> =
            buildList {
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_fps), checked = osd("fps")) { v -> setOsd("fps", v) { NativeApp.osdShowFPS(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_emulation_speed), checked = osd("speed")) { v -> setOsd("speed", v) { NativeApp.osdShowSpeed(it) } })
                add(
                    RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_internal_resolution), checked = osd("res")) { v ->
                        setOsd("res", v) { NativeApp.osdShowResolution(it) }
                    },
                )
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_cpu_usage), checked = osd("cpu")) { v -> setOsd("cpu", v) { NativeApp.osdShowCPU(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_gpu_usage), checked = osd("gpu")) { v -> setOsd("gpu", v) { NativeApp.osdShowGPU(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_frame_times), checked = osd("frametimes")) { v -> setOsd("frametimes", v) { NativeApp.osdShowFrameTimes(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_gs_stats), checked = osd("gsstats")) { v -> setOsd("gsstats", v) { NativeApp.osdShowGSStats(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_input_display), checked = osd("inputs")) { v -> setOsd("inputs", v) { NativeApp.osdShowInputs(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_hw_info), checked = osd("hwinfo")) { v -> setOsd("hwinfo", v) { NativeApp.osdShowHardwareInfo(it) } })
                add(RetroMenuEntry.Toggle(activity.getString(R.string.retro_ps2_hud_version), checked = osd("version")) { v -> setOsd("version", v) { NativeApp.osdShowVersion(it) } })
            }

        menu.tabs =
            listOf(
                RetroTabSpec(null, Icons.Outlined.Apps, activity.getString(R.string.retro_ps2_tab_menu)),
                RetroTabSpec(RetroPane.DISPLAY, Icons.Outlined.Monitor, activity.getString(R.string.retro_ps2_tab_display)),
                RetroTabSpec(RetroPane.PERFORMANCE, Icons.Outlined.Bolt, activity.getString(R.string.retro_ps2_tab_performance)),
                RetroTabSpec(RetroPane.HUD, Icons.Outlined.Speed, activity.getString(R.string.retro_ps2_tab_hud)),
                RetroTabSpec(RetroPane.SOUND, Icons.AutoMirrored.Outlined.VolumeUp, activity.getString(R.string.retro_ps2_tab_sound)),
                RetroTabSpec(RetroPane.NETWORK, Icons.Outlined.Public, activity.getString(R.string.retro_ps2_tab_online)),
                RetroTabSpec(RetroPane.CONTROLS, Icons.Outlined.SportsEsports, activity.getString(R.string.retro_ps2_tab_controls)),
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
                RetroPane.NETWORK -> networkEntries()
            }
        }
        menu.bottomProvider = {
            listOf(
                if (wnPaused) {
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_resume), RetroDrawerIcons.Resume, active = true) {
                        wnPaused = false
                        MainActivityRuntime.resume()
                        menu.close()
                    }
                } else {
                    RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_pause), RetroDrawerIcons.Pause) {
                        wnPaused = true
                        menu.rebuild()
                    }
                },
                RetroMenuEntry.Action(activity.getString(R.string.retro_ps2_exit), RetroDrawerIcons.Exit, danger = true) {
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

        // Back gesture/button and the controller guide button (Xbox/PS) toggle the
        // drawer via this hook — the menu stays reachable even with touch controls
        // hidden.
        WinNativeHost.openMenu = {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    if (menu.visible) {
                        menu.close()
                    } else {
                        pad?.releaseAll()
                        menu.rebuild()
                        menu.open()
                    }
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
                            // Opening the drawer does NOT pause the game — online
                            // titles must keep their connection alive while the
                            // user tweaks settings. Pausing is explicit via the
                            // Pause action (wnPaused); closing the drawer resumes
                            // only when nothing else holds the game paused.
                            if (!menuVisible && !wnPaused && ps2Screen.value == null) {
                                MainActivityRuntime.resume()
                            }
                        }
                        val screen by ps2Screen
                        // System Back (button or predictive-back gesture) — this is
                        // the ONLY reliable path on Android 13+ where
                        // enableOnBackInvokedCallback routes Back through the
                        // predictive-back dispatcher, bypassing legacy key handling.
                        // Contextual: exit pad-edit, else step back inside the open
                        // drawer (closing it at the top level), else open the drawer.
                        // Always keeps the game alive — Back never finishes the activity.
                        BackHandler(enabled = screen == null) {
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
                                        view.loadStickInversion()
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
                            Ps2NetEditDialog(netEdit)
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
                        // NOTE: the system Back button/gesture is handled by the
                        // Compose BackHandler in the overlay content (via
                        // onBackPressedDispatcher), NOT here — with
                        // enableOnBackInvokedCallback=true, Android 13+ routes Back
                        // through the predictive-back dispatcher and it never
                        // reaches this legacy key path. This callback still routes
                        // OTHER controller keys (D-pad/A/B) to the open drawer.
                        if (ps2Screen.value == null && menu.visible &&
                            event.keyCode != KeyEvent.KEYCODE_BACK &&
                            menu.handleKey(event.keyCode, event.action)
                        ) {
                            return true
                        }
                        return prevCallback.dispatchKeyEvent(event)
                    }

                    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
                        if (ps2Screen.value == null && menu.visible &&
                            event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK
                        ) {
                            val x = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X).takeIf { kotlin.math.abs(it) > 0.5f }
                                ?: event.getAxisValue(android.view.MotionEvent.AXIS_X)
                            val y = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y).takeIf { kotlin.math.abs(it) > 0.5f }
                                ?: event.getAxisValue(android.view.MotionEvent.AXIS_Y)
                            activity.runOnUiThread { menu.handleAxis(x, y) }
                            return true
                        }
                        return prevCallback.dispatchGenericMotionEvent(event)
                    }
                }
        }
    }
}
