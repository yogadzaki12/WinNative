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

    private fun patchesFile(ctx: Context, serial: String, crc: String): File {
        val dir = File(MainActivityRuntime.assetCopyRoot(ctx), "patches").apply { mkdirs() }
        return File(dir, "${serial}_${crc}.pnach")
    }

    /**
     * Apply (or clear) the DNAS bypass for the running game. Idempotent; safe to
     * call repeatedly. Returns a short status string for logging, or null if the
     * game has no bundled bypass.
     */
    fun apply(ctx: Context, serialRaw: String, crcRaw: String): String? {
        val serial = serialRaw.trim().uppercase()
        val crc = crcRaw.trim().uppercase()
        val game = loadDb(ctx)[serial] ?: return null
        val file = patchesFile(ctx, serial, crc)

        // Assemble unique labeled section names once (shared by enable + clear paths).
        val named = ArrayList<Pair<String, Variant>>()
        val used = HashMap<String, Int>()
        for (v in game.variants) {
            val base = v.name
            val n = (used[base] ?: 0) + 1
            used[base] = n
            named.add((if (n > 1) "$base ($n)" else base) to v)
        }
        val allNames = named.map { it.first }.toTypedArray()

        if (!isEnabled(ctx)) {
            runCatching { NativeApp.setEnabledPatches(false, allNames, emptyArray()) }
            runCatching { if (file.exists()) file.delete() }
            runCatching { NativeApp.reloadPatches() }
            return "disabled"
        }

        val sb = StringBuilder()
        sb.append("gametitle=").append(game.title).append(" (DNAS Bypass)\n")
        val enableNames = ArrayList<String>()
        for ((nm, v) in named) {
            val lines = v.codes.mapNotNull { toPatchLine(it) }
            if (lines.isEmpty()) continue
            sb.append("\n[").append(nm).append("]\n")
            lines.forEach { sb.append(it).append('\n') }
            // CRC-safe: auto flag AND (untagged OR CRC matches this exact build).
            if (v.auto && (v.crc == null || v.crc.equals(crc, ignoreCase = true))) {
                enableNames.add(nm)
            }
        }

        runCatching { file.writeText(sb.toString()) }
        runCatching { NativeApp.setEnabledPatches(false, allNames, enableNames.toTypedArray()) }
        runCatching { NativeApp.reloadPatches() }
        return if (enableNames.isNotEmpty()) "enabled ${enableNames.joinToString()}" else "available (no CRC match)"
    }

    /**
     * Poll for the running game's serial + CRC to become available, then apply.
     * Call once from a background thread shortly after the game boots.
     */
    fun applyWhenReady(ctx: Context) {
        var tries = 0
        while (tries < 60) {
            val s = runCatching { NativeApp.getGameSerial() }.getOrNull()?.takeIf { it.isNotBlank() }
            val c = runCatching { NativeApp.getGameCRC() }.getOrNull()?.takeIf { it.length == 8 && it != "00000000" }
            if (s != null && c != null) {
                runCatching { apply(ctx, s, c) }
                    .onSuccess { if (it != null) android.util.Log.i("Ps2DnasBypass", "$s/$c -> $it") }
                // Then fold in any cheats/patches the user staged pre-game from
                // Shortcut Settings (keyed by serial; now materialised with the CRC).
                runCatching { Ps2CheatStaging.materialize(ctx, s, c) }
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
