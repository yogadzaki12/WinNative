package com.winlator.cmod.feature.retro

import android.content.Context
import com.winlator.cmod.runtime.container.ContainerManager
import java.io.File

object RetroRomScanner {
    fun scanConfiguredFolder(context: Context): Int {
        val dir = RetroDefaults.romsDir(context)?.let(::File) ?: return 0
        if (!dir.isDirectory) return 0
        return scan(context, dir)
    }

    fun scan(context: Context, dir: File): Int {
        val existing = existingRomPaths(context)
        val roms =
            dir.walkTopDown()
                .maxDepth(4)
                .filter { it.isFile && RetroSystems.isRetroRom(it.absolutePath) }
                .filter { it.absolutePath !in existing }
                .toList()
        var added = 0
        for (rom in roms) {
            val system = RetroSystems.detectForFile(rom.absolutePath) ?: continue
            val name = rom.nameWithoutExtension.trim().ifBlank { rom.name }
            if (RetroShortcuts.create(context, name, rom.absolutePath, system)) added++
        }
        return added
    }

    private fun existingRomPaths(context: Context): Set<String> =
        runCatching {
            ContainerManager(context).loadShortcuts()
                .filter { RetroShortcuts.isRetroShortcut(it) }
                .map { RetroShortcuts.romPath(it) }
                .filter { it.isNotBlank() }
                .toHashSet()
        }.getOrDefault(emptySet())
}
