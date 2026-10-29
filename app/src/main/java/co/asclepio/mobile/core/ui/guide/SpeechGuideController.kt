package co.asclepio.mobile.core.ui.guide

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class SpeechGuideState { Idle, Speaking, Paused, Unavailable }

data class GuideVoice(
    val name: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
    val installed: Boolean = true,
)

/** Frontera del motor local. No permite archivos, texto guardado ni síntesis de audio persistida. */
interface SpeechGuideEngine {
    fun initialize(onReady: (Boolean) -> Unit)
    fun voices(): List<GuideVoice>
    fun selectVoice(name: String): Boolean
    fun speak(text: String, utteranceId: String, onDone: () -> Unit, onError: () -> Unit): Boolean
    fun stop()
    fun shutdown()
}

/** Nunca sustituye el idioma solicitado ni elige una voz que requiere red o datos por instalar. */
fun selectOfflineVoice(voices: List<GuideVoice>, localeTag: String): GuideVoice? {
    val requested = Locale.forLanguageTag(localeTag)
    if (requested.language !in setOf("es", "en", "pt", "fr", "de")) return null
    return voices.filter {
        !it.requiresNetwork && it.installed && Locale.forLanguageTag(it.localeTag).language == requested.language
    }.sortedWith(
        compareByDescending<GuideVoice> { Locale.forLanguageTag(it.localeTag) == requested }
            .thenByDescending { requested.country.isNotEmpty() && Locale.forLanguageTag(it.localeTag).country == requested.country }
            .thenBy { it.name },
    ).firstOrNull()
}

/**
 * Narración estática en RAM. Solo recibe un destino cerrado, locale e identidad no privada de navegación.
 * El resolver convierte los ids fijos del destino; no existe una entrada para campos o texto arbitrario.
 * Todos los callbacks se comprueban bajo el mismo lock que invalida generación e instrucción.
 */
class SpeechGuideController(
    private val engineFactory: () -> SpeechGuideEngine,
    private val resolveString: (Int, String) -> String,
) {
    private data class GuideContext(
        val destination: GuideDestination,
        val localeTag: String,
        val sessionIdentity: String,
        val instructions: List<String>,
    )

    private val _state = MutableStateFlow(SpeechGuideState.Idle)
    val state: StateFlow<SpeechGuideState> = _state.asStateFlow()
    private var context: GuideContext? = null
    private var engine: SpeechGuideEngine? = null
    private var generation = 0L
    private var instruction = 0
    private var initialized = false
    private var released = false

    /** Resuelve solo recursos constantes. También invalida si cambia el texto del mismo recurso. */
    @Synchronized
    fun update(destination: GuideDestination, localeTag: String, sessionIdentity: String) {
        if (released) return
        val texts = runCatching { destination.resources.map { resolveString(it, localeTag) } }.getOrDefault(emptyList())
        val next = GuideContext(destination, localeTag, sessionIdentity, texts)
        if (context != next) {
            reset(SpeechGuideState.Idle)
            context = next
        }
    }

    /** Solo se llama desde una acción explícita del usuario. */
    @Synchronized
    fun start() {
        if (!released && context != null) begin(0)
    }

    @Synchronized
    fun pause() {
        if (!released && _state.value == SpeechGuideState.Speaking) reset(SpeechGuideState.Paused, instruction)
    }

    @Synchronized
    fun resume() {
        if (!released && _state.value == SpeechGuideState.Paused) begin(instruction)
    }

    @Synchronized
    fun repeat() = start()

    @Synchronized
    fun stop() {
        if (!released) reset(SpeechGuideState.Idle)
    }

    fun onBackground() = stop()

    @Synchronized
    fun release() {
        if (released) return
        reset(SpeechGuideState.Idle)
        context = null
        released = true
    }

    private fun begin(index: Int) {
        reset(SpeechGuideState.Speaking, index)
        val version = generation
        val configured = context
        if (configured == null || configured.instructions.isEmpty() || configured.instructions.any(String::isBlank)) {
            fail(version)
            return
        }
        val created = runCatching(engineFactory).getOrElse { fail(version); return }
        engine = created
        runCatching { created.initialize { success -> onInitialized(version, created, success) } }
            .onFailure { fail(version) }
    }

    @Synchronized
    private fun onInitialized(version: Long, source: SpeechGuideEngine, success: Boolean) {
        if (!current(version, source) || initialized) return
        initialized = true
        val selected = runCatching {
            if (!success) null else selectOfflineVoice(source.voices(), context!!.localeTag)
        }.getOrNull()
        if (selected == null || !runCatching { source.selectVoice(selected.name) }.getOrDefault(false)) {
            fail(version)
            return
        }
        speakInstruction(version, source)
    }

    private fun speakInstruction(version: Long, source: SpeechGuideEngine) {
        val index = instruction
        val text = context!!.instructions[index]
        val success = runCatching {
            source.speak(
                text,
                "guide-$version-$index",
                onDone = { onDone(version, index, source) },
                onError = { onError(version, index, source) },
            )
        }.getOrDefault(false)
        if (!success) fail(version)
    }

    @Synchronized
    private fun onDone(version: Long, index: Int, source: SpeechGuideEngine) {
        if (!current(version, source) || instruction != index) return
        instruction++
        if (instruction >= context!!.instructions.size) reset(SpeechGuideState.Idle)
        else speakInstruction(version, source)
    }

    @Synchronized
    private fun onError(version: Long, index: Int, source: SpeechGuideEngine) {
        if (current(version, source) && instruction == index) fail(version)
    }

    private fun current(version: Long, source: SpeechGuideEngine): Boolean =
        !released && generation == version && engine === source && _state.value == SpeechGuideState.Speaking

    private fun fail(version: Long) {
        if (generation == version && !released) reset(SpeechGuideState.Unavailable)
    }

    /** Invalida ANTES de stop/shutdown, porque pueden entregar callbacks inmediatamente. */
    private fun reset(state: SpeechGuideState, index: Int = 0) {
        generation++
        val previous = engine
        engine = null
        initialized = false
        instruction = index
        runCatching { previous?.stop() }
        runCatching { previous?.shutdown() }
        _state.value = state
    }
}
