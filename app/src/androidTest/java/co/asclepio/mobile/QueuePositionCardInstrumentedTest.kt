package co.asclepio.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.asclepio.mobile.core.ui.AsclepioTheme
import co.asclepio.mobile.feature.encounters.queue.EstimateBasis
import co.asclepio.mobile.feature.encounters.queue.QueueConnection
import co.asclepio.mobile.feature.encounters.queue.QueuePhase
import co.asclepio.mobile.feature.encounters.queue.QueuePositionDto
import co.asclepio.mobile.feature.encounters.queue.QueueUiState
import co.asclepio.mobile.feature.encounters.queue.WaitEstimateDto
import co.asclepio.mobile.feature.encounters.ui.QueuePositionCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Render de la tarjeta de fila (F-10-mobile) en español, con `liveRegion` educado. */
@RunWith(AndroidJUnit4::class)
class QueuePositionCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val received = 5_000_000L

    private fun state(phase: QueuePhase, position: Int?, basis: EstimateBasis?, connection: QueueConnection = QueueConnection.LIVE) =
        QueueUiState(
            QueuePositionDto(phase, position, position?.let { it - 1 }, if (basis == EstimateBasis.RECENT_THROUGHPUT) WaitEstimateDto(15, 35) else null, basis, "2026-10-27T15:00:00.000Z"),
            received, connection, ended = false,
        )

    private fun show(state: QueueUiState, now: Long = received) = compose.setContent {
        SpanishContent { AsclepioTheme { QueuePositionCard(state, elapsedMs = { now }) } }
    }

    private val politeLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test fun `R3 R11 waiting card shows phase position people ahead and range in a polite live region`() {
        show(state(QueuePhase.WAITING_TRIAGE, 4, EstimateBasis.RECENT_THROUGHPUT))
        compose.onNodeWithText("Esperando triage", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Eres el 4.º", substring = true).assertIsDisplayed()
        compose.onNodeWithText("3 personas delante", substring = true).assertIsDisplayed()
        compose.onNodeWithText("15–35 min", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Puede cambiar si llegan casos más urgentes", substring = true).assertIsDisplayed()
        compose.onNode(politeLiveRegion and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertExists()
    }

    @Test fun `R4 R6 next patient without enough data`() {
        show(state(QueuePhase.WAITING_CARE, 1, EstimateBasis.INSUFFICIENT_DATA))
        compose.onNodeWithText("Esperando al médico", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Eres el siguiente", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Aún no hay datos suficientes para estimar el tiempo", substring = true).assertIsDisplayed()
    }

    @Test fun `R5 R7 called and no active phases`() {
        show(state(QueuePhase.CALLED, null, null))
        compose.onNodeWithText("Te están llamando: acércate al consultorio", substring = true).assertIsDisplayed()
    }

    @Test fun `R7 no active shows the reception hint`() {
        show(state(QueuePhase.NO_ACTIVE, null, null))
        compose.onNodeWithText("Acércate a recepción para entrar en la fila", substring = true).assertIsDisplayed()
    }

    @Test fun `R8 reconnecting keeps the last data with its age`() {
        show(state(QueuePhase.WAITING_TRIAGE, 4, EstimateBasis.RECENT_THROUGHPUT, QueueConnection.RECONNECTING), received + 5 * 60_000L)
        compose.onNodeWithText("Eres el 4.º", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sin conexión · actualizado hace 5 min", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reconectando…", substring = true).assertIsDisplayed()
    }

    @Composable private fun SpanishContent(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { setLocale(java.util.Locale.forLanguageTag("es")) }
        CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration, content = content)
    }
}
