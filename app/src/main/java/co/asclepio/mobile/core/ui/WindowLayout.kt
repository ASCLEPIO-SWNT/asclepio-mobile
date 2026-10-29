package co.asclepio.mobile.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

enum class PaneMode { Single, Dual }

/** Each panel needs readable space at the effective user + system text scale. */
fun paneMode(widthSizeClass: WindowWidthSizeClass, availableWidthDp: Float = Float.POSITIVE_INFINITY, effectiveTextScale: Float = 1f): PaneMode =
    if (widthSizeClass == WindowWidthSizeClass.Compact || availableWidthDp < 560f * effectiveTextScale + 24f) PaneMode.Single else PaneMode.Dual

@Composable
fun adaptivePaneMode(widthSizeClass: WindowWidthSizeClass): PaneMode {
    val density = LocalDensity.current
    val preferences = LocalUiPreferences.current
    val widthDp = LocalWindowInfo.current.containerSize.width / density.density - 32f
    val scale = density.fontScale * preferences.textScale * if (preferences.dyslexiaFont) 1.1f else 1f
    return paneMode(widthSizeClass, widthDp, scale)
}
