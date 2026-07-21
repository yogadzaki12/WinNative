package com.winlator.cmod.feature.retro

import android.content.Context
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONObject
import java.io.File

/**
 * Auto-applies per-game DNAS authentication bypass patches so PS2 online-revival
 * titles (ps2online.com, PSRewired, …) get past Sony's long-dead DNAS check.
 *
 * The codes come from PSRewired's Game-Information database (bundled as the
 * `assets/dnas/dnas_bypass.json` asset). They are written as a LABELED patch into
 * the emulator's `patches/` folder — a namespace nothing else in the app writes
 * to, and labeled patch files merge with the bundled compatibility patches rather
 * than replacing them. Codes are emitted verbatim through the `extended` pnach
 * type, which is armsx2/PCSX2's full raw Action-Replay/PS2rd interpreter, so each
 * `AAAAAAAA VVVVVVVV` raw line reproduces exactly what a hardware code device does.
 *
 * Enabling goes through the patches enable-list ([Patches] Enable), which is a
 * targeted add/remove — it never clobbers the user's cheats or other patches.
 * Gated by the `wn.ps2.net.dnasbypass` pref (default ON). Multi-version games are
 * CRC-safe: a version-specific variant is only auto-enabled when the running
 * game's CRC matches its tag, so the wrong build's code is never applied.
 */
object Ps2DnasBypass {
    const val PREF = "wn.ps2.net.dnasbypass"

    @Volatile
    private var db: Map<String, Game>? = null

    private data class Variant(val name: String, val codes: List<String>, val crc: String?, val auto: Boolean)

    private data class Game(val title: String, val variants: List<Variant>)

    private fun loadDb(ctx: Context): Map<String, Game> {
        db?.let { return it }
        val parsed =
            runCatching {
                val text = ctx.assets.open("dnas/dnas_bypass.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val map = HashMap<String, Game>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val serial = keys.next()
                    val g = root.getJSONObject(serial)
                    val vs = g.getJSONArray("cheats")
                    val variants = ArrayList<Variant>()
                    for (i in 0 until vs.length()) {
                        val v = vs.getJSONObject(i)
                        val codesArr = v.getJSONArray("codes")
                        val codes = (0 until codesArr.length()).map { codesArr.getString(it) }
                        variants.add(
                            Variant(
                                v.getString("name"),
                                codes,
                                v.optString("crc").takeIf { it.isNotBlank() },
                                v.optBoolean("auto", false),
                            ),
                        )
                    }
                    map[serial.uppercase()] = Game(g.getString("title"), variants)
                }
                map
            }.getOrDefault(emptyMap())
        db = parsed
        return parsed
    }

    /** True if this game has a DNAS bypass in the bundled database. */
    fun hasBypass(ctx: Context, serial: String?): Boolean =
        serial != null && loadDb(ctx).containsKey(serial.trim().uppercase())

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).getBoolean(PREF, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply()
    }

    /** A DNAS-bypass section for a game, for the pre-game Cheats view. [body] is the
     *  full pnach `[Name]` + code block; [auto] marks the default-on variant. */
    data class BypassEntry(val name: String, val body: String, val auto: Boolean)

    private fun disabledKey(serial: String) = "wn.ps2.dnas.disabled.${serial.trim().uppercase()}"

    /** Section names the user has explicitly turned OFF for this game (pre-game). */
    fun disabledNames(ctx: Context, serial: String): Set<String> {
        val raw = ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).getString(disabledKey(serial), "") ?: ""
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun setDisabledNames(ctx: Context, serial: String, names: Set<String>) {
        val arr = org.json.JSONArray()
        names.forEach { arr.put(it) }
        ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).edit()
            .putString(disabledKey(serial), arr.toString()).apply()
    }

    fun ensureSingleDnasEnabled(ctx: Context, serial: String, allNames: Set<String>): Set<String> {
        if (allNames.isEmpty()) return emptySet()
        val disabled = disabledNames(ctx, serial).toMutableSet()
        val enabled = allNames.filter { it !in disabled }
        if (enabled.size <= 1) return disabled
        val keep = enabled.first()
        val next = allNames - keep
        setDisabledNames(ctx, serial, next)
        return next
    }

    /** The bundled DNAS-bypass sections for [serial], with the same unique labels
     *  [apply] uses — so the pre-game Cheats view can show exactly what will be
     *  written/enabled at boot. Empty if the game has no bundled bypass. */
    fun bypassEntries(ctx: Context, serial: String?): List<BypassEntry> {
        val game = loadDb(ctx)[serial?.trim()?.uppercase()] ?: return emptyList()
        val used = HashMap<String, Int>()
        return game.variants.mapNotNull { v ->
            val base = v.name
            val n = (used[base] ?: 0) + 1
            used[base] = n
            val nm = if (n > 1) "$base ($n)" else base
            val lines = v.codes.mapNotNull { toPatchLine(it) }
            if (lines.isEmpty()) {
                null
            } else {
                val body = buildString {
                    append("[").append(nm).append("]\n")
                    lines.forEach { append(it).append('\n') }
                }
                BypassEntry(nm, body, v.auto)
            }
        }
    }

    /** Convert a raw code line "AAAAAAAA VVVVVVVV" to a pnach `extended` patch line. */
    private fun toPatchLine(raw: String): String? {
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size != 2) return null
        val a = parts[0].uppercase()
        val v = parts[1].uppercase()
        if (a.length != 8 || v.length != 8) return null
        if (!a.all { it.isDigit() || it in 'A'..'F' } || !v.all { it.isDigit() || it in 'A'..'F' }) return null
        return "patch=1,EE,$a,extended,$v"
    }

    /** A resolved DNAS-bypass patch section for a game. */
    data class Section(val name: String, val body: String, val enabledByDefault: Boolean)

    /**
     * All DNAS-bypass sections for [serial], with enable state resolved for this
     * build ([crc]): enabled by default when the global bypass pref is on, the
     * variant is `auto`, its CRC tag matches (or is untagged), and the user hasn't
     * turned it off in Shortcut Settings / the Retro Server Menu. Empty when the
     * game has no bundled bypass. This is the single authority for what the unified
     * apply writes/enables — see [Ps2CheatStaging.applyAll].
     */
    fun sectionsFor(ctx: Context, serialRaw: String, crcRaw: String): List<Section> {
        val serial = serialRaw.trim().uppercase()
        val crc = crcRaw.trim().uppercase()
        val game = loadDb(ctx)[serial] ?: return emptyList()
        val globalOn = isEnabled(ctx)
        val disabled = disabledNames(ctx, serial)
        val used = HashMap<String, Int>()
        val out = ArrayList<Section>()
        for (v in game.variants) {
            val base = v.name
            val n = (used[base] ?: 0) + 1
            used[base] = n
            val nm = if (n > 1) "$base ($n)" else base
            val lines = v.codes.mapNotNull { toPatchLine(it) }
            if (lines.isEmpty()) continue
            val body = buildString {
                append("[").append(nm).append("]\n")
                lines.forEach { append(it).append('\n') }
            }
            val crcOk = v.crc == null || v.crc.equals(crc, ignoreCase = true)
            val on = globalOn && v.auto && nm !in disabled && crcOk
            out.add(Section(nm, body, on))
        }
        val enabled = out.filter { it.enabledByDefault }
        if (enabled.size <= 1) return out
        val keep = enabled.first().name
        return out.map { if (it.enabledByDefault && it.name != keep) it.copy(enabledByDefault = false) else it }
    }

    /**
     * Poll for the running game's serial + CRC to become available, then run the
     * unified apply (DNAS bypass + the user's staged cheats/patches from Shortcut
     * Settings, all keyed off one source of truth). Call once from a background
     * thread shortly after the game boots.
     */
    fun applyWhenReady(ctx: Context) {
        var tries = 0
        while (tries < 60) {
            val s = runCatching { NativeApp.getGameSerial() }.getOrNull()?.takeIf { it.isNotBlank() }
            val c = runCatching { NativeApp.getGameCRC() }.getOrNull()?.takeIf { it.length == 8 && it != "00000000" }
            if (s != null && c != null) {
                runCatching { Ps2CheatStaging.applyAll(ctx, s, c) }
                    .onFailure { android.util.Log.w("Ps2DnasBypass", "applyAll failed for $s/$c", it) }
                return
            }
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                return
            }
            tries++
        }
    }
}
