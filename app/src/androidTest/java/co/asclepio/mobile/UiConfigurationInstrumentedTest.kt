package co.asclepio.mobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Parcel
import android.graphics.Paint
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.asclepio.mobile.core.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity/AppCompat/Navigation3 configuration, with drafts owned by the existing ViewModels. */
@RunWith(AndroidJUnit4::class)
class UiConfigurationInstrumentedTest {
    @get:Rule(order = 0) val api = UiInstrumentedApi()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetEntry() {
        val container = (compose.activity.application as AsclepioApp).container
        runBlocking {
            container.sessionManager.logout()
            container.sessionManager.showWelcome()
            container.uiPreferencesRepository.update { UiPreferences() }
        }
        compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es")) }
        compose.waitForIdle()
    }

    @Test fun `R2 welcome keeps two authorized entries and a single brand`() {
        compose.onAllNodesWithTag("app-brand").assertCountEquals(1)
        compose.onAllNodesWithText("Asclepio").assertCountEquals(1)
        compose.onNodeWithText("Continuar como paciente").assertExists()
        compose.onNodeWithText("Ingresar como personal").performClick()
        compose.onNodeWithText("Acceso del personal").assertIsDisplayed()
        compose.onAllNodesWithText("Asclepio").assertCountEquals(1)
        compose.onNodeWithText("Correo electrónico").assertExists()
    }

    @Test fun `R3 native fonts load and have distinct metrics`() {
        val context = compose.activity
        val axiforma = ResourcesCompat.getFont(context, R.font.axiforma_regular)
        val dyslexic = ResourcesCompat.getFont(context, R.font.opendyslexic_regular)
        assertNotNull(axiforma)
        assertNotNull(dyslexic)
        val paint = Paint().apply { textSize = 32f; typeface = axiforma }
        val regularWidth = paint.measureText("Asclepio ñ ã œ ß ü")
        paint.typeface = dyslexic
        assertTrue(kotlin.math.abs(regularWidth - paint.measureText("Asclepio ñ ã œ ß ü")) > 1f)
    }

    @Test fun `R6 locale theme and recreation preserve staff draft without private saved state`() {
        val email = "ui-private@example.test"
        val password = "Synthetic-Only-Password!"
        compose.onNodeWithText("Ingresar como personal").performClick()
        compose.onNodeWithText("Correo electrónico").performTextInput(email)
        compose.onNodeWithText("Contraseña").performTextInput(password)
        val application = compose.activity.application
        val probe = BundleProbe(listOf(email, password))
        application.registerActivityLifecycleCallbacks(probe)
        try {
            compose.onNodeWithTag("open-preferences").performClick()
            compose.onNodeWithTag("preference-theme-DARK").performScrollTo().performClick()
            for (locale in listOf("en", "pt", "fr", "de", "es")) {
                compose.onNodeWithTag("preference-locale-$locale").performScrollTo().performClick()
                compose.waitForIdle()
            }
            compose.onNodeWithTag("preferences-close").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText("Correo electrónico").assertTextContains(email)
            val drafts = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            assertTrue(drafts.any { it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == password })
            assertTrue(probe.savedCount > 0)
            assertFalse("Private synthetic draft entered savedInstanceState", probe.privateValueFound)
        } finally { application.unregisterActivityLifecycleCallbacks(probe) }
    }

    @Test fun `R20 profile date input and clinical draft stay in RAM across configuration`() {
        loginPatient()
        val generation = container().sessionManager.generation
        val name = "Nombre Sintético Cambiado"
        val complaint = "Motivo clínico sintético que sólo vive en memoria"
        val date = java.time.LocalDate.of(2001, 2, 3)
        val millis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val probe = BundleProbe(listOf(name, complaint, "2001-02-03", "03022001", "03/02/2001", "03/0", "31/02/2001"), listOf(millis))
        val application = compose.activity.application
        application.registerActivityLifecycleCallbacks(probe)
        try {
            text(R.string.home_action_profile).performScrollTo().performClick()
            compose.waitUntil(10_000) { exists(R.string.field_full_name) }
            text(R.string.field_full_name).performTextReplacement(name)
            compose.onNodeWithText("17/05/1990").performScrollTo().performClick()
            enterDate("03022001")
            // Valid, UNCONFIRMED input must retain both the modal and its provisional selection.
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("birth-date-input").assertTextContains("03022001")
            compose.onNodeWithTag("birth-date-accept").assertIsEnabled()
            compose.onNodeWithText("17/05/1990").assertExists()
            setSystemLocale("fr")
            compose.onNodeWithTag("birth-date-input").assertTextContains("03022001")
            // Incomplete/invalid input survives exactly; neither locale nor acceptance can reinterpret it.
            compose.onNodeWithTag("birth-date-input").performTextReplacement("03/0")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("birth-date-input").assertTextContains("03/0")
            compose.onNodeWithTag("birth-date-accept").assertIsNotEnabled()
            setSystemLocale("es")
            compose.onNodeWithTag("birth-date-input").assertTextContains("03/0")
            compose.onNodeWithTag("birth-date-input").performTextReplacement("31/02/2001")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("birth-date-input").assertTextContains("31/02/2001")
            compose.onNodeWithTag("birth-date-accept").assertIsNotEnabled()
            compose.onNodeWithTag("birth-date-input").performTextReplacement("03022001")
            compose.onNodeWithTag("birth-date-accept").performClick()
            compose.onNodeWithText("03/02/2001").performScrollTo().assertExists()
            // Cancelling a different provisional date keeps the confirmed field unchanged.
            compose.onNodeWithTag("birth-date-open").performClick()
            enterDate("04022002")
            compose.onNodeWithTag("birth-date-cancel").performClick()
            compose.onNodeWithText("03/02/2001").assertExists()
            assertEquals(generation, container().sessionManager.generation)
            text(R.string.field_full_name).assertTextContains(name)
            changeLocaleAndTheme("fr")
            changeLocaleAndTheme("es")
            text(R.string.field_full_name).assertTextContains(name)
            compose.onNodeWithText("03/02/2001").assertExists()
            text(R.string.navigate_back).performClick()
            text(R.string.home_action_new_preregistration).performScrollTo().performClick()
            compose.waitUntil(10_000) { exists(R.string.preregistration_complaint) }
            text(R.string.preregistration_complaint).performTextInput(complaint)
            changeLocaleAndTheme("de")
            changeLocaleAndTheme("es")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            text(R.string.preregistration_complaint).assertTextContains(complaint)
            compose.onNodeWithText("Sede QA Android").assertExists()
            assertEquals(generation, container().sessionManager.generation)
            assertTrue(probe.savedCount > 0)
            assertFalse("Private synthetic values or date entered the Activity Bundle", probe.privateValueFound)
            text(R.string.navigate_back).performClick()
            text(R.string.logout_confirm).performScrollTo().performClick()
            compose.onNode(hasText(compose.activity.getString(R.string.logout_confirm)) and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { container().sessionManager.generation != generation }
            loginPatient()
            text(R.string.home_action_new_preregistration).performScrollTo().performClick()
            compose.waitUntil(10_000) { exists(R.string.preregistration_complaint) }
            val draft = text(R.string.preregistration_complaint).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            assertEquals("", draft)
        } finally { application.unregisterActivityLifecycleCallbacks(probe) }
    }

    @Test fun `R5 Android application locale is reflected after Activity recreation`() {
        val previousGeneration = container().sessionManager.generation
        compose.activityRule.scenario.onActivity { activity ->
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                activity.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags("pt")
            } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt"))
        }
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals("pt", compose.activity.resources.configuration.locales[0].language)
        assertEquals(previousGeneration, container().sessionManager.generation)
        compose.onNodeWithTag("open-preferences").performClick()
        compose.onNodeWithTag("preference-locale-pt").performScrollTo().assertIsSelected()
    }

    @Test fun `R6 signup private fields survive locale and recreation without saved state`() {
        enterPatientLogin()
        text(R.string.login_go_to_signup).performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(R.string.signup_title) }
        val name = "Alta Sintética Privada"
        val document = "DEV-UI01-SIGNUP"
        val password = "Synthetic-Signup-Password!"
        text(R.string.field_document_number).performScrollTo().performTextInput(document)
        text(R.string.field_full_name).performScrollTo().performTextInput(name)
        text(R.string.field_password).performScrollTo().performTextInput(password)
        val probe = BundleProbe(listOf(name, document, password, "02/", "03022001", "03/02/2001"))
        val application = compose.activity.application
        application.registerActivityLifecycleCallbacks(probe)
        try {
            compose.onNodeWithTag("birth-date-open").performScrollTo().performClick()
            enterDate("02/")
            setSystemLocale("en")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("birth-date-input").assertTextContains("02/")
            compose.onNodeWithTag("birth-date-accept").assertIsNotEnabled()
            compose.onNodeWithTag("birth-date-input").performTextReplacement("03022001")
            setSystemLocale("es")
            compose.onNodeWithTag("birth-date-input").assertTextContains("03022001")
            compose.onNodeWithTag("birth-date-accept").performClick()
            compose.onNodeWithText("03/02/2001").assertExists()
            changeLocaleAndTheme("en")
            changeLocaleAndTheme("es")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            text(R.string.field_document_number).performScrollTo().assertTextContains(document)
            text(R.string.field_full_name).performScrollTo().assertTextContains(name)
            assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == password })
            assertTrue(probe.savedCount > 0)
            assertFalse("Private signup fields entered the Activity Bundle", probe.privateValueFound)
        } finally { application.unregisterActivityLifecycleCallbacks(probe) }
    }

    private fun container() = (compose.activity.application as AsclepioApp).container
    private fun exists(id: Int) = compose.onAllNodesWithText(compose.activity.getString(id)).fetchSemanticsNodes().isNotEmpty()
    private fun text(id: Int) = compose.onNodeWithText(compose.activity.getString(id))

    private fun enterPatientLogin() {
        if (exists(R.string.access_patient_action)) text(R.string.access_patient_action).performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(R.string.login_title) || exists(R.string.tenant_picker_title) }
        if (exists(R.string.login_title)) text(R.string.login_change_tenant).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("IPS QA Android").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("IPS QA Android").performScrollTo().performClick()
        compose.onNodeWithText("Sede QA Android").performScrollTo().performClick()
        text(R.string.tenant_picker_continue).performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(R.string.login_title) }
    }
    private fun loginPatient() {
        enterPatientLogin()
        text(R.string.document_type_cc).performScrollTo().performClick()
        text(R.string.field_document_number).performScrollTo().performTextInput("DEV-UI01-PATIENT")
        text(R.string.field_password).performScrollTo().performTextInput("Synthetic-Patient-Password!")
        text(R.string.login_submit).performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(R.string.home_action_profile) }
    }
    private fun changeLocaleAndTheme(locale: String) {
        compose.onNodeWithTag("open-preferences").performClick()
        compose.onNodeWithTag("preference-theme-DARK").performScrollTo().performClick()
        compose.onNodeWithTag("preference-locale-$locale").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("preferences-close").performScrollTo().performClick()
    }
    private fun setSystemLocale(locale: String) {
        compose.activityRule.scenario.onActivity { activity ->
            if (android.os.Build.VERSION.SDK_INT >= 33) activity.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(locale)
            else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
        }
        compose.waitForIdle()
    }
    private fun enterDate(text: String) {
        compose.onNodeWithTag("birth-date-toggle").performClick()
        compose.onNodeWithTag("birth-date-input").performTextReplacement(text)
        compose.waitForIdle()
    }

    /** Checks the actual serialized Activity Bundle, including hidden widget and dialog Savers. */
    private class BundleProbe(private val privateValues: List<String>, private val privateMillis: List<Long> = emptyList()) : Application.ActivityLifecycleCallbacks {
        var savedCount = 0
        var privateValueFound = false
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(outState)
                val bytes = parcel.marshall()
                privateValueFound = privateValueFound || privateValues.any { value ->
                    listOf(Charsets.UTF_8, Charsets.UTF_16LE).any { charset -> contains(bytes, value.toByteArray(charset)) }
                }
                privateValueFound = privateValueFound || privateMillis.any { value ->
                    listOf(java.nio.ByteOrder.LITTLE_ENDIAN, java.nio.ByteOrder.BIG_ENDIAN).any { order -> contains(bytes, java.nio.ByteBuffer.allocate(8).order(order).putLong(value).array()) }
                }
                savedCount++
            } finally { parcel.recycle() }
        }
        override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
        override fun onActivityStarted(a: Activity) = Unit
        override fun onActivityResumed(a: Activity) = Unit
        override fun onActivityPaused(a: Activity) = Unit
        override fun onActivityStopped(a: Activity) = Unit
        override fun onActivityDestroyed(a: Activity) = Unit
        private fun contains(bytes: ByteArray, wanted: ByteArray): Boolean = (0..bytes.size-wanted.size).any { start -> wanted.indices.all { bytes[start+it] == wanted[it] } }
    }
}
