package org.dolphinemu.dolphinemu.wn

import android.app.Activity

/** Cross-module hooks installed by the app's Application (armsx2 pattern). */
object DolphinHost {
    @Volatile
    var attachOverlay: ((Activity) -> Unit)? = null

    @Volatile
    var onStageSaves: ((Activity) -> Unit)? = null

    /** NetPlay status for the in-game Online tab/banner: hosting, host code, members. */
    @Volatile
    var onNetplayStatus: ((hosting: Boolean, hostCode: String?, members: List<String>) -> Unit)? = null
}
