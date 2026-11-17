package co.asclepio.mobile.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationTypesTest {
    @Test fun `R12 los estados y disponibilidades del dictado no contienen texto reconocido en toString`() {
        val recognized = "me duele el pecho"
        val values: List<Any> = listOf(
            DictationUiState.Checking, DictationUiState.Unavailable, DictationUiState.Idle,
            DictationUiState.Listening, DictationUiState.Processing,
            OnDeviceAvailability.Available("es-CO"), OnDeviceAvailability.Unavailable,
        ) + DictationError.entries
        for (value in values) assertFalse(value.toString(), value.toString().contains(recognized))
        // Ningún tipo del puerto guarda texto: solo la etiqueta de idioma de la disponibilidad.
        assertEquals("Available(languageTag=es-CO)", OnDeviceAvailability.Available("es-CO").toString())
    }

    @Test fun `R12 NoDictation informa no disponible y no inicia escuchas`() {
        var availability: OnDeviceAvailability? = null
        NoDictation.checkAvailability { availability = it }
        assertEquals(OnDeviceAvailability.Unavailable, availability)
        var callbacks = 0
        NoDictation.start("es-CO", onFinal = { callbacks++ }, onError = { callbacks++ })
        NoDictation.stop()
        NoDictation.cancel()
        NoDictation.release()
        assertEquals(0, callbacks)
        assertTrue(NoDictation is OnDeviceRecognizer)
    }
}
