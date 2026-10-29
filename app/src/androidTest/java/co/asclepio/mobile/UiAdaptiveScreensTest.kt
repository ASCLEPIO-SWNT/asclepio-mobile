package co.asclepio.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.asclepio.mobile.core.tenants.*
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.feature.account.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAdaptiveScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `R19 IPS selection and action remain reachable at 320dp and text 200 percent`() {
        val tenant = PublicTenantDto("tenant", "IPS de prueba", listOf(PublicSiteDto("site", "Sede de prueba")))
        var state by mutableStateOf(TenantPickerUiState.Success(listOf(tenant)))
        var confirmed = false
        compose.setContent {
            SpanishContent {
            AsclepioTheme(UiPreferences(textScale = 2f, dyslexiaFont = true)) {
                Box(Modifier.width(320.dp)) {
                    TenantPickerScreen(state, WindowWidthSizeClass.Compact,
                        onSelectTenant = { state = state.copy(selectedTenantId = it) },
                        onSelectSite = { state = state.copy(selectedSiteId = it) },
                        onConfirm = { confirmed = true }, onRetry = {}, onConfirmed = {})
                }
            }
            }
        }
        compose.onNodeWithText("IPS de prueba").performScrollTo().performClick()
        compose.onNodeWithText("Sede de prueba").performScrollTo().performClick()
        compose.onNodeWithText("Continuar").performScrollTo().assertIsEnabled().performClick()
        assertTrue(confirmed)
    }

    @Test fun `R21 localized error and loading remain semantic without discarding existing draft`() {
        var form by mutableStateOf(LoginForm(documentNumber = "synthetic-document", password = "synthetic-password"))
        val message = UiMessage(R.string.error_network)
        compose.setContent {
            SpanishContent {
            AsclepioTheme {
                LoginScreen(LoginUiState(form = form, message = message), onFormChange = { form = it }, onSubmit = {}, onChangeTenant = {}, onGoToSignUp = {}, onOpenStatus = null)
            }
            }
        }
        compose.onNodeWithText("No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Número de documento").assertTextContains("synthetic-document")
    }
    @Composable private fun SpanishContent(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { setLocale(java.util.Locale.forLanguageTag("es")) }
        CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration, content = content)
    }
}
