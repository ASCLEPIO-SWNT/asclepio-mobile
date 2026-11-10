package co.asclepio.mobile

import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.asclepio.mobile.core.ui.guide.AndroidSpeechGuideEngine
import co.asclepio.mobile.core.ui.guide.GuideDestination
import co.asclepio.mobile.core.ui.guide.GuideVoice
import co.asclepio.mobile.core.ui.guide.SpeechGuideController
import co.asclepio.mobile.core.ui.guide.SpeechGuideEngine
import co.asclepio.mobile.core.ui.guide.SpeechGuideState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Consulta el motor Android real. Una voz ausente comprueba degradación, nunca omite la prueba. */
@RunWith(AndroidJUnit4::class)
class AndroidSpeechGuideEngineInstrumentedTest {
    @Test
    fun `R12 motor real selecciona solo voz local instalada o comunica indisponibilidad sin fallback`() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("es"))
        }
        val spanishContext = context.createConfigurationContext(configuration)
        val allowedTexts = setOf(
            spanishContext.getString(R.string.guide_patient_login_1),
            spanishContext.getString(R.string.guide_patient_login_2),
        )
        lateinit var probe: RealEngineProbe
        lateinit var guide: SpeechGuideController
        instrumentation.runOnMainSync {
            probe = RealEngineProbe(AndroidSpeechGuideEngine(context))
            guide = SpeechGuideController({ probe }) { id, _ -> spanishContext.getString(id) }
            guide.update(GuideDestination.PatientLogin, "es", "instrumented-entry")
            assertEquals(SpeechGuideState.Idle, guide.state.value)
            assertEquals(0, probe.initializations.get())
            assertEquals(0, probe.spokenTexts.size)
            guide.start()
        }
        try {
            val result = withTimeout(45_000) {
                guide.state.first { it == SpeechGuideState.Idle || it == SpeechGuideState.Unavailable }
            }
            assertEquals(1, probe.initializations.get())
            assertTrue(probe.selectedVoices.size <= 1)
            probe.selectedVoices.forEach {
                assertFalse(it.requiresNetwork)
                assertTrue(it.installed)
                assertEquals("es", Locale.forLanguageTag(it.localeTag).language)
            }
            assertTrue(probe.spokenTexts.all { it in allowedTexts })
            if (result == SpeechGuideState.Idle) {
                assertEquals(true, probe.initialized)
                assertEquals(true, probe.selected)
                assertEquals(1, probe.selectedVoices.size)
                assertEquals(2, probe.spokenTexts.size)
            } else if (probe.spokenTexts.isNotEmpty()) {
                // Un fallo de reproducción también debe proceder de una selección local real aceptada.
                assertEquals(true, probe.selected)
                assertEquals(1, probe.selectedVoices.size)
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("tts_initialization", probe.initialized.toString())
                putInt("tts_available_voices", probe.available.size)
                putInt("tts_installed_offline_spanish_voices", probe.available.count {
                    it.installed && !it.requiresNetwork && Locale.forLanguageTag(it.localeTag).language == "es"
                })
                putString("tts_selected_offline_voice", probe.selectedVoices.singleOrNull()?.name ?: "none")
                putString("tts_native_selection_accepted", probe.selected.toString())
                putInt("tts_static_instructions_submitted", probe.spokenTexts.size)
                putString("tts_guide_result", result.name)
            })
        } finally {
            instrumentation.runOnMainSync { guide.release() }
        }
    }

    private class RealEngineProbe(private val real: SpeechGuideEngine) : SpeechGuideEngine {
        val initializations = AtomicInteger()
        @Volatile var initialized: Boolean? = null
        @Volatile var selected: Boolean? = null
        val available = CopyOnWriteArrayList<GuideVoice>()
        val selectedVoices = CopyOnWriteArrayList<GuideVoice>()
        val spokenTexts = CopyOnWriteArrayList<String>()
        override fun initialize(onReady: (Boolean) -> Unit) {
            initializations.incrementAndGet()
            real.initialize { success -> initialized = success; onReady(success) }
        }
        override fun voices(): List<GuideVoice> = real.voices().also { available.addAll(it) }
        override fun selectVoice(name: String): Boolean {
            selectedVoices.add(available.single { it.name == name })
            return real.selectVoice(name).also { selected = it }
        }
        override fun speak(text: String, utteranceId: String, onDone: () -> Unit, onError: () -> Unit): Boolean {
            spokenTexts.add(text)
            return real.speak(text, utteranceId, onDone, onError)
        }
        override fun stop() = real.stop()
        override fun shutdown() = real.shutdown()
    }
}
