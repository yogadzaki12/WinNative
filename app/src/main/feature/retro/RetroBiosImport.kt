package com.winlator.cmod.feature.retro

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object RetroBiosImport {
    private val KNOWN_BIOS: Map<String, String> =
        mapOf(
            "8dd7d5296a650fac7319bce665a6a53c" to "scph5500.bin",
            "490f666e1afb15b7362b406ed1cea246" to "scph5501.bin",
            "32736f17079d0b2b7024407c39bd3050" to "scph5502.bin",
            "924e392ed05558ffdb115408c263dccf" to "scph1001.bin",
            "1e68c231d0896b7eadcad1d7d8e76129" to "scph7001.bin",
        )

    fun importFromUri(
        context: Context,
        uri: Uri,
    ): Result<String> =
        runCatching {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read file")
            val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
            val canonical =
                KNOWN_BIOS[md5]
                    ?: throw IllegalArgumentException("Not a recognized PlayStation BIOS")
            val dir = RetroCoreManager.systemDir(context)
            val target = File(dir, canonical)
            val tmp = File(dir, "$canonical.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            canonical
        }
}
