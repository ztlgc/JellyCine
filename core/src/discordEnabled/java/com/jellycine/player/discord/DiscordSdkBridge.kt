package com.jellycine.player.discord

import android.app.Activity

internal object DiscordSdkBridge {
    fun setEngineActivity(activity: Activity) {
        com.discord.socialsdk.DiscordSocialSdkInit.setEngineActivity(activity)
    }
}
