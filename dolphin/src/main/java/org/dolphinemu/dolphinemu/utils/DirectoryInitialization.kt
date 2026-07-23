package org.dolphinemu.dolphinemu.utils

import android.content.Context
import java.io.File

/** WinNative replacement: Sys/User dirs from packaged assets; JNI-bound name. */
object DirectoryInitialization {
    @JvmStatic
    external fun SetSysDirectory(path: String)

    @JvmStatic
    external fun SetGpuDriverDirectories(path: String, libPath: String)

    fun userDir(context: Context): File = File(context.filesDir, "dolphin-embed/User")

    @Volatile
    private var sysDirSet = false

    fun ensureDirectories(context: Context): File {
        val sysDir = File(context.filesDir, "dolphin-embed/Sys")
        val marker = File(sysDir, ".wn_sys_ready")
        if (!marker.isFile) {
            copyAssetFolder(context, "Sys", sysDir)
            marker.writeText("1")
        }
        // Native asserts if these dirs are set twice in one process lifetime.
        if (!sysDirSet) {
            sysDirSet = true
            SetSysDirectory(sysDir.path + File.separator)
            val driverRoot = File(context.filesDir, "dolphin-embed/GpuDrivers")
            File(driverRoot, "Extracted").mkdirs()
            File(driverRoot, "Tmp").mkdirs()
            File(driverRoot, "FileRedirect").mkdirs()
            SetGpuDriverDirectories(driverRoot.path, context.applicationInfo.nativeLibraryDir)
        }
        val user = userDir(context)
        File(user, "Config").mkdirs()
        return user
    }

    fun writeBaseConfig(userDir: File) {
        val ini = File(userDir, "Config/Dolphin.ini")
        if (!ini.isFile) {
            ini.writeText(
                """
                [Core]
                GFXBackend = Vulkan
                """.trimIndent() + "\n",
            )
        }
        File(userDir, "Config/Logger.ini").writeText(
            """
            [Options]
            Verbosity = 2
            WriteToConsole = True
            """.trimIndent() + "\n",
        )
    }

    private fun copyAssetFolder(
        context: Context,
        assetPath: String,
        dest: File,
    ) {
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (name in children) {
            copyAssetFolder(context, "$assetPath/$name", File(dest, name))
        }
    }
}
