package com.jellycine.app

import android.app.Application
import coil3.PlatformContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.jellycine.app.locale.AppLanguageManager
import com.jellycine.app.icon.AppIconSwitcher
import com.jellycine.app.util.logging.CrashHandler
import com.jellycine.shared.util.image.ImageLoaderConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JellyCineApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.followSystemLanguage(this)
        AppIconSwitcher.sync(this)

        // Initialize crash handler for better crash log collection
        CrashHandler.initialize(this)
        com.jellycine.app.deletion.VideoDeletionController.get(this)
        com.jellycine.app.sync.WatchSyncController.get(this)
        // 按 24 小时节流的静默检查，失败不打扰用户。
        com.jellycine.app.update.AppUpdateController.get(this).checkOnLaunch()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoaderConfig.createOptimizedImageLoader(context)
    }
}
