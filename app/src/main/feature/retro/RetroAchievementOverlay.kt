package com.winlator.cmod.feature.retro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Shared in-game chrome for Retro sessions (libretro + PS2):
 * achievement unlock banners and short status messages.
 *
 * Placement:
 * - Touch controls visible (no physical pad): center of the game display band
 *   so left/right pads do not cover the text.
 * - Touch controls hidden: top-start, classic OSD corner.
 */
object RetroAchievementOverlayState {
    data class Banner(
        val title: String,
        val points: Int,
        val description: String = "",
    )

    var banner by mutableStateOf<Banner?>(null)
        private set
    var sessionMessage by mutableStateOf<String?>(null)
        private set
    var useDisplayArea by mutableStateOf(false)

    fun syncPlacement(
        touchControlsVisible: Boolean,
        controllerConnected: Boolean,
    ) {
        useDisplayArea = touchControlsVisible && !controllerConnected
    }

    fun show(
        title: String,
        points: Int,
        description: String = "",
    ) {
        if (title.contains("Unknown Emulator", ignoreCase = true)) return
        banner = Banner(title, points, description)
    }

    fun showMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        sessionMessage = trimmed
    }

    fun clear() {
        banner = null
    }

    fun clearMessage() {
        sessionMessage = null
    }
}

/** PCSX2 [OsdOverlayPos]: None=0, TopLeft=1, TopCenter=2, … */
object RetroPs2OsdPlacement {
    const val TOP_LEFT = 1
    const val TOP_CENTER = 2

    fun messagePos(touchControlsVisible: Boolean, controllerConnected: Boolean): Int =
        if (touchControlsVisible && !controllerConnected) TOP_CENTER else TOP_LEFT

    fun apply(
        touchControlsVisible: Boolean,
        controllerConnected: Boolean,
    ) {
        val pos = messagePos(touchControlsVisible, controllerConnected)
        runCatching {
            kr.co.iefriends.pcsx2.NativeApp.setSetting("EmuCore/GS", "OsdMessagesPos", "int", pos.toString())
            kr.co.iefriends.pcsx2.NativeApp.commitSettings()
            kr.co.iefriends.pcsx2.NativeApp.applyGSSettingsLive()
        }
    }
}

@Composable
fun BoxScope.RetroAchievementOverlayBanner() {
    val banner = RetroAchievementOverlayState.banner
    val message = RetroAchievementOverlayState.sessionMessage
    val displayArea = RetroAchievementOverlayState.useDisplayArea

    LaunchedEffect(banner) {
        if (banner != null) {
            delay(4500)
            if (RetroAchievementOverlayState.banner == banner) {
                RetroAchievementOverlayState.clear()
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2800)
            if (RetroAchievementOverlayState.sessionMessage == message) {
                RetroAchievementOverlayState.clearMessage()
            }
        }
    }

    val align = if (displayArea) Alignment.TopCenter else Alignment.TopStart
    val hPad = if (displayArea) 80.dp else 12.dp
    val tPad = if (displayArea) 24.dp else 12.dp

    Column(
        modifier =
            Modifier
                .align(align)
                .zIndex(1000f)
                .padding(start = hPad, end = hPad, top = tPad)
                .fillMaxWidth(if (displayArea) 0.62f else 0.55f)
                .widthIn(max = 380.dp),
    ) {
        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            val b = banner
            if (b != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xF0121824), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1A9FFF).copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "ACHIEVEMENT UNLOCKED",
                        color = Color(0xFF1A9FFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        b.title,
                        color = Color(0xFFF0F4FF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (b.description.isNotBlank()) {
                        Text(
                            b.description,
                            color = Color(0xFF93A6BC),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        "+${b.points}",
                        color = Color(0xFF35D0BA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.padding(top = if (banner != null) 8.dp else 0.dp),
        ) {
            val text = message
            if (text != null) {
                Text(
                    text = text,
                    color = Color(0xFFF0F4FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xE0121824), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
