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
     * THE single materialization path — used identically at boot
     * ([Ps2DnasBypass.applyWhenReady]) and on the in-game Cheats "Apply". Rebuilds
     * the live `<serial>_<crc>.pnach` files to EXACTLY reflect the source of truth:
     *
     *  - patches file = staged patches (user selections) + DNAS-bypass sections
     *  - cheats file  = staged cheats
     *
     * and sets the enable-lists to match. Because it rewrites the files wholesale,
     * de-selecting or deleting an item actually removes it (unlike a merge). Needs a
     * real 8-char CRC; the staging files (`<serial>.pnach`, no CRC) are the persistent
     * store and are never touched here.
     */
    fun applyAll(ctx: Context, serialRaw: String, crcRaw: String) {
        val serial = serialRaw.trim().uppercase()
        val crc = crcRaw.trim().uppercase()
        if (serial.isBlank() || crc.length != 8) return
        val suffix = "_$crc"

        // ── Patches: user-staged patches + DNAS-bypass sections ──
        val stagedPatches = read(ctx, serial, true)
        val dnas = Ps2DnasBypass.sectionsFor(ctx, serial, crc)
        val patchByName = LinkedHashMap<String, PatchRepo.Entry>()
        stagedPatches.forEach { patchByName[it.name] = it }
        dnas.forEach { s -> if (s.name !in patchByName) patchByName[s.name] = PatchRepo.Entry(s.name, "", s.body, "patches") }
        val patchFile = File(dir(ctx, true), "$serial$suffix.pnach")
        if (patchByName.isEmpty()) {
            runCatching { patchFile.delete() }
        } else {
            runCatching { patchFile.writeText(PatchRepo.buildPnach(serial, patchByName.values.toList())) }
        }
        val enabledPatches = (stagedPatches.map { it.name } + dnas.filter { it.enabledByDefault }.map { it.name }).distinct()
        runCatching { NativeApp.setEnabledPatches(false, patchByName.keys.toTypedArray(), enabledPatches.toTypedArray()) }

        // ── Cheats: user-staged cheats only ──
        val stagedCheats = read(ctx, serial, false)
        val cheatFile = File(dir(ctx, false), "$serial$suffix.pnach")
        if (stagedCheats.isEmpty()) {
            runCatching { cheatFile.delete() }
        } else {
            runCatching { cheatFile.writeText(PatchRepo.buildPnach(serial, stagedCheats)) }
            runCatching {
                NativeApp.setSetting("EmuCore", "EnableCheats", "bool", "true")
                NativeApp.commitSettings()
            }
        }
        val cheatNames = stagedCheats.map { it.name }.toTypedArray()
        runCatching { NativeApp.setEnabledPatches(true, cheatNames, cheatNames) }

        runCatching { NativeApp.reloadPatches() }
    }
}
