package co.asclepio.mobile.feature.voice

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.core.voice.FakeOnDeviceRecognizer
import co.asclepio.mobile.feature.encounters.ui.PreRegistrationViewModel
import co.asclepio.mobile.feature.encounters.ui.SitesUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * D8 (enmienda 2026-11-17): la raíz detiene la guía **vigente** al pulsar "Dictar". El ViewModel sobrevive a la
 * recreación de la composición (rotación, idioma) y la guía no, así que el callback se construye en cada composición.
 */
class DictationGuideExclusionTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    @Test fun `R13 tras recrear la guia Dictar detiene la guia actual`() = runTest {
        env.login()
        env.enqueue(200, PatientTestEnv.tenants())
        val recognizer = FakeOnDeviceRecognizer()
        // Un único ViewModel para las dos composiciones, como el del ViewModelStore de la entrada.
        val vm = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager, recognizer)
        vm.state.first { it.sites !is SitesUiState.Loading }
        env.server.takeRequest()

        // Primera composición: su guía y su callback de "Dictar".
        val firstGuideStop: () -> Unit = { recognizer.calls += "firstGuide.stop" }
        @Suppress("UNUSED_VARIABLE")
        val firstOnStart: () -> Unit = { startDictationStoppingGuide(firstGuideStop, vm::startDictation) }
        // Rotación o cambio de idioma: guía nueva y callback reconstruido con ella.
        val currentGuideStop: () -> Unit = { recognizer.calls += "currentGuide.stop" }
        val currentOnStart: () -> Unit = { startDictationStoppingGuide(currentGuideStop, vm::startDictation) }

        currentOnStart()

        assertEquals(listOf("checkAvailability", "currentGuide.stop", "start:es-CO"), recognizer.calls)
    }
}
