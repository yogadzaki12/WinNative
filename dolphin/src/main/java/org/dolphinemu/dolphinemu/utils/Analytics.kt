package org.dolphinemu.dolphinemu.utils

import androidx.annotation.Keep

/** WinNative stub: analytics is compiled out (-DENABLE_ANALYTICS=OFF); IDCache
 *  still binds getValue at JNI_OnLoad, so the class and method must exist. */
object Analytics {
    @Keep
    @JvmStatic
    fun getValue(key: String): String = ""
}
