package com.jellycine.app.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val DashboardBarHeight = 60.dp
internal val DashboardBarMargin = 10.dp

@Composable
internal fun dashboardContentBottomPadding(keyboardHidesBar: Boolean = false): Dp {
    if (keyboardHidesBar && WindowInsets.ime.getBottom(LocalDensity.current) > 0) return 16.dp
    return DashboardBarHeight + DashboardBarMargin * 2 + 8.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

@Composable
fun containerWidthDp(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return with(density) { windowInfo.containerSize.width.toDp() }
}

@Composable
fun containerHeightDp(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return with(density) { windowInfo.containerSize.height.toDp() }
}

fun isTabletLayout(screenWidthDp: Dp): Boolean = screenWidthDp >= 600.dp

fun isTabletDetailLayout(screenWidthDp: Dp, screenHeightDp: Dp): Boolean =
    isTabletLayout(screenWidthDp) && screenWidthDp > screenHeightDp

fun detailContentMaxWidth(
    screenWidthDp: Dp,
    horizontalPadding: Dp,
    useTabletLayout: Boolean = isTabletLayout(screenWidthDp)
): Dp? {
    return if (useTabletLayout) {
        (screenWidthDp - (horizontalPadding * 2)).coerceAtMost(780.dp)
    } else {
        null
    }
}

fun detailActionWidth(
    screenWidthDp: Dp,
    useTabletLayout: Boolean = isTabletLayout(screenWidthDp)
): Float = if (useTabletLayout) 0.40f else 1f
