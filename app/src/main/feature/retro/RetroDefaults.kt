package com.winlator.cmod.feature.retro

import android.content.Context
import androidx.preference.PreferenceManager

object RetroDefaults {
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    private fun key(
        setting: String,
        systemId: String,
    ) = "retro_def_${setting}_$systemId"

    private fun varKey(
        systemId: String,
        optionKey: String,
    ) = "retro_def_var_${systemId}_$optionKey"

    fun shader(context: Context, systemId: String): String =
        prefs(context).getString(key("shader", systemId), "default") ?: "default"

    fun setShader(context: Context, systemId: String, value: String) =
        prefs(context).edit().putString(key("shader", systemId), value).apply()

    fun sgsr(context: Context, systemId: String): Boolean =
        prefs(context).getBoolean(key("sgsr", systemId), false)

    fun setSgsr(context: Context, systemId: String, value: Boolean) =
        prefs(context).edit().putBoolean(key("sgsr", systemId), value).apply()

    fun upscale(context: Context, systemId: String): String =
        prefs(context).getString(key("upscale", systemId), "native") ?: "native"

    fun setUpscale(context: Context, systemId: String, value: String) =
        prefs(context).edit().putString(key("upscale", systemId), value).apply()

    fun audio(context: Context, systemId: String): Boolean =
        prefs(context).getBoolean(key("audio", systemId), true)

    fun setAudio(context: Context, systemId: String, value: Boolean) =
        prefs(context).edit().putBoolean(key("audio", systemId), value).apply()

    fun touchControls(context: Context, systemId: String): Boolean =
        prefs(context).getBoolean(key("touch", systemId), true)

    fun setTouchControls(context: Context, systemId: String, value: Boolean) =
        prefs(context).edit().putBoolean(key("touch", systemId), value).apply()

    fun hud(context: Context, systemId: String): Boolean =
        prefs(context).getBoolean(key("hud", systemId), false)

    fun setHud(context: Context, systemId: String, value: Boolean) =
        prefs(context).edit().putBoolean(key("hud", systemId), value).apply()

    fun coreOption(
        context: Context,
        systemId: String,
        optionKey: String,
        fallback: String,
    ): String = prefs(context).getString(varKey(systemId, optionKey), fallback) ?: fallback

    fun setCoreOption(
        context: Context,
        systemId: String,
        optionKey: String,
        value: String,
    ) = prefs(context).edit().putString(varKey(systemId, optionKey), value).apply()
}
