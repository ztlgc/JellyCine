package com.jellycine.player.discord

import android.app.Activity

internal object DiscordSdkBridge {
    fun setEngineActivity(activity: Activity) {
        check(false) { "Discord SDK is not included in this build" }
    }
}
