package com.jellycine.app.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguageManager {
    fun followSystemLanguage(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null && !localeManager.applicationLocales.isEmpty) {
                // Reset a language selected in older versions of the app.
                localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
            }
        }
        Locale.setDefault(Resources.getSystem().configuration.locales[0])
    }
}
