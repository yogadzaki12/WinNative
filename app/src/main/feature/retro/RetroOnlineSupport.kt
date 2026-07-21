package com.winlator.cmod.feature.retro

enum class RetroOnlineMode {
    NONE,
    DEV9_ONLINE,
    LIBRETRO_NETPLAY,
}

object RetroOnlineSupport {
    fun modeFor(systemId: String?): RetroOnlineMode =
        when (systemId) {
            RetroSystems.PS2.id -> RetroOnlineMode.DEV9_ONLINE
            RetroSystems.NES.id,
            RetroSystems.SNES.id,
            RetroSystems.GAMEBOY.id,
            RetroSystems.GAMEBOY_COLOR.id,
            RetroSystems.GBA.id,
            RetroSystems.GENESIS.id,
            RetroSystems.MASTER_SYSTEM.id,
            RetroSystems.GAME_GEAR.id,
            RetroSystems.N64.id,
            RetroSystems.PSX.id,
            -> RetroOnlineMode.LIBRETRO_NETPLAY
            else -> RetroOnlineMode.NONE
        }

    fun supportsDev9(systemId: String?): Boolean = modeFor(systemId) == RetroOnlineMode.DEV9_ONLINE

    fun supportsNetplayCore(systemId: String?): Boolean = modeFor(systemId) == RetroOnlineMode.LIBRETRO_NETPLAY

    fun netplayFrontendReady(): Boolean = true
}
