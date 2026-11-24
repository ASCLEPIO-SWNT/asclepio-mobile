package co.asclepio.mobile.feature.interview

import co.asclepio.mobile.BuildConfig
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.OutputValidator
import co.asclepio.mobile.core.interview.Turn
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Versión del prompt local: copia literal de `../asclepio-api/ai/prompts/interview-v2.md` (R9 enmendado). */
const val INTERVIEW_PROMPT_ASSET = "prompts/interview-v2.md"

/** Quita el comentario HTML inicial, que no se envía al modelo (igual que `loadPrompt` de la API). */
fun stripPromptComment(raw: String): String = raw.replace(Regex("""^\s*<!--[\s\S]*?-->\s*"""), "").trim()

/**
 * Mensaje del usuario para el modelo, con la misma forma que el fallback del servidor:
 * `{ chief_complaint_text, turns: [{ dimension, question, answer }] }`. Se queda en el teléfono (R15).
 */
fun interviewUserMessage(complaint: String, turns: List<Turn>): String = buildJsonObject {
    put("chief_complaint_text", complaint)
    put(
        "turns",
        buildJsonArray {
            turns.forEach { t ->
                add(
                    buildJsonObject {
                        put("dimension", t.dimension.name)
                        put("question", t.question)
                        put("answer", t.answer?.let(::JsonPrimitive) ?: JsonNull)
                    },
                )
            }
        },
    )
}.toString()

/** Medidas de una generación local (R18): solo números, nunca contenido. */
data class GenerationSample(val ttftMs: Long?, val durationMs: Long)

/** Registro en memoria de las generaciones, solo en compilaciones de depuración (R18; lo lee la evaluación D11). */
object DebugGenerationMetrics {
    private val samples = mutableListOf<GenerationSample>()

    @Synchronized fun record(sample: GenerationSample) {
        if (BuildConfig.DEBUG) samples += sample
    }

    @Synchronized fun snapshot(): List<GenerationSample> = samples.toList()

    @Synchronized fun clear() = samples.clear()
}

/**
 * Motor local (D1, D7): LiteRT-LM con Gemma 4 E2B-it. Backend GPU con respaldo en CPU, una `Conversation` nueva por
 * pregunta con el prompt `interview-v2` y `ResponseFormat.json` con el esquema del validador (R9). No usa red: el
 * modelo se lee de `filesDir/models/` (R15). Devuelve la salida cruda: la valida [co.asclepio.mobile.core.interview.InterviewSession].
 * La cancelación (R17) llama a `cancelProcess()`. [close] libera el `Engine` (D7).
 */
class LiteRtInterviewer(
    private val modelPath: String,
    private val cacheDir: String,
    private val systemPrompt: String,
    private val clock: () -> Long = System::nanoTime,
) : PreparableInterviewer {
    override val engine = InterviewEngine.ON_DEVICE
    override val modelId: String = BuildConfig.MODEL_ID

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var init: Deferred<Engine>? = null

    /**
     * Arranca el `Engine` fuera de la corrutina que espera: si [DeviceCapability] deja de esperar a los 20 s, el arranque
     * nativo termina en segundo plano y se libera en [close].
     */
    override suspend fun initialize() {
        val deferred = init ?: scope.async { createEngine() }.also { init = it }
        deferred.await()
    }

    private fun createEngine(): Engine {
        val gpu = runCatching { Engine(EngineConfig(modelPath, Backend.GPU(), cacheDir = cacheDir)).also { it.initialize() } }
        return gpu.getOrElse { Engine(EngineConfig(modelPath, Backend.CPU(), cacheDir = cacheDir)).also { it.initialize() } }
    }

    override suspend fun next(complaint: String, turns: List<Turn>): NextStep {
        val engine = checkNotNull(init?.takeIf { it.isCompleted }?.await()) { "Engine not initialized" }
        val config = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
            enableResponseFormat = true,
        )
        val conversation = engine.createConversation(config)
        try {
            val start = clock()
            var firstToken: Long? = null
            val raw = suspendCancellableCoroutine { cont ->
                val text = StringBuilder()
                conversation.sendMessageAsync(
                    interviewUserMessage(complaint, turns),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            if (firstToken == null) firstToken = clock()
                            message.contents.contents.filterIsInstance<Content.Text>().forEach { text.append(it.text) }
                        }

                        override fun onDone() {
                            if (cont.isActive) cont.resume(text.toString())
                        }

                        override fun onError(throwable: Throwable) {
                            if (cont.isActive) cont.resumeWithException(throwable)
                        }
                    },
                    responseFormat = ResponseFormat.json(OutputValidator.SCHEMA),
                )
                cont.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
            }
            val end = clock()
            DebugGenerationMetrics.record(GenerationSample(firstToken?.let { (it - start) / 1_000_000 }, (end - start) / 1_000_000))
            return NextStep.ModelOutput(raw)
        } finally {
            runCatching { conversation.close() }
        }
    }

    /** D7: libera el `Engine` en cuanto se sale de la entrevista (también si el arranque sigue en curso). */
    override fun close() {
        val pending = init ?: return
        init = null
        // Sin cancelar: el arranque nativo no se interrumpe; el Engine se cierra en cuanto exista.
        pending.invokeOnCompletion { error -> if (error == null) runCatching { pending.getCompleted().close() } }
    }
}
