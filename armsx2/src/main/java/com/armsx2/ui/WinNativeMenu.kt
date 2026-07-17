package com.armsx2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.runtime.MainActivityRuntime

/**
 * WinNative-styled in-game drawer for the embedded PS2 emulator. Renders in place
 * of ARMSX2's EmulationMenuScreen and re-points every action to ARMSX2's native
 * equivalents (save/load state, patches=cheats, RetroAchievements, memory cards,
 * settings, exit) so the PS2 in-game menu matches the rest of WinNative's retro
 * consoles. Shown only when the host enabled "wn.controls".
 */
object WinNativeMenu {
    val visible = mutableStateOf(false)

    private val Panel = Color(0xFF141B24)
    private val Surface = Color(0xFF1B2430)
    private val TextPrimary = Color(0xFFF0F4FF)
    private val TextSecondary = Color(0xFF93A6BC)
    private val Accent = Color(0xFF1A9FFF)

    fun open() {
        MainActivityRuntime.pauseForOverlay()
        visible.value = true
    }

    private fun close() {
        visible.value = false
    }

    private fun openScreen(screen: InGameScreen) {
        visible.value = false
        WindowImpl.openInGameScreen(screen)
    }

    @Composable
    fun Render() {
        if (!visible.value) return
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(), indication = null) {
                    close(); MainActivityRuntime.resume()
                },
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Panel)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("PLAYSTATION 2", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    MainActivityRuntime.currentGame.value?.title ?: "Game",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Item("▶", "Resume") { close(); MainActivityRuntime.resume() }
                Item("💾", "Save State") { openScreen(InGameScreen.SaveState) }
                Item("📂", "Load State") { openScreen(InGameScreen.LoadState) }
                Item("🏆", "Achievements") { openScreen(InGameScreen.Achievements) }
                Item("⚡", "Cheats") { openScreen(InGameScreen.Patches) }
                Item("🎮", "Controls") { openScreen(InGameScreen.Controls) }
                Item("🗂", "Memory Cards") { openScreen(InGameScreen.Memcard) }
                Item("⚙", "Settings") { openScreen(InGameScreen.Settings) }
                Spacer(Modifier.height(4.dp))
                Item("✕", "Exit Game", danger = true) { close(); MainActivityRuntime.exitApp() }
            }
        }
    }

    @Composable
    private fun Item(
        icon: String,
        label: String,
        danger: Boolean = false,
        onClick: () -> Unit,
    ) {
        Row(
            Modifier
                .background(Surface, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(icon, fontSize = 16.sp)
            Text(
                label,
                color = if (danger) Color(0xFFE06B6B) else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
