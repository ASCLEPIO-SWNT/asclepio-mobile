package co.asclepio.mobile.core.voice

/**
 * Reconocedor falso para la JVM: registra las llamadas y deja disparar los callbacks de cada escucha,
 * también tarde (después de cancelar o de una escucha nueva).
 */
class FakeOnDeviceRecognizer(
    /** `null` deja la comprobación pendiente hasta [answerAvailability]. */
    private val availability: OnDeviceAvailability? = OnDeviceAvailability.Available("es-CO"),
) : OnDeviceRecognizer {
    class Listen(val languageTag: String, val onFinal: (String) -> Unit, val onError: (DictationError) -> Unit)

    val calls = mutableListOf<String>()
    val listens = mutableListOf<Listen>()
    private var pendingAvailability: ((OnDeviceAvailability) -> Unit)? = null

    val lastListen: Listen get() = listens.last()

    override fun checkAvailability(onResult: (OnDeviceAvailability) -> Unit) {
        calls += "checkAvailability"
        if (availability == null) pendingAvailability = onResult else onResult(availability)
    }

    fun answerAvailability(value: OnDeviceAvailability) = checkNotNull(pendingAvailability).invoke(value)

    override fun start(languageTag: String, onFinal: (String) -> Unit, onError: (DictationError) -> Unit) {
        calls += "start:$languageTag"
        listens += Listen(languageTag, onFinal, onError)
    }

    override fun stop() {
        calls += "stop"
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun release() {
        calls += "release"
    }
}
