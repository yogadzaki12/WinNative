package com.winlator.cmod.feature.retro

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.winlator.cmod.shared.ui.nav.paneNavItem
import java.io.File
import kotlinx.coroutines.launch

private val PageBg = Color(0xFF101018)
private val PageText = Color(0xFFF0F4FF)
private val PageSub = Color(0xFF93A6BC)

private val SHADER_KEYS = listOf("default", "crt", "lcd", "sharp")
private val SHADER_LABELS = listOf("Default", "CRT", "LCD", "Sharp")
private val UPSCALE_KEYS = listOf("2x", "4x", "native")
private val UPSCALE_LABELS = listOf("2x", "4x", "Native")

@Composable
fun RetroDefaultsScreen() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var expandedConsole by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmHardcore by remember { mutableStateOf(false) }
    var creditsTab by remember { mutableIntStateOf(0) }

    if (confirmHardcore) {
        RetroHardcoreConfirmDialog(
            onConfirm = {
                confirmHardcore = false
                RetroAchievementsManager.setHardcorePreferred(context, true)
                refresh++
            },
            onDismiss = { confirmHardcore = false },
        )
    }

    val biosPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        RetroBiosImport.importFromUri(context, uri)
                    }
                    result
                        .onSuccess { Toast.makeText(context, "BIOS imported: $it", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "Invalid BIOS file", Toast.LENGTH_LONG).show() }
                    refresh++
                }
            }
        }

    val ps2BiosPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        RetroBiosImport.importPs2FromUri(context, uri)
                    }
                    result
                        .onSuccess { Toast.makeText(context, "PS2 BIOS imported: $it", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "Invalid PS2 BIOS file", Toast.LENGTH_LONG).show() }
                    refresh++
                }
            }
        }

    @Suppress("UNUSED_EXPRESSION") refresh

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PageBg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RetroSettingsTabBar(creditsTab) { creditsTab = it }
        if (creditsTab == 0) {
        Text(
            "RETRO DEFAULTS",
            color = PageSub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            "Defaults applied to each console's games unless overridden in a game's own settings.",
            color = PageSub,
            style = MaterialTheme.typography.bodySmall,
        )

        RetroSettingGroup {
            RetroGroupTitle("RETROACHIEVEMENTS")
            RetroInfoRow(
                "Account",
                if (RetroAchievementsManager.isLoggedIn(context)) {
                    RetroAchievementsManager.displayName(context) ?: "Signed in"
                } else {
                    "Not signed in — open a game's Achievements to sign in"
                },
            )
            RetroSettingSwitch(
                "Achievements enabled",
                RetroAchievementsManager.isEnabled(context),
            ) {
                RetroAchievementsManager.setEnabled(context, it)
                refresh++
            }
            RetroSettingSwitch(
                "Hardcore mode by default (no save states)",
                RetroAchievementsManager.isHardcorePreferred(context),
            ) {
                if (it) {
                    confirmHardcore = true
                } else {
                    RetroAchievementsManager.setHardcorePreferred(context, false)
                    refresh++
                }
            }
            if (RetroAchievementsManager.isLoggedIn(context)) {
                OutlinedButton(
                    onClick = {
                        RetroAchievementsManager.logout(context)
                        refresh++
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("Sign Out")
                }
            }
        }

        RetroSettingGroup {
            RetroGroupTitle("PLAYSTATION BIOS")
            val dir = RetroCoreManager.systemDir(context)
            val installed = RetroSystems.PSX.biosFiles.filter { File(dir, it).isFile }
            RetroInfoRow(
                "Installed",
                if (installed.isEmpty()) "None — PS1 games require a BIOS" else installed.joinToString(", "),
            )
            Button(
                onClick = { runCatching { biosPicker.launch(arrayOf("*/*")) } },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text("Import PS1 BIOS…")
            }
            if (installed.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val n = RetroBiosImport.deletePs1Bios(context)
                        Toast.makeText(context, if (n > 0) "PS1 BIOS removed" else "No BIOS to remove", Toast.LENGTH_SHORT).show()
                        refresh++
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("Remove PS1 BIOS")
                }
            }
        }

        RetroSettingGroup {
            RetroGroupTitle("PLAYSTATION 2 BIOS")
            val ps2Installed = RetroBiosImport.installedPs2Bios(context)
            RetroInfoRow(
                "Installed",
                if (ps2Installed.isEmpty()) "None — PS2 games require a BIOS" else ps2Installed.joinToString(", "),
            )
            RetroInfoRow(
                "Format",
                "Merged single-file dump (region-tagged .bin, ~4MB). Split ROM0/MEC/NVM sets are not accepted.",
            )
            Button(
                onClick = { runCatching { ps2BiosPicker.launch(arrayOf("*/*")) } },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text("Import PS2 BIOS…")
            }
            if (ps2Installed.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val n = RetroBiosImport.deletePs2Bios(context)
                        Toast.makeText(context, if (n > 0) "PS2 BIOS removed" else "No BIOS to remove", Toast.LENGTH_SHORT).show()
                        refresh++
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("Remove PS2 BIOS")
                }
            }
        }

        RetroSettingGroup {
            RetroGroupTitle("ROMS FOLDER")
            val romsDir = RetroDefaults.romsDir(context)
            RetroInfoRow(
                "Folder",
                romsDir ?: "Not set — pick a folder to auto-import games",
            )
            RetroInfoRow(
                "Auto-import",
                "New games in this folder are added to your library automatically with the right console detected.",
            )
            Button(
                onClick = {
                    val activity = context as? android.app.Activity ?: return@Button
                    com.winlator.cmod.shared.android.DirectoryPickerDialog.show(
                        activity = activity,
                        initialPath = romsDir
                            ?: android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS,
                            ).absolutePath,
                        title = "Select ROMs Folder",
                    ) { path ->
                        RetroDefaults.setRomsDir(context, path)
                        Thread {
                            val result = RetroRomScanner.scan(context, File(path))
                            activity.runOnUiThread {
                                Toast.makeText(context, scanMessage(result), Toast.LENGTH_SHORT).show()
                                refresh++
                            }
                        }.start()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(if (romsDir == null) "Select ROMs Folder…" else "Change ROMs Folder…")
            }
            if (romsDir != null) {
                OutlinedButton(
                    onClick = {
                        val activity = context as? android.app.Activity
                        Thread {
                            val result = RetroRomScanner.scanConfiguredFolder(context)
                            activity?.runOnUiThread {
                                Toast.makeText(context, scanMessage(result), Toast.LENGTH_SHORT).show()
                                refresh++
                            }
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("Scan Now")
                }
            }
        }

        Text(
            "CONSOLE DEFAULTS",
            color = PageSub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Tap a console to set its default settings.",
            color = PageSub,
            style = MaterialTheme.typography.labelMedium,
        )

        RetroSystems.ALL.forEach { console ->
            val sys = console.id
            val expanded = expandedConsole == sys
            RetroSettingGroup {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { expandedConsole = if (expanded) null else sys }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        console.displayName,
                        color = PageText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        if (expanded) "▲" else "▼",
                        color = PageSub,
                    )
                }
                if (expanded && console.isExternal) {
                    val ps2Prefs = context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE)
                    val rendererKeys = listOf("vulkan", "opengl", "software")
                    val rendererLabels = listOf("Vulkan", "OpenGL", "Software")
                    RetroSettingDropdown(
                        label = "Renderer",
                        entries = rendererLabels,
                        selectedIndex = rendererKeys.indexOf(ps2Prefs.getString("wn.ps2.renderer", "vulkan")).coerceAtLeast(0),
                        onSelected = { ps2Prefs.edit().putString("wn.ps2.renderer", rendererKeys[it]).apply(); refresh++ },
                    )
                    val ps2Scales = listOf(1f, 1.5f, 2f, 3f, 4f)
                    val ps2ScaleLabels = listOf("1x (Native)", "1.5x", "2x", "3x", "4x")
                    val curScale = ps2Prefs.getFloat("wn.ps2.upscale", 1f)
                    RetroSettingDropdown(
                        label = "Upscale resolution",
                        entries = ps2ScaleLabels,
                        selectedIndex = ps2Scales.indexOfFirst { kotlin.math.abs(it - curScale) < 0.01f }.coerceAtLeast(0),
                        onSelected = { ps2Prefs.edit().putFloat("wn.ps2.upscale", ps2Scales[it]).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "Aspect Ratio",
                        entries = listOf("Stretch", "Auto (Standard)", "4:3", "16:9"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.aspect", 1).coerceIn(0, 3),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.aspect", it).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "Display Filter",
                        entries = listOf("Nearest", "Bilinear (Smooth)", "Bilinear (Sharp)"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.displayfilter", 1).coerceIn(0, 2),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.displayfilter", it).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "Texture Filter",
                        entries = listOf("Nearest", "Bilinear (Forced)", "Bilinear (PS2)", "Bilinear (Sprites)"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.filter", 2).coerceIn(0, 3),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.filter", it).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "Blending Accuracy",
                        entries = listOf("Minimum", "Basic", "Medium", "High", "Full", "Maximum"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.blend", 1).coerceIn(0, 5),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.blend", it).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "CRT / TV Shader",
                        entries = listOf("Off", "Scanline", "Diagonal", "Triangular", "Wave", "Lottes", "4xRGSS", "NxAGSS"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.tvshader", 0).coerceIn(0, 7),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.tvshader", it).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "Frame Skip",
                        entries = listOf("Off", "Skip 1", "Skip 2", "Skip 3"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.frameskip", 0).coerceIn(0, 3),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.frameskip", it).apply(); refresh++ },
                    )
                    RetroSettingSwitch("Mipmapping", ps2Prefs.getBoolean("wn.ps2.mipmap", true)) {
                        ps2Prefs.edit().putBoolean("wn.ps2.mipmap", it).apply(); refresh++
                    }
                    val eeRates = listOf(-3, -2, -1, 0, 1, 2, 3)
                    RetroSettingDropdown(
                        label = "EE Cycle Rate",
                        entries = listOf("50%", "60%", "75%", "100% (Default)", "130%", "180%", "300%"),
                        selectedIndex = eeRates.indexOf(ps2Prefs.getInt("wn.ps2.eeRate", 0).coerceIn(-3, 3)).coerceAtLeast(0),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.eeRate", eeRates[it]).apply(); refresh++ },
                    )
                    RetroSettingDropdown(
                        label = "EE Cycle Skip",
                        entries = listOf("Off", "1", "2", "3"),
                        selectedIndex = ps2Prefs.getInt("wn.ps2.eeSkip", 0).coerceIn(0, 3),
                        onSelected = { ps2Prefs.edit().putInt("wn.ps2.eeSkip", it).apply(); refresh++ },
                    )
                    RetroSettingSwitch("Instant VU1", ps2Prefs.getBoolean("wn.ps2.instantVu1", true)) {
                        ps2Prefs.edit().putBoolean("wn.ps2.instantVu1", it).apply(); refresh++
                    }
                    RetroSettingSwitch("Multi-Threaded VU (MTVU)", ps2Prefs.getBoolean("wn.ps2.mtvu", true)) {
                        ps2Prefs.edit().putBoolean("wn.ps2.mtvu", it).apply(); refresh++
                    }
                    RetroSettingSwitch("Fast CDVD", ps2Prefs.getBoolean("wn.ps2.fastCdvd", false)) {
                        ps2Prefs.edit().putBoolean("wn.ps2.fastCdvd", it).apply(); refresh++
                    }
                    RetroSettingSwitch(
                        "On-screen touch controls",
                        RetroDefaults.touchControls(context, sys),
                    ) { RetroDefaults.setTouchControls(context, sys, it); refresh++ }
                    RetroSettingSwitch(
                        "Sound",
                        !ps2Prefs.getBoolean("wn.ps2.muted", false),
                    ) { ps2Prefs.edit().putBoolean("wn.ps2.muted", !it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "Swap Stereo Channels",
                        ps2Prefs.getBoolean("wn.ps2.swap", false),
                    ) { ps2Prefs.edit().putBoolean("wn.ps2.swap", it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "HUD: FPS",
                        ps2Prefs.getBoolean("wn.osd.fps", false),
                    ) { ps2Prefs.edit().putBoolean("wn.osd.fps", it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "HUD: Emulation Speed",
                        ps2Prefs.getBoolean("wn.osd.speed", false),
                    ) { ps2Prefs.edit().putBoolean("wn.osd.speed", it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "HUD: CPU Usage",
                        ps2Prefs.getBoolean("wn.osd.cpu", false),
                    ) { ps2Prefs.edit().putBoolean("wn.osd.cpu", it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "HUD: GPU Usage",
                        ps2Prefs.getBoolean("wn.osd.gpu", false),
                    ) { ps2Prefs.edit().putBoolean("wn.osd.gpu", it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "HUD: Internal Resolution",
                        ps2Prefs.getBoolean("wn.osd.res", false),
                    ) { ps2Prefs.edit().putBoolean("wn.osd.res", it).apply(); refresh++ }
                    RetroSettingSwitch(
                        "Enable Online (DEV9)",
                        ps2Prefs.getBoolean("wn.ps2.net.enable", false),
                    ) { ps2Prefs.edit().putBoolean("wn.ps2.net.enable", it).apply(); refresh++ }
                    if (ps2Prefs.getBoolean("wn.ps2.net.enable", false)) {
                        val devices = listOf("Auto", "Wi-Fi")
                        RetroSettingDropdown(
                            label = "Ethernet Device",
                            entries = devices,
                            selectedIndex = devices.indexOf(ps2Prefs.getString("wn.ps2.net.ethdevice", "Auto")).coerceAtLeast(0),
                            onSelected = { ps2Prefs.edit().putString("wn.ps2.net.ethdevice", devices[it]).apply(); refresh++ },
                        )
                        val dnsModes = listOf("Manual", "Auto", "Internal")
                        RetroSettingDropdown(
                            label = "DNS Mode",
                            entries = dnsModes,
                            selectedIndex = dnsModes.indexOf(ps2Prefs.getString("wn.ps2.net.dnsmode", "Manual")).coerceAtLeast(0),
                            onSelected = { ps2Prefs.edit().putString("wn.ps2.net.dnsmode", dnsModes[it]).apply(); refresh++ },
                        )
                        RetroSettingTextField("Primary DNS", ps2Prefs.getString("wn.ps2.net.dns1", PS2_DEFAULT_DNS).orEmpty(), PS2_DEFAULT_DNS) { ps2Prefs.edit().putString("wn.ps2.net.dns1", it).apply(); refresh++ }
                        RetroSettingTextField("Secondary DNS", ps2Prefs.getString("wn.ps2.net.dns2", "").orEmpty(), "optional") { ps2Prefs.edit().putString("wn.ps2.net.dns2", it).apply(); refresh++ }
                        RetroSettingSwitch("Auto IP (DHCP)", ps2Prefs.getBoolean("wn.ps2.net.dhcp", true)) { ps2Prefs.edit().putBoolean("wn.ps2.net.dhcp", it).apply(); refresh++ }
                    }
                }
                if (expanded && !console.isExternal) {
                    RetroSettingDropdown(
                        label = "Shader",
                        entries = SHADER_LABELS,
                        selectedIndex = SHADER_KEYS.indexOf(RetroDefaults.shader(context, sys)).coerceAtLeast(0),
                        onSelected = { RetroDefaults.setShader(context, sys, SHADER_KEYS[it]); refresh++ },
                    )
                    RetroSettingSwitch(
                        "SGSR upscaling",
                        RetroDefaults.sgsr(context, sys),
                    ) { RetroDefaults.setSgsr(context, sys, it); refresh++ }
                    RetroSettingDropdown(
                        label = "Upscale resolution",
                        entries = UPSCALE_LABELS,
                        selectedIndex = UPSCALE_KEYS.indexOf(RetroDefaults.upscale(context, sys)).coerceAtLeast(0),
                        onSelected = { RetroDefaults.setUpscale(context, sys, UPSCALE_KEYS[it]); refresh++ },
                    )
                    RetroCoreOptions.forSystem(console).forEach { option ->
                        val current = RetroDefaults.coreOption(context, sys, option.key, option.defaultValue)
                        RetroSettingDropdown(
                            label = option.label,
                            entries = option.valueLabels,
                            selectedIndex = option.values.indexOf(current).coerceAtLeast(0),
                            onSelected = { RetroDefaults.setCoreOption(context, sys, option.key, option.values[it]); refresh++ },
                        )
                    }
                    RetroSettingSwitch(
                        "On-screen touch controls",
                        RetroDefaults.touchControls(context, sys),
                    ) { RetroDefaults.setTouchControls(context, sys, it); refresh++ }
                    RetroSettingSwitch(
                        "Sound",
                        RetroDefaults.audio(context, sys),
                    ) { RetroDefaults.setAudio(context, sys, it); refresh++ }
                    RetroSettingSwitch(
                        "Performance HUD",
                        RetroDefaults.hud(context, sys),
                    ) { RetroDefaults.setHud(context, sys, it); refresh++ }
                }
            }
        }
        }

        if (creditsTab == 1) {
        Text(
            "CREDITS & LICENSES",
            color = PageSub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "WinNative's retro features are built on these open-source projects. Tap to view each source.",
            color = PageSub,
            style = MaterialTheme.typography.labelMedium,
        )
        RetroSettingGroup {
            RETRO_CREDITS.forEach { credit ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(credit.url),
                                        ),
                                    )
                                }
                            }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(credit.name, color = PageText, style = MaterialTheme.typography.bodyMedium)
                        Text(credit.detail, color = PageSub, fontSize = 11.sp)
                    }
                    Text(credit.license, color = PageSub, fontSize = 11.sp)
                }
            }
        }
        }
    }
}

private fun scanMessage(result: RetroRomScanner.Result): String {
    val parts = buildList {
        if (result.added > 0) add("added ${result.added}")
        if (result.removed > 0) add("removed ${result.removed}")
    }
    return if (parts.isEmpty()) "Library up to date" else "ROMs: ${parts.joinToString(", ")}"
}

@Composable
private fun RetroSettingsTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Defaults", "Credits")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A26))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Color(0xFF1A9FFF).copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) }
                        .paneNavItem(
                            cornerRadius = 8.dp,
                            onActivate = { onSelect(index) },
                            highlightColor = Color(0xFF4FC3F7),
                            tapToSelect = true,
                        )
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Color(0xFF58A6FF) else PageSub,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

private data class RetroCredit(
    val name: String,
    val detail: String,
    val license: String,
    val url: String,
)

private val RETRO_CREDITS =
    listOf(
        RetroCredit("ARMSX2", "PlayStation 2", "GPL-3.0", "https://github.com/ARMSX2/ARMSX2"),
        RetroCredit("PCSX2", "PS2 upstream of ARMSX2", "GPL-3.0", "https://github.com/pcsx2/pcsx2"),
        RetroCredit("FCEUmm", "NES", "GPL-2.0", "https://github.com/libretro/libretro-fceumm"),
        RetroCredit("Snes9x", "SNES", "Snes9x", "https://github.com/libretro/snes9x"),
        RetroCredit("Gambatte", "Game Boy / Color", "GPL-2.0", "https://github.com/libretro/gambatte-libretro"),
        RetroCredit("mGBA", "Game Boy Advance", "MPL-2.0", "https://github.com/libretro/mgba"),
        RetroCredit("Genesis Plus GX", "Genesis / SMS / GG", "GPX", "https://github.com/libretro/Genesis-Plus-GX"),
        RetroCredit("ParaLLEl N64", "Nintendo 64", "GPL-2.0", "https://github.com/libretro/parallel-n64"),
        RetroCredit("Beetle PSX", "PlayStation", "GPL-2.0", "https://github.com/libretro/beetle-psx-libretro"),
        RetroCredit("SwanStation", "PlayStation", "GPL-3.0", "https://github.com/libretro/swanstation"),
        RetroCredit("LibretroDroid", "libretro frontend", "GPL-3.0", "https://github.com/Swordfish90/LibretroDroid"),
        RetroCredit("rcheevos", "RetroAchievements", "MIT", "https://github.com/RetroAchievements/rcheevos"),
        RetroCredit("Snapdragon GSR", "Upscaling", "BSD-3", "https://github.com/quic/snapdragon-gsr"),
        RetroCredit("Winlator", "Windows-on-Android base", "GPL-3.0", "https://github.com/brunodev85/winlator"),
    )

@Composable
fun RetroHardcoreConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = Color(0xFF1A9FFF)
    com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell(
        onDismiss = onDismiss,
        title = "Enable Hardcore mode?",
    ) {
        Text(
            "Hardcore mode resets the game now and disables loading save states, fast forward, and cheats. Any unsaved progress will be lost. Continue?",
            color = com.winlator.cmod.shared.theme.WinNativeTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(com.winlator.cmod.shared.theme.WinNativeOutline))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton(
                label = "Cancel",
                textColor = com.winlator.cmod.shared.theme.WinNativeTextPrimary,
                onClick = onDismiss,
            )
            com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton(
                label = "Enable",
                textColor = accent,
                backgroundColor = accent.copy(alpha = 0.12f),
                borderColor = accent.copy(alpha = 0.3f),
                onClick = onConfirm,
            )
        }
    }
}
