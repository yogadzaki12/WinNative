package com.winlator.cmod.feature.retro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.winlator.cmod.runtime.input.controls.GameHubLayout
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class RetroInputView(
    context: Context,
    private val listener: Listener,
    private val system: RetroSystem? = null,
) : View(context) {
    interface Listener {
        fun onButton(
            keyCode: Int,
            down: Boolean,
        )

        fun onDpad(
            x: Float,
            y: Float,
        )

        fun onStick(
            x: Float,
            y: Float,
        )

        fun onRightStick(
            x: Float,
            y: Float,
        )

        fun onMenu()
    }

    private enum class GlassShape { CIRCLE, PILL, TRIGGER_LT, TRIGGER_LB, TRIGGER_RT, TRIGGER_RB }

    private class GlassButton(
        val keyCode: Int,
        val label: String,
        val shape: GlassShape,
        val textScale: Float = 1f,
        val bounds: RectF = RectF(),
    )

    private class CButton(
        val dx: Float,
        val dy: Float,
        val glyph: String,
        val bounds: RectF = RectF(),
    )

    private data class OverlayConfig(
        val hasXY: Boolean,
        val hasShoulders: Boolean,
        val hasTriggers: Boolean,
        val hasStick: Boolean,
        val leftTriggerLabel: String = "L2",
        val rightTriggerLabel: String = "R2",
        val showRightTrigger: Boolean = true,
        val faceTop: String = "X",
        val faceBottom: String = "B",
        val faceLeft: String = "Y",
        val faceRight: String = "A",
    )

    private val config =
        when (system?.id) {
            RetroSystems.SNES.id -> OverlayConfig(hasXY = true, hasShoulders = true, hasTriggers = false, hasStick = false)
            RetroSystems.GBA.id -> OverlayConfig(hasXY = false, hasShoulders = true, hasTriggers = false, hasStick = false)
            RetroSystems.GENESIS.id -> OverlayConfig(hasXY = true, hasShoulders = true, hasTriggers = false, hasStick = false)
            RetroSystems.N64.id ->
                OverlayConfig(
                    hasXY = false,
                    hasShoulders = true,
                    hasTriggers = true,
                    hasStick = true,
                    leftTriggerLabel = "Z",
                    showRightTrigger = false,
                )
            RetroSystems.PSX.id ->
                OverlayConfig(
                    hasXY = true,
                    hasShoulders = true,
                    hasTriggers = true,
                    hasStick = false,
                    faceTop = "△",
                    faceBottom = "✕",
                    faceLeft = "□",
                    faceRight = "○",
                )
            else -> OverlayConfig(hasXY = false, hasShoulders = false, hasTriggers = false, hasStick = false)
        }

    private val buttons = mutableListOf<GlassButton>()
    private val cButtons = mutableListOf<CButton>()
    private val menuButton = GlassButton(0, "MENU", GlassShape.PILL, textScale = 0.75f)
    private var snap = 0f
    private var cStickX = 0f
    private var cStickY = 0f

    private class RetroTheme(
        val body: Int,
        val dpad: Int,
        val pill: Int,
        val pillText: Int,
        val button: Int,
        val buttonText: Int,
        val buttonColors: Map<String, Int> = emptyMap(),
        val buttonTextColors: Map<String, Int> = emptyMap(),
        val backplate: Int = 0,
        val clusterPlate: Int = 0,
        val stickCap: Int = 0xFFB4B4BA.toInt(),
        val cButton: Int = 0xFFE3BE2A.toInt(),
        val cText: Int = 0xFF4A3B08.toInt(),
    )

    private val theme: RetroTheme =
        when (system?.id) {
            RetroSystems.NES.id ->
                RetroTheme(
                    body = 0xFFD8D5CD.toInt(),
                    dpad = 0xFF26262A.toInt(),
                    pill = 0xFF303034.toInt(),
                    pillText = 0xFFD8D5CD.toInt(),
                    button = 0xFFC03A30.toInt(),
                    buttonText = 0xFFF6EAE8.toInt(),
                    backplate = 0xFFEDEBE6.toInt(),
                )
            RetroSystems.SNES.id ->
                RetroTheme(
                    body = 0xFFCBCBD3.toInt(),
                    dpad = 0xFF3E3E46.toInt(),
                    pill = 0xFF8F8F98.toInt(),
                    pillText = 0xFF2F2F36.toInt(),
                    button = 0xFF564A9E.toInt(),
                    buttonText = 0xFFEDEBFA.toInt(),
                    buttonColors =
                        mapOf(
                            "X" to 0xFFBCB6DF.toInt(),
                            "Y" to 0xFFBCB6DF.toInt(),
                        ),
                    buttonTextColors =
                        mapOf(
                            "X" to 0xFF423D66.toInt(),
                            "Y" to 0xFF423D66.toInt(),
                        ),
                    clusterPlate = 0xFFB2B1BB.toInt(),
                )
            RetroSystems.GAMEBOY.id ->
                RetroTheme(
                    body = 0xFFC9C6C1.toInt(),
                    dpad = 0xFF2E2E33.toInt(),
                    pill = 0xFF8A8A86.toInt(),
                    pillText = 0xFF33333A.toInt(),
                    button = 0xFF8E3162.toInt(),
                    buttonText = 0xFFF2DEE8.toInt(),
                )
            RetroSystems.GAMEBOY_COLOR.id ->
                RetroTheme(
                    body = 0xFF5F45AC.toInt(),
                    dpad = 0xFF26262B.toInt(),
                    pill = 0xFF453D78.toInt(),
                    pillText = 0xFFD9D3F2.toInt(),
                    button = 0xFF37305E.toInt(),
                    buttonText = 0xFFCFC8EE.toInt(),
                )
            RetroSystems.GBA.id ->
                RetroTheme(
                    body = 0xFF7C74C6.toInt(),
                    dpad = 0xFF33333A.toInt(),
                    pill = 0xFF9C96CC.toInt(),
                    pillText = 0xFF3C3866.toInt(),
                    button = 0xFFD3CFEA.toInt(),
                    buttonText = 0xFF4A4670.toInt(),
                )
            RetroSystems.N64.id ->
                RetroTheme(
                    body = 0xFFA7A7AD.toInt(),
                    dpad = 0xFF4C4C54.toInt(),
                    pill = 0xFF77777F.toInt(),
                    pillText = 0xFFEFEFF4.toInt(),
                    button = 0xFF77777F.toInt(),
                    buttonText = 0xFFEFEFF4.toInt(),
                    buttonColors =
                        mapOf(
                            "A" to 0xFF2E63C9.toInt(),
                            "B" to 0xFF2F9E44.toInt(),
                            "START" to 0xFFC8362E.toInt(),
                        ),
                    stickCap = 0xFF9EA0A6.toInt(),
                )
            RetroSystems.GENESIS.id, RetroSystems.MASTER_SYSTEM.id, RetroSystems.GAME_GEAR.id ->
                RetroTheme(
                    body = 0xFF1D1D21.toInt(),
                    dpad = 0xFF0E0E11.toInt(),
                    pill = 0xFF2C2C33.toInt(),
                    pillText = 0xFFB9B9C2.toInt(),
                    button = 0xFF303038.toInt(),
                    buttonText = 0xFFB9B9C2.toInt(),
                )
            RetroSystems.PSX.id ->
                RetroTheme(
                    body = 0xFFB7B7C0.toInt(),
                    dpad = 0xFF50505A.toInt(),
                    pill = 0xFF5A5A64.toInt(),
                    pillText = 0xFFE9E9F0.toInt(),
                    button = 0xFF3D3D45.toInt(),
                    buttonText = 0xFFE9E9F0.toInt(),
                    buttonTextColors =
                        mapOf(
                            "△" to 0xFF43B26A.toInt(),
                            "○" to 0xFFD64B45.toInt(),
                            "✕" to 0xFF8FA9DF.toInt(),
                            "□" to 0xFFD387BE.toInt(),
                        ),
                )
            else ->
                RetroTheme(
                    body = 0xFFC9C6C1.toInt(),
                    dpad = 0xFF2E2E33.toInt(),
                    pill = 0xFF8A8A86.toInt(),
                    pillText = 0xFF33333A.toInt(),
                    button = 0xFF55555C.toInt(),
                    buttonText = 0xFFEFEFF4.toInt(),
                )
        }

    private var gameArea: RectF? = null
    var hapticStrength = 0f
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    fun setGameArea(area: RectF?) {
        if (area == gameArea) return
        gameArea = area
        relayout()
    }

    private fun hapticTick() {
        if (hapticStrength <= 0.01f) return
        val amplitude = (hapticStrength * 255f).toInt().coerceIn(1, 255)
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(15, amplitude.toInt())) }
    }

    private fun mix(
        a: Int,
        b: Int,
        f: Float,
    ): Int {
        val inv = 1f - f
        return Color.argb(
            255,
            (Color.red(a) * inv + Color.red(b) * f).toInt(),
            (Color.green(a) * inv + Color.green(b) * f).toInt(),
            (Color.blue(a) * inv + Color.blue(b) * f).toInt(),
        )
    }

    private fun lighten(
        color: Int,
        f: Float,
    ) = mix(color, Color.WHITE, f)

    private fun darken(
        color: Int,
        f: Float,
    ) = mix(color, Color.BLACK, f)

    private var dpadCx = 0f
    private var dpadCy = 0f
    private var dpadRadius = 0f

    private var stickCx = 0f
    private var stickCy = 0f
    private var stickRadius = 0f
    private var stickPointerId = -1
    private var stickX = 0f
    private var stickY = 0f

    private val pressedButtons = HashSet<Int>()
    private var dpadX = 0f
    private var dpadY = 0f
    private var menuLatched = false

    private var strokeWidth = 4f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val arrowCenter = FloatArray(2)


    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    fun relayout() {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        buttons.clear()
        cButtons.clear()
        if (height > width) {
            layoutPortrait(width, height)
        } else {
            layoutLandscape(width, height)
        }
        invalidate()
    }

    private fun layoutLandscape(
        width: Float,
        height: Float,
    ) {
        if (config.hasStick) {
            layoutN64(width, height)
            return
        }
        snap = width / 100f
        val margin = snap * 2.5f
        val bottomGap = snap * 9f
        val faceRadius = snap * 3f
        strokeWidth = max(2f, snap * 0.18f)

        val trigW = snap * 10.4f
        val trigH = snap * 5.2f
        val trigGap = snap * 1.5f

        var leftCursor = margin
        var rightCursor = margin
        if (config.hasTriggers) {
            val lt =
                GlassButton(KeyEvent.KEYCODE_BUTTON_L2, config.leftTriggerLabel, GlassShape.TRIGGER_LT, textScale = 1.3f)
            lt.bounds.set(margin, leftCursor, margin + trigW, leftCursor + trigH)
            buttons += lt
            leftCursor += trigH + trigGap
            if (config.showRightTrigger) {
                val rt =
                    GlassButton(
                        KeyEvent.KEYCODE_BUTTON_R2,
                        config.rightTriggerLabel,
                        GlassShape.TRIGGER_RT,
                        textScale = 1.3f,
                    )
                rt.bounds.set(width - margin - trigW, rightCursor, width - margin, rightCursor + trigH)
                buttons += rt
                rightCursor += trigH + trigGap
            }
        }
        if (config.hasShoulders) {
            val lb = GlassButton(KeyEvent.KEYCODE_BUTTON_L1, "L", GlassShape.TRIGGER_LB, textScale = 1.3f)
            lb.bounds.set(margin, leftCursor, margin + trigW, leftCursor + trigH)
            buttons += lb
            val rb = GlassButton(KeyEvent.KEYCODE_BUTTON_R1, "R", GlassShape.TRIGGER_RB, textScale = 1.3f)
            rb.bounds.set(width - margin - trigW, rightCursor, width - margin, rightCursor + trigH)
            buttons += rb
            leftCursor += trigH + trigGap
        }

        val spread = snap * 5.5f
        var clusterCx = width - margin - faceRadius - spread
        val rightBarWidth = gameArea?.let { width - it.right } ?: 0f
        if (rightBarWidth >= (faceRadius + spread) * 2f + snap * 2f) {
            clusterCx = width - rightBarWidth * 0.5f
        }
        val clusterCy = height - bottomGap - faceRadius - spread
        var clusterTop = height
        fun addFace(
            keyCode: Int,
            label: String,
            cx: Float,
            cy: Float,
        ) {
            val button = GlassButton(keyCode, label, GlassShape.CIRCLE)
            button.bounds.set(cx - faceRadius, cy - faceRadius, cx + faceRadius, cy + faceRadius)
            buttons += button
            clusterTop = min(clusterTop, button.bounds.top)
        }
        if (config.hasXY) {
            addFace(KeyEvent.KEYCODE_BUTTON_X, config.faceTop, clusterCx, clusterCy - spread)
            addFace(KeyEvent.KEYCODE_BUTTON_B, config.faceBottom, clusterCx, clusterCy + spread)
            addFace(KeyEvent.KEYCODE_BUTTON_Y, config.faceLeft, clusterCx - spread, clusterCy)
            addFace(KeyEvent.KEYCODE_BUTTON_A, config.faceRight, clusterCx + spread, clusterCy)
        } else {
            addFace(KeyEvent.KEYCODE_BUTTON_B, "B", clusterCx - faceRadius * 1.1f, clusterCy + spread * 0.5f + faceRadius * 0.5f)
            addFace(KeyEvent.KEYCODE_BUTTON_A, "A", clusterCx + faceRadius * 1.1f, clusterCy + spread * 0.5f - faceRadius * 1.1f)
        }

        val pillW = snap * 6f
        val pillH = snap * 3f
        val pillGap = snap * 1.2f
        val pillY = clusterTop - pillH - snap * 3.5f
        val start = GlassButton(KeyEvent.KEYCODE_BUTTON_START, "START", GlassShape.PILL, textScale = 0.75f)
        start.bounds.set(width - margin - pillW, pillY, width - margin, pillY + pillH)
        buttons += start
        val select = GlassButton(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT", GlassShape.PILL, textScale = 0.75f)
        select.bounds.set(
            width - margin - pillW * 2 - pillGap,
            pillY,
            width - margin - pillW - pillGap,
            pillY + pillH,
        )
        buttons += select

        val menuW = snap * 6f
        stickRadius = 0f
        dpadRadius = snap * 7.5f
        dpadCx = margin + dpadRadius
        val leftBarWidth = gameArea?.left ?: 0f
        if (leftBarWidth >= dpadRadius * 2f + snap * 2f) {
            dpadCx = leftBarWidth * 0.5f
        }
        dpadCy = height - bottomGap - dpadRadius
        val menuY = max(pillY, leftCursor)
        menuButton.bounds.set(dpadCx - menuW * 0.5f, menuY, dpadCx + menuW * 0.5f, menuY + pillH)
    }

    private fun layoutN64(
        width: Float,
        height: Float,
    ) {
        snap = width / 100f
        strokeWidth = max(2f, snap * 0.18f)
        val margin = snap * 2.5f
        val trigW = snap * 10.4f
        val trigH = snap * 5.2f
        val trigGap = snap * 1.5f

        val lb = GlassButton(KeyEvent.KEYCODE_BUTTON_L1, "L", GlassShape.TRIGGER_LB, textScale = 1.3f)
        lb.bounds.set(margin, margin, margin + trigW, margin + trigH)
        buttons += lb
        val z =
            GlassButton(KeyEvent.KEYCODE_BUTTON_L2, config.leftTriggerLabel, GlassShape.TRIGGER_RT, textScale = 1.3f)
        z.bounds.set(width - margin - trigW, margin, width - margin, margin + trigH)
        buttons += z
        val rb = GlassButton(KeyEvent.KEYCODE_BUTTON_R1, "R", GlassShape.TRIGGER_RB, textScale = 1.3f)
        rb.bounds.set(
            width - margin - trigW,
            margin + trigH + trigGap,
            width - margin,
            margin + trigH * 2 + trigGap,
        )
        buttons += rb

        val faceRadius = snap * 3f
        val spread = snap * 5.5f
        var clusterCx = width - margin - faceRadius - spread
        val rightBarWidth = gameArea?.let { width - it.right } ?: 0f
        if (rightBarWidth >= (faceRadius + spread) * 2f + snap * 2f) {
            clusterCx = width - rightBarWidth * 0.5f
        }
        val clusterCy = height - snap * 4.5f - faceRadius - spread
        val bCx = clusterCx - faceRadius * 0.9f
        val bCy = clusterCy + spread * 0.5f - faceRadius * 0.9f
        val bButton = GlassButton(KeyEvent.KEYCODE_BUTTON_Y, "B", GlassShape.CIRCLE)
        bButton.bounds.set(bCx - faceRadius, bCy - faceRadius, bCx + faceRadius, bCy + faceRadius)
        buttons += bButton
        val aCx = clusterCx + faceRadius * 0.9f
        val aCy = clusterCy + spread * 0.5f + faceRadius * 0.9f
        val aButton = GlassButton(KeyEvent.KEYCODE_BUTTON_B, "A", GlassShape.CIRCLE)
        aButton.bounds.set(aCx - faceRadius, aCy - faceRadius, aCx + faceRadius, aCy + faceRadius)
        buttons += aButton

        val pillW = snap * 6f
        val pillH = snap * 3f
        val pillGap = snap * 1.2f
        val pillY = height - snap * 2.5f - pillH
        var pillX = (width - pillW * 3f - pillGap * 2f) * 0.5f
        menuButton.bounds.set(pillX, pillY, pillX + pillW, pillY + pillH)
        pillX += pillW + pillGap
        val select = GlassButton(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT", GlassShape.PILL, textScale = 0.75f)
        select.bounds.set(pillX, pillY, pillX + pillW, pillY + pillH)
        buttons += select
        pillX += pillW + pillGap
        val start = GlassButton(KeyEvent.KEYCODE_BUTTON_START, "START", GlassShape.PILL, textScale = 0.75f)
        start.bounds.set(pillX, pillY, pillX + pillW, pillY + pillH)
        buttons += start

        stickRadius = snap * 7f
        stickCx = margin + stickRadius + snap * 1f
        val leftBarWidth = gameArea?.left ?: 0f
        if (leftBarWidth >= stickRadius * 2f + snap * 2f) {
            stickCx = leftBarWidth * 0.5f
        }
        stickCy = height - snap * 5.5f - stickRadius
        dpadRadius = snap * 6.5f
        dpadCx = stickCx
        dpadCy = stickCy - stickRadius - snap * 2f - dpadRadius

        val cRadius = snap * 2.4f
        val cSpread = snap * 3.6f
        val cCx = clusterCx + snap * 2f
        val topOfFaces = bCy - faceRadius
        val bottomOfTriggers = margin + trigH * 2 + trigGap
        val cCy =
            min(
                (bottomOfTriggers + topOfFaces) * 0.5f + snap * 2f,
                topOfFaces - snap * 1.5f - cSpread - cRadius,
            )
        fun addC(
            dx: Float,
            dy: Float,
            glyph: String,
            x: Float,
            y: Float,
        ) {
            val c = CButton(dx, dy, glyph)
            c.bounds.set(x - cRadius, y - cRadius, x + cRadius, y + cRadius)
            cButtons += c
        }
        addC(0f, -1f, "▲", cCx, cCy - cSpread)
        addC(0f, 1f, "▼", cCx, cCy + cSpread)
        addC(-1f, 0f, "◀", cCx - cSpread, cCy)
        addC(1f, 0f, "▶", cCx + cSpread, cCy)
    }

    private val portraitGameAspect: Float
        get() =
            when (system?.id) {
                RetroSystems.GAMEBOY.id, RetroSystems.GAMEBOY_COLOR.id -> 0.9f
                RetroSystems.GBA.id -> 2f / 3f
                else -> 0.75f
            }

    private fun portraitZoneTop(
        width: Float,
        height: Float,
    ): Float = max(width * portraitGameAspect + snap * 8f, height * 0.42f)

    private fun layoutPortrait(
        width: Float,
        height: Float,
    ) {
        if (config.hasStick) {
            layoutPortraitN64(width, height)
            return
        }
        snap = width / 100f
        strokeWidth = max(2f, snap * 0.4f)
        stickRadius = 0f
        val zoneTop = portraitZoneTop(width, height)
        val zoneH = height - zoneTop
        val margin = snap * 5f
        val trigW = snap * 24f
        val trigH = snap * 8f
        val trigGap = snap * 1.5f

        var sideCursor = zoneTop + snap * 2f
        if (config.hasTriggers) {
            val lt = GlassButton(KeyEvent.KEYCODE_BUTTON_L2, config.leftTriggerLabel, GlassShape.TRIGGER_LT, textScale = 1.3f)
            lt.bounds.set(margin, sideCursor, margin + trigW, sideCursor + trigH)
            buttons += lt
            if (config.showRightTrigger) {
                val rt =
                    GlassButton(
                        KeyEvent.KEYCODE_BUTTON_R2,
                        config.rightTriggerLabel,
                        GlassShape.TRIGGER_RT,
                        textScale = 1.3f,
                    )
                rt.bounds.set(width - margin - trigW, sideCursor, width - margin, sideCursor + trigH)
                buttons += rt
            }
            sideCursor += trigH + trigGap
        }
        if (config.hasShoulders) {
            val lb = GlassButton(KeyEvent.KEYCODE_BUTTON_L1, "L", GlassShape.TRIGGER_LB, textScale = 1.3f)
            lb.bounds.set(margin, sideCursor, margin + trigW, sideCursor + trigH)
            buttons += lb
            val rb = GlassButton(KeyEvent.KEYCODE_BUTTON_R1, "R", GlassShape.TRIGGER_RB, textScale = 1.3f)
            rb.bounds.set(width - margin - trigW, sideCursor, width - margin, sideCursor + trigH)
            buttons += rb
            sideCursor += trigH + trigGap
        }

        val rowCy = max(zoneTop + zoneH * 0.45f, sideCursor + snap * 18f)
        dpadRadius = snap * 15f
        dpadCx = margin + dpadRadius
        dpadCy = rowCy

        fun addFace(
            keyCode: Int,
            label: String,
            cx: Float,
            cy: Float,
            radius: Float,
        ) {
            val button = GlassButton(keyCode, label, GlassShape.CIRCLE)
            button.bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            buttons += button
        }
        if (config.hasXY) {
            val faceRadius = snap * 6.5f
            val spread = snap * 12f
            val clusterCx = width - margin - faceRadius - spread
            addFace(KeyEvent.KEYCODE_BUTTON_X, config.faceTop, clusterCx, rowCy - spread, faceRadius)
            addFace(KeyEvent.KEYCODE_BUTTON_B, config.faceBottom, clusterCx, rowCy + spread, faceRadius)
            addFace(KeyEvent.KEYCODE_BUTTON_Y, config.faceLeft, clusterCx - spread, rowCy, faceRadius)
            addFace(KeyEvent.KEYCODE_BUTTON_A, config.faceRight, clusterCx + spread, rowCy, faceRadius)
        } else {
            val faceRadius = snap * 8.5f
            addFace(KeyEvent.KEYCODE_BUTTON_A, "A", width - margin - faceRadius, rowCy - faceRadius * 0.9f, faceRadius)
            addFace(
                KeyEvent.KEYCODE_BUTTON_B,
                "B",
                width - margin - faceRadius * 3f - snap * 2f,
                rowCy + faceRadius * 0.9f,
                faceRadius,
            )
        }

        val pillW = snap * 13f
        val pillH = snap * 5.5f
        val pillGap = snap * 2f
        val pillY = height - margin - pillH
        val select = GlassButton(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT", GlassShape.PILL, textScale = 0.75f)
        select.bounds.set(width * 0.5f - pillGap * 0.5f - pillW, pillY, width * 0.5f - pillGap * 0.5f, pillY + pillH)
        buttons += select
        val start = GlassButton(KeyEvent.KEYCODE_BUTTON_START, "START", GlassShape.PILL, textScale = 0.75f)
        start.bounds.set(width * 0.5f + pillGap * 0.5f, pillY, width * 0.5f + pillGap * 0.5f + pillW, pillY + pillH)
        buttons += start

        val menuW = snap * 13f
        menuButton.bounds.set(
            width * 0.5f - menuW * 0.5f,
            zoneTop + snap * 1.5f,
            width * 0.5f + menuW * 0.5f,
            zoneTop + snap * 1.5f + pillH,
        )
    }

    private fun layoutPortraitN64(
        width: Float,
        height: Float,
    ) {
        snap = width / 100f
        strokeWidth = max(2f, snap * 0.4f)
        val zoneTop = portraitZoneTop(width, height)
        val margin = snap * 5f
        val trigW = snap * 24f
        val trigH = snap * 8f
        val trigGap = snap * 1.5f

        val lb = GlassButton(KeyEvent.KEYCODE_BUTTON_L1, "L", GlassShape.TRIGGER_LB, textScale = 1.3f)
        lb.bounds.set(margin, zoneTop + snap * 2f, margin + trigW, zoneTop + snap * 2f + trigH)
        buttons += lb
        val z = GlassButton(KeyEvent.KEYCODE_BUTTON_L2, config.leftTriggerLabel, GlassShape.TRIGGER_RT, textScale = 1.3f)
        z.bounds.set(width - margin - trigW, zoneTop + snap * 2f, width - margin, zoneTop + snap * 2f + trigH)
        buttons += z
        val rb = GlassButton(KeyEvent.KEYCODE_BUTTON_R1, "R", GlassShape.TRIGGER_RB, textScale = 1.3f)
        rb.bounds.set(
            width - margin - trigW,
            zoneTop + snap * 2f + trigH + trigGap,
            width - margin,
            zoneTop + snap * 2f + trigH * 2 + trigGap,
        )
        buttons += rb

        val pillW = snap * 13f
        val pillH = snap * 5.5f
        val pillGap = snap * 2f
        val pillY = height - margin - pillH
        val select = GlassButton(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT", GlassShape.PILL, textScale = 0.75f)
        select.bounds.set(width * 0.5f - pillGap * 0.5f - pillW, pillY, width * 0.5f - pillGap * 0.5f, pillY + pillH)
        buttons += select
        val start = GlassButton(KeyEvent.KEYCODE_BUTTON_START, "START", GlassShape.PILL, textScale = 0.75f)
        start.bounds.set(width * 0.5f + pillGap * 0.5f, pillY, width * 0.5f + pillGap * 0.5f + pillW, pillY + pillH)
        buttons += start

        val menuW = snap * 13f
        menuButton.bounds.set(
            width * 0.5f - menuW * 0.5f,
            zoneTop + snap * 1.5f,
            width * 0.5f + menuW * 0.5f,
            zoneTop + snap * 1.5f + pillH,
        )

        stickRadius = snap * 12f
        stickCx = margin + stickRadius
        stickCy = pillY - snap * 2f - stickRadius
        dpadRadius = snap * 10f
        dpadCx = stickCx
        dpadCy = stickCy - stickRadius - snap * 2f - dpadRadius

        val faceRadius = snap * 7.5f
        val clusterCx = width - margin - faceRadius - snap * 9f
        val aCy = stickCy
        val aCx = clusterCx + faceRadius * 0.9f
        val bCx = clusterCx - faceRadius * 0.9f
        val bCy = aCy - faceRadius * 1.8f
        val bButton = GlassButton(KeyEvent.KEYCODE_BUTTON_Y, "B", GlassShape.CIRCLE)
        bButton.bounds.set(bCx - faceRadius, bCy - faceRadius, bCx + faceRadius, bCy + faceRadius)
        buttons += bButton
        val aButton = GlassButton(KeyEvent.KEYCODE_BUTTON_B, "A", GlassShape.CIRCLE)
        aButton.bounds.set(aCx - faceRadius, aCy - faceRadius, aCx + faceRadius, aCy + faceRadius)
        buttons += aButton

        val cRadius = snap * 4.5f
        val cSpread = snap * 6.5f
        val cCx = clusterCx + snap * 1f
        val shouldersBottom = zoneTop + snap * 2f + trigH * 2 + trigGap
        val facesTop = bCy - faceRadius
        val cCy = (shouldersBottom + facesTop) * 0.5f
        fun addC(
            dx: Float,
            dy: Float,
            glyph: String,
            x: Float,
            y: Float,
        ) {
            val c = CButton(dx, dy, glyph)
            c.bounds.set(x - cRadius, y - cRadius, x + cRadius, y + cRadius)
            cButtons += c
        }
        addC(0f, -1f, "▲", cCx, cCy - cSpread)
        addC(0f, 1f, "▼", cCx, cCy + cSpread)
        addC(-1f, 0f, "◀", cCx - cSpread, cCy)
        addC(1f, 0f, "▶", cCx + cSpread, cCy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        drawShellBackground(canvas)
        drawClusterPlate(canvas)
        drawDpad(canvas)
        if (config.hasStick) drawStick(canvas)
        buttons.forEach { drawThemedButton(canvas, it, pressedButtons.contains(it.keyCode)) }
        cButtons.forEach { drawCButton(canvas, it) }
        drawThemedButton(canvas, menuButton, menuLatched)
    }

    private fun drawShellBackground(canvas: Canvas) {
        val area = gameArea ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = theme.body
        if (area.top > 0f) canvas.drawRect(0f, 0f, w, area.top, paint)
        if (area.bottom < h) canvas.drawRect(0f, area.bottom, w, h, paint)
        if (area.left > 0f) canvas.drawRect(0f, area.top, area.left, area.bottom, paint)
        if (area.right < w) canvas.drawRect(area.right, area.top, w, area.bottom, paint)
    }

    private fun drawClusterPlate(canvas: Canvas) {
        if (theme.clusterPlate == 0 || !config.hasXY) return
        val faces = buttons.filter { it.shape == GlassShape.CIRCLE }
        if (faces.size < 4) return
        val cx = faces.map { it.bounds.centerX() }.average().toFloat()
        val cy = faces.map { it.bounds.centerY() }.average().toFloat()
        val reach =
            faces.maxOf {
                hypot(it.bounds.centerX() - cx, it.bounds.centerY() - cy) + it.bounds.width() * 0.5f
            }
        val radius = reach + snap * 1.4f
        paint.shader =
            RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(theme.clusterPlate, theme.clusterPlate, darken(theme.clusterPlate, 0.12f)),
                floatArrayOf(0f, 0.8f, 1f),
                Shader.TileMode.CLAMP,
            )
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = darken(theme.clusterPlate, 0.22f)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun buttonFill(button: GlassButton): Int =
        theme.buttonColors[button.label]
            ?: if (button.shape == GlassShape.CIRCLE) theme.button else theme.pill

    private fun buttonTextColor(button: GlassButton): Int =
        theme.buttonTextColors[button.label]
            ?: if (button.shape == GlassShape.CIRCLE) theme.buttonText else theme.pillText

    private fun drawThemedButton(
        canvas: Canvas,
        button: GlassButton,
        pressed: Boolean,
    ) {
        val b = button.bounds
        val color = buttonFill(button)
        if (button.shape == GlassShape.CIRCLE) {
            drawRoundButton(
                canvas,
                b.centerX(),
                b.centerY(),
                b.width() * 0.5f,
                color,
                buttonTextColor(button),
                button.label,
                pressed,
                if (button.label.length > 1) 0.62f else 0.92f,
            )
            return
        }
        val depth = b.height() * 0.08f
        val dy = if (pressed) depth * 0.7f else 0f
        buildShapePath(button)
        path.offset(0f, depth)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (pressed) 40 else 70, 0, 0, 0)
        canvas.drawPath(path, paint)
        buildShapePath(button)
        path.offset(0f, dy)
        paint.shader =
            LinearGradient(
                b.left,
                b.top + dy,
                b.left,
                b.bottom + dy,
                intArrayOf(
                    lighten(color, if (pressed) 0.06f else 0.24f),
                    color,
                    darken(color, if (pressed) 0.24f else 0.14f),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = darken(color, 0.38f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = buttonTextColor(button)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = b.height() * (if (button.shape == GlassShape.PILL) 0.44f else 0.5f) * button.textScale / 0.75f * 0.75f
        val maxTextWidth = b.width() - b.height() * 0.5f
        if (button.label.isNotEmpty() && paint.measureText(button.label) > maxTextWidth) {
            paint.textSize = paint.textSize * maxTextWidth / paint.measureText(button.label)
        }
        val textY = b.centerY() + dy - (paint.descent() + paint.ascent()) * 0.5f
        canvas.drawText(button.label, b.centerX(), textY, paint)
        paint.isFakeBoldText = false
    }

    private fun drawRoundButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        textColor: Int,
        label: String,
        pressed: Boolean,
        textScale: Float,
    ) {
        val depth = radius * 0.12f
        val dy = if (pressed) depth * 0.7f else 0f
        val bodyCy = cy + dy

        if (theme.backplate != 0) {
            val pr = radius * 1.3f
            val corner = radius * 0.3f
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = theme.backplate
            canvas.drawRoundRect(cx - pr, cy - pr, cx + pr, cy + pr, corner, corner, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.color = darken(theme.backplate, 0.16f)
            canvas.drawRoundRect(cx - pr, cy - pr, cx + pr, cy + pr, corner, corner, paint)
        }

        paint.style = Paint.Style.FILL
        paint.shader =
            RadialGradient(
                cx,
                cy + depth,
                radius * 1.18f,
                Color.argb(if (pressed) 55 else 95, 0, 0, 0),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(cx, cy + depth, radius * 1.18f, paint)

        paint.shader =
            RadialGradient(
                cx - radius * 0.35f,
                bodyCy - radius * 0.45f,
                radius * 1.9f,
                intArrayOf(
                    lighten(color, if (pressed) 0.08f else 0.3f),
                    color,
                    darken(color, if (pressed) 0.28f else 0.16f),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(cx, bodyCy, radius, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = darken(color, 0.4f)
        canvas.drawCircle(cx, bodyCy, radius - strokeWidth * 0.5f, paint)

        if (!pressed) {
            paint.style = Paint.Style.FILL
            paint.shader =
                RadialGradient(
                    cx - radius * 0.34f,
                    bodyCy - radius * 0.4f,
                    radius * 0.62f,
                    Color.argb(70, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawCircle(cx - radius * 0.34f, bodyCy - radius * 0.4f, radius * 0.62f, paint)
            paint.shader = null
        }

        if (label.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            paint.color = textColor
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.textSize = radius * textScale * 1.05f
            val maxTextWidth = radius * 1.7f
            if (paint.measureText(label) > maxTextWidth) {
                paint.textSize = paint.textSize * maxTextWidth / paint.measureText(label)
            }
            val textY = bodyCy - (paint.descent() + paint.ascent()) * 0.5f
            canvas.drawText(label, cx, textY, paint)
            paint.isFakeBoldText = false
        }
    }

    private fun drawCButton(
        canvas: Canvas,
        button: CButton,
    ) {
        val b = button.bounds
        val pressed =
            (button.dx != 0f && cStickX == button.dx) || (button.dy != 0f && cStickY == button.dy)
        drawRoundButton(
            canvas,
            b.centerX(),
            b.centerY(),
            b.width() * 0.5f,
            theme.cButton,
            theme.cText,
            button.glyph,
            pressed,
            0.8f,
        )
    }

    private fun buildShapePath(button: GlassButton) {
        val b = button.bounds
        when (button.shape) {
            GlassShape.CIRCLE -> {
                path.reset()
                path.addCircle(b.centerX(), b.centerY(), b.width() * 0.5f, Path.Direction.CW)
            }
            GlassShape.PILL -> {
                path.reset()
                val r = b.height() * 0.5f
                path.addRoundRect(b.left, b.top, b.right, b.bottom, r, r, Path.Direction.CW)
            }
            GlassShape.TRIGGER_LT ->
                GameHubLayout.buildTriggerPath(path, GameHubLayout.RenderShape.TRIGGER_LT, b.left, b.top, b.right, b.bottom)
            GlassShape.TRIGGER_LB ->
                GameHubLayout.buildTriggerPath(path, GameHubLayout.RenderShape.TRIGGER_LB, b.left, b.top, b.right, b.bottom)
            GlassShape.TRIGGER_RT ->
                GameHubLayout.buildTriggerPath(path, GameHubLayout.RenderShape.TRIGGER_RT, b.left, b.top, b.right, b.bottom)
            GlassShape.TRIGGER_RB ->
                GameHubLayout.buildTriggerPath(path, GameHubLayout.RenderShape.TRIGGER_RB, b.left, b.top, b.right, b.bottom)
        }
    }

    private fun drawDpad(canvas: Canvas) {
        val color = theme.dpad
        val arm = dpadRadius * 0.36f
        val corner = arm * 0.5f
        val cx = dpadCx
        val cy = dpadCy
        val r = dpadRadius
        val depth = r * 0.05f
        val pressedUp = dpadY < -0.1f
        val pressedDown = dpadY > 0.1f
        val pressedLeft = dpadX < -0.1f
        val pressedRight = dpadX > 0.1f

        path.reset()
        path.addRoundRect(cx - r, cy - arm + depth, cx + r, cy + arm + depth, corner, corner, Path.Direction.CW)
        path.addRoundRect(cx - arm, cy - r + depth, cx + arm, cy + r + depth, corner, corner, Path.Direction.CW)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(80, 0, 0, 0)
        canvas.drawPath(path, paint)

        path.reset()
        path.addRoundRect(cx - r, cy - arm, cx + r, cy + arm, corner, corner, Path.Direction.CW)
        path.addRoundRect(cx - arm, cy - r, cx + arm, cy + r, corner, corner, Path.Direction.CW)
        paint.shader =
            LinearGradient(
                cx,
                cy - r,
                cx,
                cy + r,
                intArrayOf(lighten(color, 0.2f), color, darken(color, 0.2f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, paint)
        paint.shader = null

        fun pressArm(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(90, 0, 0, 0)
            canvas.drawRoundRect(left, top, right, bottom, corner, corner, paint)
        }
        if (pressedUp) pressArm(cx - arm, cy - r, cx + arm, cy - arm * 0.4f)
        if (pressedDown) pressArm(cx - arm, cy + arm * 0.4f, cx + arm, cy + r)
        if (pressedLeft) pressArm(cx - r, cy - arm, cx - arm * 0.4f, cy + arm)
        if (pressedRight) pressArm(cx + arm * 0.4f, cy - arm, cx + r, cy + arm)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = darken(color, 0.45f)
        path.reset()
        path.addRoundRect(cx - r, cy - arm, cx + r, cy + arm, corner, corner, Path.Direction.CW)
        val vertical = Path()
        vertical.addRoundRect(cx - arm, cy - r, cx + arm, cy + r, corner, corner, Path.Direction.CW)
        path.op(vertical, Path.Op.UNION)
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        paint.shader =
            RadialGradient(
                cx,
                cy,
                arm * 0.85f,
                darken(color, 0.3f),
                color,
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(cx, cy, arm * 0.85f, paint)
        paint.shader = null

        val tip = arm * 0.42f
        fun arrow(
            tx: Float,
            ty: Float,
            dxu: Float,
            dyu: Float,
            engaged: Boolean,
        ) {
            path.reset()
            path.moveTo(tx + dxu * tip, ty + dyu * tip)
            path.lineTo(tx - dyu * tip * 0.8f - dxu * tip * 0.5f, ty + dxu * tip * 0.8f - dyu * tip * 0.5f)
            path.lineTo(tx + dyu * tip * 0.8f - dxu * tip * 0.5f, ty - dxu * tip * 0.8f - dyu * tip * 0.5f)
            path.close()
            paint.color = if (engaged) lighten(color, 0.5f) else lighten(color, 0.16f)
            canvas.drawPath(path, paint)
        }
        val inset = arm * 0.62f
        arrow(cx, cy - r + inset, 0f, -1f, pressedUp)
        arrow(cx, cy + r - inset, 0f, 1f, pressedDown)
        arrow(cx - r + inset, cy, -1f, 0f, pressedLeft)
        arrow(cx + r - inset, cy, 1f, 0f, pressedRight)
    }

    private fun drawStick(canvas: Canvas) {
        val engaged = stickPointerId != -1
        val wellColor = darken(theme.dpad, 0.1f)
        paint.shader = null
        paint.style = Paint.Style.FILL

        path.reset()
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45 - 22.5).toDouble())
            val px = stickCx + stickRadius * Math.cos(angle).toFloat()
            val py = stickCy + stickRadius * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        paint.shader =
            RadialGradient(
                stickCx,
                stickCy,
                stickRadius,
                intArrayOf(darken(wellColor, 0.35f), wellColor, lighten(wellColor, 0.08f)),
                floatArrayOf(0f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = darken(wellColor, 0.4f)
        canvas.drawPath(path, paint)

        val thumbX = stickCx + stickX * stickRadius * 0.5f
        val thumbY = stickCy + stickY * stickRadius * 0.5f
        val thumbRadius = stickRadius * 0.5f
        val capColor = theme.stickCap
        paint.style = Paint.Style.FILL
        paint.shader =
            RadialGradient(
                thumbX,
                thumbY + thumbRadius * 0.18f,
                thumbRadius * 1.25f,
                Color.argb(90, 0, 0, 0),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(thumbX, thumbY + thumbRadius * 0.18f, thumbRadius * 1.25f, paint)
        paint.shader =
            RadialGradient(
                thumbX - thumbRadius * 0.3f,
                thumbY - thumbRadius * 0.35f,
                thumbRadius * 1.8f,
                intArrayOf(
                    lighten(capColor, if (engaged) 0.1f else 0.3f),
                    capColor,
                    darken(capColor, 0.22f),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(thumbX, thumbY, thumbRadius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = darken(capColor, 0.35f)
        canvas.drawCircle(thumbX, thumbY, thumbRadius - strokeWidth * 0.5f, paint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius * 0.55f, paint)
    }

    fun releaseAll() {
        for (keyCode in pressedButtons) listener.onButton(keyCode, false)
        pressedButtons.clear()
        if (dpadX != 0f || dpadY != 0f) {
            dpadX = 0f
            dpadY = 0f
            listener.onDpad(0f, 0f)
        }
        if (stickPointerId != -1 || stickX != 0f || stickY != 0f) {
            stickPointerId = -1
            stickX = 0f
            stickY = 0f
            listener.onStick(0f, 0f)
        }
        if (cStickX != 0f || cStickY != 0f) {
            cStickX = 0f
            cStickY = 0f
            listener.onRightStick(0f, 0f)
        }
        menuLatched = false
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> recompute(event)
            else -> return false
        }
        invalidate()
        return true
    }

    private fun hitButton(
        button: GlassButton,
        x: Float,
        y: Float,
    ): Boolean {
        val b = button.bounds
        val inflate = snap * 1.2f
        return if (button.shape == GlassShape.CIRCLE) {
            val r = b.width() * 0.5f + inflate
            hypot(x - b.centerX(), y - b.centerY()) <= r
        } else {
            x >= b.left - inflate && x <= b.right + inflate &&
                y >= b.top - inflate && y <= b.bottom + inflate
        }
    }

    private fun recompute(event: MotionEvent) {
        val released =
            event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        val liftedPointer =
            if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1

        val newPressed = HashSet<Int>()
        var newDpadX = 0f
        var newDpadY = 0f
        var newCX = 0f
        var newCY = 0f
        var menuTouched = false
        var stickSeen = false
        var newStickX = stickX
        var newStickY = stickY

        if (!released) {
            for (i in 0 until event.pointerCount) {
                if (i == liftedPointer) continue
                val x = event.getX(i)
                val y = event.getY(i)
                val pointerId = event.getPointerId(i)

                if (config.hasStick) {
                    if (pointerId == stickPointerId) {
                        stickSeen = true
                        newStickX = ((x - stickCx) / stickRadius).coerceIn(-1f, 1f)
                        newStickY = ((y - stickCy) / stickRadius).coerceIn(-1f, 1f)
                        continue
                    }
                    if (stickPointerId == -1 &&
                        hypot(x - stickCx, y - stickCy) <= stickRadius * 1.3f
                    ) {
                        stickPointerId = pointerId
                        stickSeen = true
                        hapticTick()
                        newStickX = ((x - stickCx) / stickRadius).coerceIn(-1f, 1f)
                        newStickY = ((y - stickCy) / stickRadius).coerceIn(-1f, 1f)
                        continue
                    }
                }

                if (hitButton(menuButton, x, y)) {
                    menuTouched = true
                    continue
                }

                var cHit = false
                for (c in cButtons) {
                    val reach = c.bounds.width() * 0.5f + snap * 1.2f
                    if (hypot(x - c.bounds.centerX(), y - c.bounds.centerY()) <= reach) {
                        if (c.dx != 0f) newCX = c.dx
                        if (c.dy != 0f) newCY = c.dy
                        cHit = true
                        break
                    }
                }
                if (cHit) continue

                var buttonHit = false
                for (button in buttons) {
                    if (hitButton(button, x, y)) {
                        newPressed.add(button.keyCode)
                        buttonHit = true
                        break
                    }
                }
                if (buttonHit) continue

                val dxToPad = x - dpadCx
                val dyToPad = y - dpadCy
                if (hypot(dxToPad, dyToPad) <= dpadRadius * 1.4f) {
                    val dz = dpadRadius * 0.24f
                    if (dxToPad > dz) newDpadX = 1f else if (dxToPad < -dz) newDpadX = -1f
                    if (dyToPad > dz) newDpadY = 1f else if (dyToPad < -dz) newDpadY = -1f
                }
            }
        }

        if (newCX != cStickX || newCY != cStickY) {
            if (newCX != 0f || newCY != 0f) hapticTick()
            cStickX = newCX
            cStickY = newCY
            listener.onRightStick(cStickX, cStickY)
        }

        if (!stickSeen && stickPointerId != -1) {
            stickPointerId = -1
            newStickX = 0f
            newStickY = 0f
        }
        if (newStickX != stickX || newStickY != stickY) {
            stickX = newStickX
            stickY = newStickY
            listener.onStick(stickX, stickY)
        }

        for (keyCode in pressedButtons) {
            if (!newPressed.contains(keyCode)) listener.onButton(keyCode, false)
        }
        for (keyCode in newPressed) {
            if (!pressedButtons.contains(keyCode)) {
                hapticTick()
                listener.onButton(keyCode, true)
            }
        }
        pressedButtons.clear()
        pressedButtons.addAll(newPressed)

        if (newDpadX != dpadX || newDpadY != dpadY) {
            if (newDpadX != 0f || newDpadY != 0f) hapticTick()
            dpadX = newDpadX
            dpadY = newDpadY
            listener.onDpad(dpadX, dpadY)
        }

        if (menuTouched && !menuLatched) {
            menuLatched = true
            hapticTick()
            listener.onMenu()
        } else if (!menuTouched) {
            menuLatched = false
        }
    }
}
