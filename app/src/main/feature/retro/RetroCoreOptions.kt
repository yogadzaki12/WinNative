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

    private val DOLPHIN_COMMON_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "dolphin_renderer",
                label = R.string.retro_co_renderer,
                values = listOf("Hardware", "Software"),
                valueLabels = listOf(R.string.retro_co_val_hardware, R.string.retro_co_val_software),
                defaultValue = "Hardware",
            ),
            RetroCoreOption(
                key = "dolphin_efb_scale",
                label = R.string.retro_co_internal_resolution,
                values = listOf("1", "2", "3", "4", "5", "6"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_1x,
                        R.string.retro_co_val_2x,
                        R.string.retro_co_val_3x,
                        R.string.retro_co_val_4x,
                        R.string.retro_co_val_5x,
                        R.string.retro_co_val_6x,
                    ),
                defaultValue = "1",
            ),
        )

    private fun dolphinAspectOption(defaultValue: String) =
        RetroCoreOption(
            key = "dolphin_aspect_ratio",
            label = R.string.retro_co_aspect_ratio,
            values = listOf("0", "1", "2", "3"),
            valueLabels =
                listOf(
                    R.string.retro_co_val_auto,
                    R.string.retro_co_val_16_9,
                    R.string.retro_co_val_4_3,
                    R.string.retro_co_val_stretch,
                ),
            defaultValue = defaultValue,
        )

    private val DOLPHIN_CPU_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "dolphin_cpu_core",
                label = R.string.retro_co_cpu_core,
                values = listOf("4", "5", "0"),
                valueLabels =
                    listOf(
                        R.string.retro_co_val_jit,
                        R.string.retro_co_val_cached_interpreter,
                        R.string.retro_co_val_interpreter,
                    ),
                defaultValue = "4",
            ),
            RetroCoreOption(
                key = "dolphin_main_cpu_thread",
                label = R.string.retro_co_dual_core,
                values = listOf("disabled", "enabled"),
                valueLabels = listOf(R.string.retro_co_val_off, R.string.retro_co_val_on),
                defaultValue = "disabled",
            ),
        )

    private val DOLPHIN_CHEATS_OPTION =
        RetroCoreOption(
            key = "dolphin_cheats_enabled",
            label = R.string.retro_co_internal_cheats,
            values = listOf("disabled", "enabled"),
            valueLabels = listOf(R.string.retro_co_val_off, R.string.retro_co_val_on),
            defaultValue = "disabled",
        )

    private val GAMECUBE_OPTIONS =
        DOLPHIN_COMMON_OPTIONS +
            dolphinAspectOption("2") +
            listOf(
                RetroCoreOption(
                    key = "dolphin_widescreen_hack",
                    label = R.string.retro_co_widescreen_hack,
                    values = listOf("disabled", "enabled"),
                    valueLabels = listOf(R.string.retro_co_val_off, R.string.retro_co_val_on_16_9),
                    defaultValue = "disabled",
                ),
            ) +
            DOLPHIN_CPU_OPTIONS +
            listOf(
                RetroCoreOption(
                    key = "dolphin_skip_gc_bios",
                    label = R.string.retro_co_skip_gc_bios,
                    values = listOf("enabled", "disabled"),
                    valueLabels = listOf(R.string.retro_co_val_on, R.string.retro_co_val_off),
                    defaultValue = "enabled",
                ),
            RetroCoreOption(
                key = "dolphin_vi_skip",
                label = R.string.retro_co_vbi_skip,
                values = listOf("enabled", "disabled"),
                valueLabels = listOf(R.string.retro_co_val_on, R.string.retro_co_val_off),
                defaultValue = "enabled",
            ),
                DOLPHIN_CHEATS_OPTION,
            )

    private val WII_OPTIONS =
        DOLPHIN_COMMON_OPTIONS +
            dolphinAspectOption("1") +
            listOf(
                RetroCoreOption(
                    key = "dolphin_widescreen",
                    label = R.string.retro_co_widescreen,
                    values = listOf("enabled", "disabled"),
                    valueLabels = listOf(R.string.retro_co_val_on, R.string.retro_co_val_off),
                    defaultValue = "enabled",
                ),
            ) +
            DOLPHIN_CPU_OPTIONS +
            listOf(
                RetroCoreOption(
                    key = "dolphin_sensor_bar_position",
                    label = R.string.retro_co_sensor_bar,
                    values = listOf("0", "1"),
                    valueLabels = listOf(R.string.retro_co_val_bottom, R.string.retro_co_val_top),
                    defaultValue = "0",
                ),
                RetroCoreOption(
                    key = "dolphin_ir_mode",
                    label = R.string.retro_co_wiimote_pointer,
                    values = listOf("1", "0", "2"),
                    valueLabels =
                        listOf(
                            R.string.retro_co_val_right_stick_absolute,
                            R.string.retro_co_val_right_stick_relative,
                            R.string.retro_co_val_mouse_pointer,
                        ),
                    defaultValue = "1",
                ),
            RetroCoreOption(
                key = "dolphin_vi_skip",
                label = R.string.retro_co_vbi_skip,
                values = listOf("enabled", "disabled"),
                valueLabels = listOf(R.string.retro_co_val_on, R.string.retro_co_val_off),
                defaultValue = "enabled",
            ),
                DOLPHIN_CHEATS_OPTION,
            )

    /** Normalize stored dolphin values to the ordinals/keys the core reads and force Adreno-safe hacks. */
    fun sanitizeDolphinVariables(vars: MutableMap<String, String>) {
        when (vars["dolphin_cpu_core"]?.trim()) {
            "JITARM64", "JIT", "JIT64" -> vars["dolphin_cpu_core"] = "4"
            "Cached Interpreter", "CachedInterpreter" -> vars["dolphin_cpu_core"] = "5"
            "Interpreter" -> vars["dolphin_cpu_core"] = "0"
        }
        vars["dolphin_efb_scale"]?.let { raw ->
            val t = raw.trim()
            if (t.length == 1 && t[0].isDigit()) return@let
            val digit =
                Regex("""^x?(\d+)""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.getOrNull(1)
            if (digit != null) vars["dolphin_efb_scale"] = digit
        }
        DOLPHIN_PERF_DEFAULTS.forEach { (k, v) ->
            if (vars[k].isNullOrBlank()) vars[k] = v
        }
        when (vars["dolphin_shader_compilation_mode"]) {
            "3", "2" -> vars["dolphin_shader_compilation_mode"] = "0"
        }
        if (vars["dolphin_skip_dupe_frames"] == "enabled") {
            vars["dolphin_skip_dupe_frames"] = "disabled"
        }
        if (vars["dolphin_early_xfb_output"] == "enabled") {
            vars["dolphin_early_xfb_output"] = "disabled"
        }
        if (vars["dolphin_gpu_texture_decoding"] == "enabled") {
            vars["dolphin_gpu_texture_decoding"] = "disabled"
        }
    }

    /** Adreno GLES-safe performance defaults for GameCube/Wii (JITARM64, single-core, sync shaders). */
    private val DOLPHIN_PERF_DEFAULTS: Map<String, String> =
        mapOf(
            "dolphin_renderer" to "Hardware",
            "dolphin_efb_scale" to "1",
            "dolphin_cpu_core" to "4",
            "dolphin_main_cpu_thread" to "disabled",
            "dolphin_fastmem" to "disabled",
            "dolphin_fastmem_arena" to "disabled",
            "dolphin_dsp_hle" to "enabled",
            "dolphin_gpu_texture_decoding" to "disabled",
            "dolphin_efb_to_texture" to "enabled",
            "dolphin_efb_to_vram" to "disabled",
            "dolphin_xfb_to_texture_enable" to "enabled",
            "dolphin_defer_efb_copies" to "disabled",
            "dolphin_immediate_xfb" to "disabled",
            "dolphin_early_xfb_output" to "disabled",
            "dolphin_skip_dupe_frames" to "disabled",
            "dolphin_vi_skip" to "disabled",
            "dolphin_efb_access_enable" to "disabled",
            "dolphin_fast_disc_speed" to "enabled",
            "dolphin_shader_compilation_mode" to "0",
            "dolphin_wait_for_shaders" to "disabled",
            "dolphin_texture_cache_accuracy" to "128",
            "dolphin_anti_aliasing" to "0",
            "dolphin_progressive_scan" to "enabled",
            "dolphin_call_back_audio_method" to "0",
            "dolphin_cheats_enabled" to "disabled",
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
            RetroSystems.GAMECUBE.id ->
                DOLPHIN_PERF_DEFAULTS +
                    mapOf(
                        "dolphin_widescreen" to "disabled",
                        "dolphin_widescreen_hack" to "disabled",
                        "dolphin_aspect_ratio" to "2",
                        "dolphin_skip_gc_bios" to "enabled",
                    )
            RetroSystems.WII.id ->
                DOLPHIN_PERF_DEFAULTS +
                    mapOf(
                        "dolphin_widescreen" to "enabled",
                        "dolphin_aspect_ratio" to "1",
                        "dolphin_sensor_bar_position" to "0",
                        "dolphin_ir_mode" to "1",
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
            RetroSystems.GAMECUBE.id -> GAMECUBE_OPTIONS
            RetroSystems.WII.id -> WII_OPTIONS
            else -> emptyList()
        }
}
