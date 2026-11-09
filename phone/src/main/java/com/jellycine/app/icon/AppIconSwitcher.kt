package com.jellycine.app.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.jellycine.app.R
import com.jellycine.shared.R as SharedR
import com.jellycine.shared.preferences.Preferences

enum class LauncherIconStyle(
    val id: String,
    internal val aliasName: String,
    @DrawableRes val previewRes: Int,
    @StringRes val nameRes: Int
) {
    Jellyfish("01", "JellyCineLauncher01", R.drawable.ic_app_icon_01, SharedR.string.settings_app_icon_jellyfish),
    FilmJ("02", "JellyCineLauncher02", R.drawable.ic_app_icon_02, SharedR.string.settings_app_icon_film_j),
    Projection("03", "JellyCineLauncher03", R.drawable.ic_app_icon_03, SharedR.string.settings_app_icon_projection),
    JellyPlay("04", "JellyCineLauncher04", R.drawable.ic_app_icon_04, SharedR.string.settings_app_icon_jelly_play),
    PrivateCinema("05", "JellyCineLauncher05", R.drawable.ic_app_icon_05, SharedR.string.settings_app_icon_private_cinema);

    companion object {
        fun fromId(id: String?): LauncherIconStyle = entries.firstOrNull { it.id == id } ?: FilmJ
    }
}

/**
 * Switches the launcher entry by enabling exactly one launcher alias and persists the choice.
 * Aliases share [com.jellycine.app.ui.activity.JellyCineActivity] as their target.
 */
object AppIconSwitcher {
    fun current(context: Context): LauncherIconStyle =
        LauncherIconStyle.fromId(Preferences(context).getLauncherIcon())

    fun select(context: Context, style: LauncherIconStyle) {
        apply(context, style)
        Preferences(context).setLauncherIcon(style.id)
    }

    /**
     * Restores the persisted icon selection when component state was reset, for example after
     * an app data restore. Only changed components are written, so startup stays cheap.
     */
    fun sync(context: Context) {
        apply(context, current(context))
    }

    private fun apply(context: Context, selected: LauncherIconStyle) {
        val manager = context.packageManager
        // Enable the new entry first so the launcher never sees a gap with no icon.
        val selectedComponent = component(context, selected)
        if (!isEnabled(manager, selectedComponent)) {
            setEnabled(manager, selectedComponent, true)
        }
        LauncherIconStyle.entries
            .filter { it != selected }
            .forEach { style ->
                val component = component(context, style)
                if (isEnabled(manager, component)) setEnabled(manager, component, false)
            }
    }

    private fun component(context: Context, style: LauncherIconStyle) = ComponentName(
        context.packageName,
        "${context.packageName}.ui.activity.${style.aliasName}"
    )

    private fun setEnabled(manager: PackageManager, component: ComponentName, enabled: Boolean) {
        manager.setComponentEnabledSetting(
            component,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }

    private fun isEnabled(manager: PackageManager, component: ComponentName): Boolean {
        return when (manager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> try {
                manager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
