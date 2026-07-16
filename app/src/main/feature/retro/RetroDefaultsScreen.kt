package com.winlator.cmod.feature.retro

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

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
    var consoleIndex by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    val biosPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                RetroBiosImport.importFromUri(context, uri)
                    .onSuccess {
                        Toast.makeText(context, "BIOS imported: $it", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(context, it.message ?: "Invalid BIOS file", Toast.LENGTH_LONG).show()
                    }
                refresh++
            }
        }

    val system = RetroSystems.ALL[consoleIndex]
    val sys = system.id
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
                RetroAchievementsManager.setHardcorePreferred(context, it)
                refresh++
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
        }

        RetroSettingGroup {
            RetroGroupTitle("CONSOLE")
            RetroSettingDropdown(
                label = "Console",
                entries = RetroSystems.ALL.map { it.displayName },
                selectedIndex = consoleIndex,
                onSelected = { consoleIndex = it },
            )
        }

        RetroSettingGroup {
            RetroGroupTitle("${system.shortName.uppercase()} GRAPHICS")
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
            val coreOptions = RetroCoreOptions.forSystem(system)
            coreOptions.forEach { option ->
                val current = RetroDefaults.coreOption(context, sys, option.key, option.defaultValue)
                RetroSettingDropdown(
                    label = option.label,
                    entries = option.valueLabels,
                    selectedIndex = option.values.indexOf(current).coerceAtLeast(0),
                    onSelected = { RetroDefaults.setCoreOption(context, sys, option.key, option.values[it]); refresh++ },
                )
            }
        }

        RetroSettingGroup {
            RetroGroupTitle("${system.shortName.uppercase()} INPUT & AUDIO")
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

        Text(
            "Tip: import your PlayStation BIOS above before launching PS1 games.",
            color = PageSub,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
