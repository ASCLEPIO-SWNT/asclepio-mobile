package co.asclepio.mobile.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
    @Test fun `R35 Compact usa una sola columna`() {
        assertEquals(PaneMode.Single, paneMode(WindowWidthSizeClass.Compact))
    }

    @Test fun `R35 Medium y Expanded usan dos paneles`() {
        assertEquals(PaneMode.Dual, paneMode(WindowWidthSizeClass.Medium))
        assertEquals(PaneMode.Dual, paneMode(WindowWidthSizeClass.Expanded))
    }
    @org.junit.Test fun `R19 panels stack when effective text or available width requires it`() {
        org.junit.Assert.assertEquals(PaneMode.Single, paneMode(WindowWidthSizeClass.Medium, 600f, 2f))
        org.junit.Assert.assertEquals(PaneMode.Single, paneMode(WindowWidthSizeClass.Expanded, 500f, 1f))
        org.junit.Assert.assertEquals(PaneMode.Dual, paneMode(WindowWidthSizeClass.Expanded, 1200f, 2f))
    }
}
