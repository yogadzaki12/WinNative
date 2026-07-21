package com.winlator.cmod.feature.retro

import android.content.Context
import kr.co.iefriends.pcsx2.NativeApp

object Ps2RaBridge {
    fun pushSharedLogin(context: Context): Boolean {
        if (!RetroAchievementsManager.isLoggedIn(context)) return false
        val ra = context.getSharedPreferences("retro_achievements", Context.MODE_PRIVATE)
        val user = ra.getString("username", null)?.takeIf { it.isNotBlank() } ?: return false
        val token = ra.getString("token", null)?.takeIf { it.isNotBlank() } ?: return false
        val hardcore = RetroAchievementsManager.isHardcorePreferred(context)
        return runCatching {
            NativeApp.setSetting("Achievements", "Enabled", "bool", "true")
            NativeApp.setSetting("Achievements", "Username", "string", user)
            NativeApp.setSetting("Achievements", "Token", "string", token)
            NativeApp.setSetting("Achievements", "ChallengeMode", "bool", hardcore.toString())
            NativeApp.setSetting("Achievements", "HardcoreMode", "bool", hardcore.toString())
            NativeApp.commitSettings()
            runCatching { NativeApp.setHardcoreMode(hardcore) }
            NativeApp.clearAchievementsHostOverride()
            true
        }.getOrDefault(false)
    }
}
