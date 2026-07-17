package com.armsx2.ui.touch

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.iefriends.pcsx2.NativeApp
import kotlin.math.roundToInt

/**
 * WinNative-styled on-screen PS2 pad rendered inside ARMSX2's emulation window.
 * Drives the emulated DualShock 2 through NativeApp.setPadButton — the same native
 * entry point ARMSX2's own overlay uses — so it unifies the touch controls across
 * every console in WinNative without depending on the (unreachable) app module.
 */
private const val FULL = 32767

private val Accent = Color(0xFF1A9FFF)
private val FaceFill = Color(0x33202832)
private val FaceLine = Color(0x55FFFFFF)

private fun Modifier.padButton(keycode: Int): Modifier =
    pointerInput(keycode) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                NativeApp.setPadButton(keycode, FULL, true)
                var up = false
                while (!up) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) up = true
                }
                NativeApp.setPadButton(keycode, 0, false)
            }
        }
    }

@Composable
private fun RoundBtn(
    label: String,
    keycode: Int,
    size: Int,
    color: Color = Color.White,
) {
    Box(
        modifier =
            Modifier
                .size(size.dp)
                .background(FaceFill, CircleShape)
                .border(1.5.dp, FaceLine, CircleShape)
                .padButton(keycode),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(label, color = color, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PillBtn(
    label: String,
    keycode: Int,
    w: Int = 56,
) {
    Box(
        modifier =
            Modifier
                .width(w.dp)
                .height(30.dp)
                .background(FaceFill, RoundedCornerShape(8.dp))
                .border(1.dp, FaceLine, RoundedCornerShape(8.dp))
                .padButton(keycode),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DPad() {
    val arm = 46
    Box(Modifier.size((arm * 3).dp), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.TopCenter).size(arm.dp).background(FaceFill, RoundedCornerShape(8.dp)).border(1.dp, FaceLine, RoundedCornerShape(8.dp)).padButton(KeyEvent.KEYCODE_DPAD_UP), Alignment.Center) {
            androidx.compose.material3.Text("▲", color = Color.White, fontSize = 14.sp)
        }
        Box(Modifier.align(Alignment.BottomCenter).size(arm.dp).background(FaceFill, RoundedCornerShape(8.dp)).border(1.dp, FaceLine, RoundedCornerShape(8.dp)).padButton(KeyEvent.KEYCODE_DPAD_DOWN), Alignment.Center) {
            androidx.compose.material3.Text("▼", color = Color.White, fontSize = 14.sp)
        }
        Box(Modifier.align(Alignment.CenterStart).size(arm.dp).background(FaceFill, RoundedCornerShape(8.dp)).border(1.dp, FaceLine, RoundedCornerShape(8.dp)).padButton(KeyEvent.KEYCODE_DPAD_LEFT), Alignment.Center) {
            androidx.compose.material3.Text("◀", color = Color.White, fontSize = 14.sp)
        }
        Box(Modifier.align(Alignment.CenterEnd).size(arm.dp).background(FaceFill, RoundedCornerShape(8.dp)).border(1.dp, FaceLine, RoundedCornerShape(8.dp)).padButton(KeyEvent.KEYCODE_DPAD_RIGHT), Alignment.Center) {
            androidx.compose.material3.Text("▶", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Stick(
    xPos: Int,
    xNeg: Int,
    yPos: Int,
    yNeg: Int,
) {
    val base = 120f
    var knob by remember { mutableStateOf(Offset.Zero) }
    fun emit(nx: Float, ny: Float) {
        val sx = (kotlin.math.abs(nx) * FULL).roundToInt()
        val sy = (kotlin.math.abs(ny) * FULL).roundToInt()
        NativeApp.setPadButton(xPos, if (nx > 0) sx else 0, nx > 0)
        NativeApp.setPadButton(xNeg, if (nx < 0) sx else 0, nx < 0)
        NativeApp.setPadButton(yPos, if (ny > 0) sy else 0, ny > 0)
        NativeApp.setPadButton(yNeg, if (ny < 0) sy else 0, ny < 0)
    }
    Box(
        modifier =
            Modifier
                .size(base.dp)
                .background(Color(0x22FFFFFF), CircleShape)
                .border(1.dp, FaceLine, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { knob = Offset.Zero; emit(0f, 0f) },
                        onDragCancel = { knob = Offset.Zero; emit(0f, 0f) },
                    ) { change, drag ->
                        change.consume()
                        val r = base * 0.9f
                        var nx = (knob.x + drag.x)
                        var ny = (knob.y + drag.y)
                        val len = kotlin.math.hypot(nx, ny)
                        val max = r
                        if (len > max) {
                            nx = nx / len * max
                            ny = ny / len * max
                        }
                        knob = Offset(nx, ny)
                        emit((nx / max).coerceIn(-1f, 1f), (ny / max).coerceIn(-1f, 1f))
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(52.dp)
                .background(Color(0x55FFFFFF), CircleShape),
        )
    }
}

@Composable
fun WinNativeControls(onMenu: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // Shoulder buttons — top corners
        Row(Modifier.align(Alignment.TopStart).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillBtn("L2", KeyEvent.KEYCODE_BUTTON_L2, 48)
            PillBtn("L1", KeyEvent.KEYCODE_BUTTON_L1, 48)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillBtn("R1", KeyEvent.KEYCODE_BUTTON_R1, 48)
            PillBtn("R2", KeyEvent.KEYCODE_BUTTON_R2, 48)
        }

        // D-pad — lower left
        Box(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 150.dp)) { DPad() }

        // Face buttons — lower right (PS2 layout: △ top, ✕ bottom, □ left, ○ right)
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 150.dp).size(160.dp)) {
            Box(Modifier.align(Alignment.TopCenter)) { RoundBtn("△", KeyEvent.KEYCODE_BUTTON_Y, 52, Color(0xFF4CC79A)) }
            Box(Modifier.align(Alignment.BottomCenter)) { RoundBtn("✕", KeyEvent.KEYCODE_BUTTON_A, 52, Color(0xFF7FA8FF)) }
            Box(Modifier.align(Alignment.CenterStart)) { RoundBtn("□", KeyEvent.KEYCODE_BUTTON_X, 52, Color(0xFFE58AB0)) }
            Box(Modifier.align(Alignment.CenterEnd)) { RoundBtn("○", KeyEvent.KEYCODE_BUTTON_B, 52, Color(0xFFE06B6B)) }
        }

        // Analog sticks — inner-bottom
        Box(Modifier.align(Alignment.BottomStart).padding(start = 200.dp, bottom = 30.dp)) {
            Stick(xPos = 111, xNeg = 113, yPos = 112, yNeg = 110)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 200.dp, bottom = 30.dp)) {
            Stick(xPos = 121, xNeg = 123, yPos = 122, yNeg = 120)
        }

        // Start / Select / Menu — bottom center
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillBtn("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, 74)
            Box(
                Modifier
                    .size(40.dp)
                    .background(Accent.copy(alpha = 0.18f), CircleShape)
                    .border(1.dp, Accent.copy(alpha = 0.5f), CircleShape)
                    .padButtonMenu(onMenu),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text("☰", color = Accent, fontSize = 18.sp)
            }
            PillBtn("START", KeyEvent.KEYCODE_BUTTON_START, 74)
        }
    }
}

private fun Modifier.padButtonMenu(onMenu: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                var up = false
                while (!up) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) up = true
                }
                onMenu()
            }
        }
    }
