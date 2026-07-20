package com.winlator.cmod.feature.retro

import android.content.Context
import com.armsx2.PatchRepo
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File

/**
 * Per-serial staging store for cheats/patches the user adds **before the game is
 * running** (from Shortcut Settings, where only the disc serial is known — not the
 * ELF CRC that the emulator names its live pnach files with).
 *
 * Staged sections are written to `cheats/<serial>.pnach` / `patches/<serial>.pnach`.
 * Those serial-only names are never matched by the emulator's `<serial>_<crc>*` /
 * `<crc>*` globs, so they sit inert until [materialize] runs at boot — once the
 * real CRC is known — and merges them into the live `<serial>_<crc>.pnach`, then
 * enables exactly the staged sections (leaving DNAS-bypass and other groups, which
 * use disjoint names, untouched — see setEnabledPatches' scoped remove/add).
 */
object Ps2CheatStaging {
    private fun dir(ctx: Context, isPatch: Boolean): File =
        File(MainActivityRuntime.assetCopyRoot(ctx), if (isPatch) "patches" else "cheats").apply { mkdirs() }

    private fun stagingFile(ctx: Context, serial: String, isPatch: Boolean): File =
        File(dir(ctx, isPatch), "$serial.pnach")

    /** Staged entries for [serial] (empty if none). [source] tags them "custom"/"patches". */
    fun read(ctx: Context, serial: String, isPatch: Boolean): List<PatchRepo.Entry> {
        val f = stagingFile(ctx, serial, isPatch)
        if (!f.isFile) return emptyList()
        val src = if (isPatch) "patches" else "custom"
        return runCatching {
            PatchRepo.parseInstalled(f.readText(), if (isPatch) "patches" else "cheats").second
                .map { PatchRepo.Entry(it.name, it.description, it.body, src) }
        }.getOrDefault(emptyList())
    }

    /** Overwrite the staging file with [entries] (empty list deletes it). */
    fun write(ctx: Context, serial: String, isPatch: Boolean, title: String, entries: List<PatchRepo.Entry>) {
        val f = stagingFile(ctx, serial, isPatch)
        if (entries.isEmpty()) {
            f.delete()
            return
        }
        runCatching { f.writeText(PatchRepo.buildPnach(title.ifBlank { serial }, entries)) }
    }

    /**
     * Merge staged sections into the live `<serial>_<crc>.pnach` files and enable
     * them. Called from the boot hook once serial + CRC are known. Safe to call
     * when nothing is staged (no-op). Never removes existing sections.
     */
    fun materialize(ctx: Context, serialRaw: String, crcRaw: String) {
        val serial = serialRaw.trim().uppercase()
        val crc = crcRaw.trim().uppercase()
        if (serial.isBlank() || crc.length != 8) return
        var changed = false
        for (isPatch in listOf(false, true)) {
            val staged = read(ctx, serial, isPatch)
            if (staged.isEmpty()) continue
            val liveDir = dir(ctx, isPatch)
            val live = File(liveDir, "${serial}_$crc.pnach")
            val existingText = if (live.isFile) live.readText() else ""
            val parsed = runCatching { PatchRepo.parseInstalled(existingText, if (isPatch) "patches" else "cheats") }.getOrNull()
            val title = parsed?.first?.takeIf { it.isNotBlank() } ?: serial
            val existing = parsed?.second.orEmpty()
                .map { PatchRepo.Entry(it.name, it.description, it.body, if (isPatch) "patches" else "custom") }
            val existingNames = existing.map { it.name }.toSet()
            // Keep every existing section (DNAS etc.); append staged ones not already present.
            val merged = existing + staged.filter { it.name !in existingNames }
            runCatching { live.writeText(PatchRepo.buildPnach(title, merged)) }
            if (isPatch) {
                // Patches are always-applied; cheats need EnableCheats on.
            } else {
                runCatching {
                    NativeApp.setSetting("EmuCore", "EnableCheats", "bool", "true")
                    NativeApp.commitSettings()
                }
            }
            val names = staged.mapNotNull { it.name.takeIf(String::isNotBlank) }.distinct().toTypedArray()
            runCatching { NativeApp.setEnabledPatches(!isPatch, names, names) }
            changed = true
        }
        if (changed) runCatching { NativeApp.reloadPatches() }
    }
}
