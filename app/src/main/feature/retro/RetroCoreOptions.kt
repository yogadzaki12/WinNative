package com.winlator.cmod.feature.retro

import com.winlator.cmod.R

data class RetroCoreOption(
    val key: String,
    @androidx.annotation.StringRes val label: Int,
    val values: List<String>,
    val valueLabels: List<Int>,
    val defaultValue: String,
)

object RetroCoreOptions {
    private val NES_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "fceumm_aspect",
                label = R.string.retro_co_aspect_ratio,
                values = listOf("8:7 PAR", "4:3"),
                valueLabels = listOf(R.string.retro_co_val_8_7_native, R.string.retro_co_val_4_3_tv),
                defaultValue = "8:7 PAR",
            ),
        )

    private val SNES_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "snes9x_blargg",
                label = R.string.retro_co_ntsc_filter,
                values = listOf("disabled", "rf", "composite", "s-video", "rgb"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_off,
                        R.string.retro_co_val_rf,
                        R.string.retro_co_val_composite,
                        R.string.retro_co_val_s_video,
                        R.string.retro_co_val_rgb,
                    ),
                defaultValue = "disabled",
            ),
        )

    private val GB_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "gambatte_gb_colorization",
                label = R.string.retro_co_colorization,
                values = listOf("disabled", "auto"),
                valueLabels = listOf(R.string.retro_co_val_off, R.string.retro_co_val_auto),
                defaultValue = "disabled",
            ),
        )

    private val GBA_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "mgba_color_correction",
                label = R.string.retro_co_color_correction,
                values = listOf("OFF", "GBA", "GBC"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_off,
                        R.string.retro_co_val_gba_screen,
                        R.string.retro_co_val_gbc_screen,
                    ),
                defaultValue = "OFF",
            ),
        )

    private val GENESIS_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "genesis_plus_gx_no_sprite_limit",
                label = R.string.retro_co_remove_sprite_limit,
                values = listOf("disabled", "enabled"),
                valueLabels = listOf(R.string.retro_co_val_off, R.string.retro_co_val_on),
                defaultValue = "disabled",
            ),
            RetroCoreOption(
                key = "genesis_plus_gx_blargg_ntsc_filter",
                label = R.string.retro_co_ntsc_filter,
                values = listOf("Off", "Composite", "S-Video", "RGB"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_off,
                        R.string.retro_co_val_composite,
                        R.string.retro_co_val_s_video,
                        R.string.retro_co_val_rgb,
                    ),
                defaultValue = "Off",
            ),
        )

    private val N64_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "mupen64plus-43screensize",
                label = R.string.retro_co_resolution,
                values = listOf("320x240", "640x480", "960x720", "1280x960"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_320x240_native,
                        R.string.retro_co_val_640x480,
                        R.string.retro_co_val_960x720,
                        R.string.retro_co_val_1280x960,
                    ),
                defaultValue = "640x480",
            ),
            RetroCoreOption(
                key = "mupen64plus-EnableFBEmulation",
                label = R.string.retro_co_framebuffer_emulation,
                values = listOf("True", "False"),
                valueLabels = listOf(R.string.retro_co_val_on, R.string.retro_co_val_off),
                defaultValue = "True",
            ),
            RetroCoreOption(
                key = "mupen64plus-aspect",
                label = R.string.retro_co_aspect_ratio,
                values = listOf("4:3", "16:9", "16:9 adjusted"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_4_3,
                        R.string.retro_co_val_16_9,
                        R.string.retro_co_val_16_9_adjusted,
                    ),
                defaultValue = "4:3",
            ),
        )

    private val PSX_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "beetle_psx_dither_mode",
                label = R.string.retro_co_dithering,
                values = listOf("1x(native)", "internal resolution", "disabled"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_native,
                        R.string.retro_co_val_internal,
                        R.string.retro_co_val_off,
                    ),
                defaultValue = "1x(native)",
            ),
            RetroCoreOption(
                key = "beetle_psx_widescreen_hack",
                label = R.string.retro_co_widescreen_hack,
                values = listOf("disabled", "enabled"),
                valueLabels = listOf(R.string.retro_co_val_off, R.string.retro_co_val_on_16_9),
                defaultValue = "disabled",
            ),
        )

    fun defaultVariables(system: RetroSystem?): Map<String, String> =
        when (system?.id) {
            RetroSystems.N64.id ->
                mapOf(
                    "mupen64plus-43screensize" to "640x480",
                    "mupen64plus-EnableFBEmulation" to "True",
                    "mupen64plus-aspect" to "4:3",
                )
            RetroSystems.PSX.id ->
                mapOf(
                    "beetle_psx_skip_bios" to "enabled",
                )
            else -> emptyMap()
        }

    fun forSystem(system: RetroSystem?): List<RetroCoreOption> =
        when (system?.id) {
            RetroSystems.NES.id -> NES_OPTIONS
            RetroSystems.SNES.id -> SNES_OPTIONS
            RetroSystems.GAMEBOY.id -> GB_OPTIONS
            RetroSystems.GBA.id -> GBA_OPTIONS
            RetroSystems.GENESIS.id, RetroSystems.MASTER_SYSTEM.id, RetroSystems.GAME_GEAR.id -> GENESIS_OPTIONS
            RetroSystems.N64.id -> N64_OPTIONS
            RetroSystems.PSX.id -> PSX_OPTIONS
            else -> emptyList()
        }
}
