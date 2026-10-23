package com.jellycine.app

import android.app.Application
import coil3.PlatformContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.jellycine.app.locale.AppLanguageManager
import com.jellycine.app.util.logging.CrashHandler
import com.jellycine.shared.util.image.ImageLoaderConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JellyCineApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.followSystemLanguage(this)

        // Initialize crash handler for better crash log collection
        CrashHandler.initialize(this)
        com.jellycine.app.sync.WatchSyncController.get(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoaderConfig.createOptimizedImageLoader(context)
    }
}
