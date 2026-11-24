package co.asclepio.mobile.feature.interview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.interview.EndedBy
import co.asclepio.mobile.core.interview.InterviewLimits
import co.asclepio.mobile.core.interview.InterviewResult
import co.asclepio.mobile.core.interview.InterviewSession
import co.asclepio.mobile.core.interview.InterviewState
import co.asclepio.mobile.core.interview.RedFlag
import co.asclepio.mobile.core.interview.RedFlagRules
import co.asclepio.mobile.core.interview.SymptomInterviewDto
import co.asclepio.mobile.core.interview.SymptomInterviewer
import co.asclepio.mobile.core.interview.Turn
import co.asclepio.mobile.core.voice.DictationError
import co.asclepio.mobile.core.voice.DictationUiState
import co.asclepio.mobile.core.voice.NoDictation
import co.asclepio.mobile.core.voice.OnDeviceAvailability
import co.asclepio.mobile.core.voice.OnDeviceRecognizer
import co.asclepio.mobile.feature.interview.DeviceCapability
import co.asclepio.mobile.feature.interview.EngineChoice
import co.asclepio.mobile.feature.interview.PreparableInterviewer
import co.asclepio.mobile.feature.interview.selectEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Resultado de confirmar el resumen. */
sealed interface SummaryDecision {
    data class Send(val interview: SymptomInterviewDto?) : SummaryDecision

    /** Una respuesta editada tiene una señal: la pantalla de alarma envía de inmediato. */
    data object Escalated : SummaryDecision
}

/** Dependencias de la entrevista, inyectables para la JVM (D4). */
class InterviewDependencies(
    val capability: DeviceCapability,
    val noticeSeen: suspend () -> Boolean,
    val markNoticeSeen: suspend () -> Unit,
    val localInterviewer: () -> PreparableInterviewer,
    val serverInterviewer: () -> SymptomInterviewer,
    /** R1 desde el prerregistro: encola la descarga del modelo. */
    val downloadModel: () -> Unit = {},
    val modelVerified: () -> Boolean = { false },
)

/** Estados de la pantalla. Ninguno lleva texto del modelo sin validar (R10). */
sealed interface InterviewUiState {
    /** Eligiendo motor o arrancando el `Engine`. */
    data object Preparing : InterviewUiState

    /** R6: aviso del motor del servidor (PA-F11-3) con "Continuar" y "Omitir asistente". */
    data class ServerNotice(val canDownload: Boolean, val downloadRequested: Boolean = false) : InterviewUiState

    /** R8: una pregunta validada o fija, con su borrador de respuesta (solo en RAM). */
    data class Asking(
        val question: String,
        val number: Int,
        val answer: String = "",
        val generating: Boolean = false,
    ) : InterviewUiState {
        val canSend: Boolean get() = !generating && answer.isNotBlank()

        override fun toString() = "Asking(question=***, number=$number, answer=***, generating=$generating)"
    }

    /** R14: resumen editable antes de enviar; `unavailable` si el motor del servidor falló (R7). */
    data class Summary(val result: InterviewResult, val answers: List<String?>) : InterviewUiState {
        override fun toString() = "Summary(endedBy=${result.endedBy}, turns=${answers.size})"
    }

    /** R12: "Acude ya a urgencias o llama al 123"; [interview] se envía de inmediato. */
    data class RedFlag(val interview: SymptomInterviewDto) : InterviewUiState

    /** El paciente omitió el asistente: el prerregistro se envía sin entrevista. */
    data object Skipped : InterviewUiState
}

/**
 * Entrevista de síntomas (R6–R14, R17). La [InterviewSession] guarda los turnos en RAM y sobrevive a la rotación;
 * no se guardan en el estado serializable de navegación ni en disco (borradores privados, PrivacyTest R32; ver
 * progress/impl_F-11-mobile.md).
 * Al pasar a `ON_STOP` se cancela la generación ([onStop]) y al volver se regenera ([onStart]). El motor se libera al
 * terminar y en [onCleared] (D7). El dictado de la respuesta es el mismo reconocedor local de F-08.
 */
class InterviewViewModel(
    private val complaint: String,
    private val deps: InterviewDependencies,
    private val recognizer: OnDeviceRecognizer = NoDictation,
) : ViewModel() {
    private val _state = MutableStateFlow<InterviewUiState>(InterviewUiState.Preparing)
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private val _dictation = MutableStateFlow<DictationUiState>(DictationUiState.Checking)
    val dictation: StateFlow<DictationUiState> = _dictation.asStateFlow()

    private var session: InterviewSession? = null
    private var interviewer: SymptomInterviewer? = null
    private var job: Job? = null
    private var stopped = false
    private var languageTag: String? = null
    private var listenGeneration = 0L

    init {
        recognizer.checkAvailability { availability ->
            languageTag = (availability as? OnDeviceAvailability.Available)?.languageTag
            _dictation.value = if (languageTag != null) DictationUiState.Idle else DictationUiState.Unavailable
        }
        job = viewModelScope.launch { chooseEngine() }
    }

    /** R6: local si el equipo es capaz y el `Engine` arranca en ≤20 s; si no, el servidor con el aviso una vez. */
    private suspend fun chooseEngine() {
        if (deps.capability.isCapable()) {
            val local = deps.localInterviewer()
            if (deps.capability.initialize { local.initialize() }) {
                begin(local)
                return
            }
            // Arranque fallido o de más de 20 s: el equipo deja de ser capaz en esta sesión.
            local.close()
        }
        when (selectEngine(deps.capability.isCapable(), deps.noticeSeen())) {
            EngineChoice.SERVER_NOTICE -> _state.value = InterviewUiState.ServerNotice(canDownload = deps.capability.hasEnoughMemory() && !deps.modelVerified())
            EngineChoice.SERVER, EngineChoice.LOCAL -> begin(deps.serverInterviewer())
        }
    }

    /** "Continuar" en el aviso del servidor (R6): se marca visto y empieza la entrevista en el servidor. */
    fun acceptServerNotice() {
        if (_state.value !is InterviewUiState.ServerNotice) return
        _state.value = InterviewUiState.Preparing
        job = viewModelScope.launch {
            deps.markNoticeSeen()
            begin(deps.serverInterviewer())
        }
    }

    /** R1 desde el prerregistro: el paciente acepta descargar el asistente (por Wi-Fi) para la próxima vez. */
    fun downloadModel() {
        val notice = _state.value as? InterviewUiState.ServerNotice ?: return
        deps.downloadModel()
        _state.value = notice.copy(downloadRequested = true)
    }

    /** "Omitir asistente" (R6, F-11.AC1). */
    fun skip() {
        job?.cancel()
        cancelDictation()
        release()
        _state.value = InterviewUiState.Skipped
    }

    private suspend fun begin(engine: SymptomInterviewer) {
        interviewer = engine
        val s = InterviewSession(complaint, engine)
        session = s
        _state.value = InterviewUiState.Preparing
        publish(s.advance())
    }

    private fun publish(state: InterviewState) {
        when (state) {
            InterviewState.Generating -> _state.update { (it as? InterviewUiState.Asking)?.copy(generating = true) ?: it }
            is InterviewState.Asking -> _state.value = InterviewUiState.Asking(state.question, state.number)
            is InterviewState.Ended -> end(state.result)
        }
    }

    private fun end(result: InterviewResult) {
        release()
        _state.value = if (result.endedBy == EndedBy.RED_FLAG) {
            InterviewUiState.RedFlag(checkNotNull(result.toDto()))
        } else {
            InterviewUiState.Summary(result, result.turns.map { it.answer })
        }
    }

    fun updateAnswer(text: String) = _state.update {
        (it as? InterviewUiState.Asking)?.takeIf { a -> !a.generating }?.copy(answer = text.take(InterviewLimits.TURN_ANSWER_MAX)) ?: it
    }

    /** "Siguiente" (R8) con la respuesta escrita o dictada. */
    fun next() {
        val asking = _state.value as? InterviewUiState.Asking ?: return
        if (asking.canSend) answer(asking.answer)
    }

    /** "No sé" (R8): `answer = null`. */
    fun dontKnow() {
        val asking = _state.value as? InterviewUiState.Asking ?: return
        if (!asking.generating) answer(null)
    }

    private fun answer(text: String?) {
        val s = session ?: return
        cancelDictation()
        _state.update { (it as InterviewUiState.Asking).copy(generating = true) }
        // R11: las reglas se aplican dentro de la sesión, antes de pedir la siguiente pregunta.
        job = viewModelScope.launch { publish(s.answer(text)) }
    }

    /** "Terminar" (R8): fin por PATIENT con lo respondido. */
    fun finish() {
        val s = session ?: return
        if (_state.value !is InterviewUiState.Asking) return
        job?.cancel()
        cancelDictation()
        publish(s.finish())
    }

    /** R17: la pantalla pasa a `ON_STOP`; se cancela la generación en curso (los turnos quedan en la sesión). */
    fun onStop() {
        stopped = true
        cancelDictation()
        if (session?.state == InterviewState.Generating) job?.cancel()
    }

    /** R17: al volver, se regenera la pregunta actual si se había cancelado. */
    fun onStart() {
        if (!stopped) return
        stopped = false
        val s = session ?: return
        if (s.state == InterviewState.Generating && job?.isActive != true) {
            job = viewModelScope.launch { publish(s.advance()) }
        }
    }

    // ---------------------------------------------------------------- Resumen (R14)

    fun editAnswer(index: Int, text: String) = _state.update { s ->
        val summary = s as? InterviewUiState.Summary ?: return@update s
        if (index !in summary.answers.indices) return@update s
        summary.copy(answers = summary.answers.toMutableList().also { it[index] = text.take(InterviewLimits.TURN_ANSWER_MAX) })
    }

    /**
     * Confirma el resumen: envía la entrevista con las respuestas editadas (o ninguna si el motor falló sin turnos, R7).
     * Si una respuesta editada tiene una señal de alarma, pasa a [InterviewUiState.RedFlag] (R11, R12).
     */
    fun confirmSummary(): SummaryDecision? {
        val summary = _state.value as? InterviewUiState.Summary ?: return null
        val turns = summary.result.turns.mapIndexed { i, t -> Turn(t.dimension, t.question, summary.answers[i]?.trim()?.takeIf { it.isNotEmpty() }) }
        val flags: List<RedFlag> = RedFlagRules.detect(complaint, turns)
        val result = summary.result.copy(turns = turns)
        if (flags.isNotEmpty()) {
            val dto = checkNotNull(result.copy(endedBy = EndedBy.RED_FLAG, redFlags = flags, assistantUnavailable = false).toDto())
            _state.value = InterviewUiState.RedFlag(dto)
            return SummaryDecision.Escalated
        }
        return SummaryDecision.Send(result.toDto())
    }

    // ---------------------------------------------------------------- Dictado de la respuesta (F-08, R8)

    fun startDictation() {
        val tag = languageTag ?: return
        val asking = _state.value as? InterviewUiState.Asking ?: return
        if (asking.generating || _dictation.value != DictationUiState.Idle) return
        val generation = ++listenGeneration
        _dictation.value = DictationUiState.Listening
        recognizer.start(tag, onFinal = { onDictationResult(generation, it) }, onError = { onDictationError(generation, it) })
    }

    fun stopDictation() {
        if (_dictation.value != DictationUiState.Listening) return
        recognizer.stop()
        _dictation.value = DictationUiState.Processing
    }

    fun cancelDictation() {
        val d = _dictation.value
        if (d != DictationUiState.Listening && d != DictationUiState.Processing) return
        listenGeneration++
        recognizer.cancel()
        _dictation.value = DictationUiState.Idle
    }

    private fun onDictationResult(generation: Long, text: String) {
        if (generation != listenGeneration) return
        listenGeneration++
        _dictation.value = DictationUiState.Idle
        val accepted = text.trim()
        if (accepted.isNotEmpty()) {
            _state.update { s ->
                val a = s as? InterviewUiState.Asking ?: return@update s
                val sep = if (a.answer.isEmpty() || a.answer.last().isWhitespace()) "" else " "
                a.copy(answer = (a.answer + sep + accepted).take(InterviewLimits.TURN_ANSWER_MAX))
            }
        }
    }

    private fun onDictationError(generation: Long, error: DictationError) {
        if (generation != listenGeneration) return
        listenGeneration++
        if (error == DictationError.LanguageUnavailable) languageTag = null
        _dictation.value = if (languageTag == null) DictationUiState.Unavailable else DictationUiState.Idle
    }

    fun onDictationPermissionDenied() = Unit

    private fun release() {
        interviewer?.close()
        interviewer = null
    }

    /** D7: al salir de la entrevista se libera el `Engine` y el reconocedor. */
    override fun onCleared() {
        listenGeneration++
        recognizer.release()
        release()
    }
}
