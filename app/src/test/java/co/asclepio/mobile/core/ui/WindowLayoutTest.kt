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
}
