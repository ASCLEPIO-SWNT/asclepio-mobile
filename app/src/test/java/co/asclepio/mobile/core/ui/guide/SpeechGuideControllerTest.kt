package co.asclepio.mobile.core.ui.guide

import co.asclepio.mobile.R
import org.junit.Assert.*
import org.junit.Test

class SpeechGuideControllerTest {
    private val engines = mutableListOf<FakeEngine>()
    private var catalogRevision = "v1"
    private val resolved = mutableListOf<Pair<Int, String>>()
    private fun controller(make: () -> FakeEngine = { FakeEngine() }) = SpeechGuideController(
        engineFactory = { make().also(engines::add) },
        resolveString = { resource, locale -> resolved.add(resource to locale); "$catalogRevision:$locale:$resource" },
    )
    private fun SpeechGuideController.configure(
        destination: GuideDestination = GuideDestination.Welcome,
        locale: String = "es-CO",
        session: String = "entry-Welcome",
    ) = update(destination, locale, session)
    private fun SpeechGuideController.play(): FakeEngine {
        start()
        return engines.last().also { it.ready(true) }
    }

    @Test fun `R11 entrar y configurar no inicializa ni reproduce sin accion explicita`() {
        val guide = controller()
        guide.configure()
        guide.configure()
        assertEquals(SpeechGuideState.Idle, guide.state.value)
        assertTrue(engines.isEmpty())
        guide.start()
        assertEquals(SpeechGuideState.Speaking, guide.state.value)
        assertTrue(engines.single().speeches.isEmpty())
        engines.single().ready(true)
        assertEquals("v1:es-CO:${R.string.guide_welcome_1}", engines.single().speeches.single().text)
    }

    @Test fun `R11 cada instruccion estatica se narra en orden una vez y termina inactiva`() {
        val guide = controller()
        guide.configure(GuideDestination.Home, "de-DE", "session-3")
        val engine = guide.play()
        engine.speeches[0].done()
        assertEquals(listOf("v1:de-DE:${R.string.guide_home_1}", "v1:de-DE:${R.string.guide_home_2}"), engine.speeches.map { it.text })
        engine.speeches[1].done()
        assertEquals(SpeechGuideState.Idle, guide.state.value)
        assertEquals(1, engine.shutdowns)
        assertEquals(1, engines.size)
    }

    @Test fun `R12 solo selecciona voces locales instaladas del idioma y prefiere region exacta`() {
        val voices = listOf(
            GuideVoice("es-network", "es-CO", requiresNetwork = true),
            GuideVoice("es-uninstalled", "es-CO", requiresNetwork = false, installed = false),
            GuideVoice("en-local", "en-US", requiresNetwork = false),
            GuideVoice("es-other", "es-ES", requiresNetwork = false),
            GuideVoice("es-local", "es-CO", requiresNetwork = false),
        )
        assertEquals("es-local", selectOfflineVoice(voices, "es-CO")?.name)
        assertEquals("es-other", selectOfflineVoice(voices, "es-ES")?.name)
        assertNull(selectOfflineVoice(voices, "fr-FR"))
        val guide = controller { FakeEngine().also { it.availableVoices = voices } }
        guide.configure()
        assertEquals("es-local", guide.play().selected)
    }

    @Test fun `R12 inicializacion voz seleccion y speak fallidos dejan indisponibilidad sin fallback`() {
        val cases = listOf<(FakeEngine) -> Unit>(
            { it.initSuccess = false },
            { it.availableVoices = emptyList() },
            { it.availableVoices = listOf(GuideVoice("network", "es-CO", true)) },
            { it.availableVoices = listOf(GuideVoice("foreign", "fr-FR", false)) },
            { it.availableVoices = listOf(GuideVoice("missing", "es-CO", false, false)) },
            { it.selectSuccess = false },
            { it.speakSuccess = false },
        )
        for (change in cases) {
            val guide = controller { FakeEngine().also(change) }
            guide.configure()
            val engine = guide.play()
            assertEquals(SpeechGuideState.Unavailable, guide.state.value)
            assertEquals(1, engine.shutdowns)
            assertTrue(engine.speeches.size <= 1)
            guide.configure(GuideDestination.Profile)
            assertEquals(SpeechGuideState.Idle, guide.state.value)
        }
        val guide = SpeechGuideController({ error("Engine unavailable") }) { _, _ -> "Static" }
        guide.configure()
        guide.start()
        assertEquals(SpeechGuideState.Unavailable, guide.state.value)
    }

    @Test fun `R12 error actual de reproduccion comunica indisponibilidad y no sigue instrucciones`() {
        val guide = controller()
        guide.configure()
        val engine = guide.play()
        engine.speeches.single().error()
        assertEquals(SpeechGuideState.Unavailable, guide.state.value)
        engine.speeches.single().done()
        assertEquals(1, engine.speeches.size)
        assertEquals(1, engine.shutdowns)
    }

    @Test fun `R13 pausa detiene y continua la instruccion interrumpida y repetir comienza por la primera`() {
        val guide = controller()
        guide.configure()
        val first = guide.play()
        first.speeches.single().done()
        val interrupted = first.speeches.last()
        guide.pause()
        assertEquals(SpeechGuideState.Paused, guide.state.value)
        assertEquals(1, first.stops)
        assertEquals(1, first.shutdowns)
        interrupted.done(); interrupted.error()
        assertEquals(SpeechGuideState.Paused, guide.state.value)
        guide.resume()
        val resumed = engines.last().also { it.ready(true) }
        assertEquals(interrupted.text, resumed.speeches.single().text)
        guide.repeat()
        val repeated = engines.last().also { it.ready(true) }
        assertEquals(first.speeches.first().text, repeated.speeches.single().text)
        assertEquals(SpeechGuideState.Speaking, guide.state.value)
    }

    @Test fun `R14 ruta idioma texto y sesion invalidan callbacks y dejan idle sin autoplay`() {
        val updates = listOf<(SpeechGuideController) -> Unit>(
            { it.configure(GuideDestination.Profile) },
            { it.configure(locale = "fr-FR") },
            { it.configure(session = "session-9") },
            { catalogRevision = "v2"; it.configure() },
        )
        for (update in updates) {
            catalogRevision = "v1"
            val guide = controller()
            guide.configure()
            val old = guide.play()
            val callback = old.speeches.single()
            update(guide)
            callback.done(); callback.error(); old.ready(false); old.ready(true)
            assertEquals(SpeechGuideState.Idle, guide.state.value)
            assertEquals(1, old.speeches.size)
            assertEquals(1, old.shutdowns)
        }
    }

    @Test fun `R14 A B A desde activo y fallo no recupera controles errores ni callbacks anteriores`() {
        for (failed in listOf(false, true)) {
            val guide = controller()
            guide.configure()
            val old = guide.play()
            val oldSpeech = old.speeches.single()
            if (failed) oldSpeech.error()
            guide.configure(GuideDestination.Preferences)
            guide.configure()
            assertEquals(SpeechGuideState.Idle, guide.state.value)
            val fresh = guide.play()
            oldSpeech.done(); oldSpeech.error(); old.ready(false)
            assertEquals(SpeechGuideState.Speaking, guide.state.value)
            assertEquals(1, fresh.speeches.size)
        }
    }

    @Test fun `R14 callback de inicializacion viejo no inicia ni falla despues de A B A`() {
        val guide = controller()
        guide.configure()
        guide.start()
        val initializing = engines.last()
        guide.configure(GuideDestination.Preferences)
        guide.configure()
        guide.start()
        val current = engines.last()
        initializing.ready(true); initializing.ready(false)
        assertEquals(SpeechGuideState.Speaking, guide.state.value)
        assertTrue(initializing.speeches.isEmpty())
        current.ready(true)
        assertEquals(1, current.speeches.size)
    }

    @Test fun `R14 repetir en misma ruta ignora fin y error antiguos y callbacks duplicados de instruccion`() {
        val guide = controller()
        guide.configure()
        val previous = guide.play()
        guide.repeat()
        val current = engines.last().also { it.ready(true) }
        previous.speeches.single().done(); previous.speeches.single().error()
        assertEquals(SpeechGuideState.Speaking, guide.state.value)
        current.speeches.first().done()
        current.speeches.first().done(); current.speeches.first().error()
        assertEquals(SpeechGuideState.Speaking, guide.state.value)
        assertEquals(2, current.speeches.size)
        current.speeches.last().done()
        assertEquals(SpeechGuideState.Idle, guide.state.value)
    }

    @Test fun `R14 background stop y release cancelan y nunca vuelven a reproducir automaticamente`() {
        val guide = controller()
        guide.configure()
        val old = guide.play()
        guide.onBackground()
        old.speeches.single().done(); old.speeches.single().error()
        assertEquals(SpeechGuideState.Idle, guide.state.value)
        assertEquals(1, engines.size)
        val active = guide.play()
        guide.stop()
        active.speeches.single().done()
        assertEquals(SpeechGuideState.Idle, guide.state.value)
        val last = guide.play()
        guide.release()
        guide.start(); guide.resume(); guide.repeat(); guide.configure(GuideDestination.Profile)
        last.speeches.single().error()
        assertEquals(SpeechGuideState.Idle, guide.state.value)
        assertEquals(3, engines.size)
        assertEquals(1, last.shutdowns)
    }

    @Test fun `R20 toda entrada narrable procede solo del catalogo estatico sin argumentos privados ni archivos`() {
        val guide = controller()
        for (destination in GuideDestination.entries) {
            guide.configure(destination, "pt-BR", "session-4")
            val engine = guide.play()
            engine.speeches.first().done()
            engine.speeches.last().done()
            assertTrue(engine.speeches.all { it.text.startsWith("v1:pt-BR:") })
            assertEquals(destination.resources.size, engine.speeches.size)
            assertEquals(SpeechGuideState.Idle, guide.state.value)
        }
        val expected = GuideDestination.entries.flatMap { it.resources }.map { it to "pt-BR" }.toSet()
        assertEquals(expected, resolved.toSet())
        assertTrue(resolved.all { (_, locale) -> locale == "pt-BR" })
        assertFalse(SpeechGuideEngine::class.java.methods.any { it.name.contains("file", ignoreCase = true) || it.name.contains("synthesize", ignoreCase = true) })
        assertFalse(SpeechGuideController::class.java.methods.any { it.name == "speak" || it.name == "setText" })
    }

    private class FakeEngine : SpeechGuideEngine {
        var initSuccess = true
        var selectSuccess = true
        var speakSuccess = true
        var availableVoices = listOf(GuideVoice("local", "es-CO", false), GuideVoice("de", "de-DE", false), GuideVoice("pt", "pt-BR", false))
        var selected: String? = null
        var stops = 0
        var shutdowns = 0
        val speeches = mutableListOf<Speech>()
        private lateinit var initialized: (Boolean) -> Unit
        override fun initialize(onReady: (Boolean) -> Unit) { initialized = onReady }
        fun ready(success: Boolean) = initialized(success && initSuccess)
        override fun voices() = availableVoices
        override fun selectVoice(name: String): Boolean { selected = name; return selectSuccess }
        override fun speak(text: String, utteranceId: String, onDone: () -> Unit, onError: () -> Unit): Boolean {
            speeches.add(Speech(text, utteranceId, onDone, onError))
            return speakSuccess
        }
        override fun stop() { stops++ }
        override fun shutdown() { shutdowns++ }
    }

    private data class Speech(val text: String, val id: String, val done: () -> Unit, val error: () -> Unit)
}
