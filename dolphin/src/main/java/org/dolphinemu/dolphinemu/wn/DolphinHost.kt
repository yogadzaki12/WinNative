package org.dolphinemu.dolphinemu.wn

import android.app.Activity

/** Cross-module hooks installed by the app's Application (armsx2 pattern). */
object DolphinHost {
    @Volatile
    var attachOverlay: ((Activity) -> Unit)? = null

    @Volatile
    var onStageSaves: ((Activity) -> Unit)? = null
}
