package co.asclepio.mobile.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

enum class PaneMode { Single, Dual }

/** Teléfono (Compact): una columna. Tablet (Medium o Expanded): dos paneles (D16, R35). */
fun paneMode(widthSizeClass: WindowWidthSizeClass): PaneMode =
    if (widthSizeClass == WindowWidthSizeClass.Compact) PaneMode.Single else PaneMode.Dual
