package co.asclepio.mobile

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.asclepio.mobile.core.auth.StaffDestination
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.feature.dashboards.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DashboardScreensInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val counts = mapOf(
        EncounterStatus.WAITING_TRIAGE to DashboardRead.Ready(EncounterCount(100, true)),
        EncounterStatus.TRIAGED to DashboardRead.Ready(EncounterCount(2, false)),
        EncounterStatus.IN_CARE to DashboardRead.Ready(EncounterCount(0, false)),
    )
    @Test fun `R2 medicine keeps header summary useful main and pending strip reachable at320dp text200`() {
        var opened: String? = null
        var all = false
        val state = DashboardOverviewUiState(StaffDestination.MEDICINE, counts = counts,
            recent = DashboardRead.Ready(listOf(RecentEncounter("opaque-id", "DEV Ana Demo", "Sede Centro", "2026-10-29T15:00:00.000Z", "REGISTERED"))))
        compose.setContent { Spanish {
            AsclepioTheme(UiPreferences(textScale = 2f)) { Box(Modifier.width(320.dp)) {
                DashboardOverviewScreen(state, {}, {}, {}, {}, {}, { opened = it }, { all = true }, { _, _ -> })
            } }
        } }
        compose.onNodeWithText("Medicina").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithContentDescription("Al menos 100").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText("Atenciones recientes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Ver atención: DEV Ana Demo").performScrollTo().performClick()
        assertEquals("opaque-id", opened)
        compose.onNodeWithText("Ver todas las atenciones").performScrollTo().performClick()
        assertTrue(all)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("Cola por nivel y espera").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Próximamente").performScrollTo().assertIsDisplayed()
    }
    @Test fun `R9 administration keeps actual sites and counts alongside future reports without patient rows or export action`() {
        val state = DashboardOverviewUiState(StaffDestination.ADMINISTRATION, counts = counts,
            sites = DashboardRead.Ready(listOf(PublicSiteDto("site", "Sede Norte"))),
            siteCounts = mapOf("site" to DashboardRead.Ready(SiteEncounterCount("site", "Sede Norte", EncounterCount(0, false)))),
            recent = DashboardRead.Ready(listOf(RecentEncounter("private", "PRIVATE NAME", "Sede Norte", "2026-10-29T15:00:00.000Z", "WAITING_TRIAGE"))))
        compose.setContent { Spanish { AsclepioTheme { DashboardOverviewScreen(state, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }) } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText("Atenciones por sede").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sede Norte").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("Indicadores y reportes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Próximamente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PRIVATE NAME").assertDoesNotExist()
        compose.onAllNodes(hasClickAction() and hasText("Descargar CSV")).assertCountEquals(0)
    }
    @Composable private fun Spanish(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("es")) }
        CompositionLocalProvider(LocalContext provides context.createConfigurationContext(config), LocalConfiguration provides config, content = content)
    }
}
