package co.asclepio.mobile.feature.voice

import android.speech.SpeechRecognizer
import co.asclepio.mobile.core.voice.DictationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpanishOnDeviceSupportTest {
    @Test fun `R1 API 31 y 32 usan solo isOnDeviceRecognitionAvailable`() {
        assertEquals(RecognitionStrategy.ServiceOnly, recognitionStrategy(31))
        assertEquals(RecognitionStrategy.ServiceOnly, recognitionStrategy(32))
    }

    @Test fun `R1 API 33 o mas consulta checkRecognitionSupport`() {
        for (sdk in listOf(33, 34, 36, 37)) assertEquals(RecognitionStrategy.CheckSupport, recognitionStrategy(sdk))
    }

    @Test fun `R1 prefiere es-CO luego es luego otra variante es`() {
        assertEquals("es-CO", pickSpanishTag(listOf("en-US", "es-ES", "es", "es-CO")))
        assertEquals("es", pickSpanishTag(listOf("es-MX", "es", "pt-BR")))
        assertEquals("es-MX", pickSpanishTag(listOf("en-US", "es-MX", "es-ES")))
        assertEquals("es-US", pickSpanishTag(listOf("es-US")))
    }

    @Test fun `R1 sin variante es instalada no hay idioma`() {
        assertNull(pickSpanishTag(emptyList()))
        assertNull(pickSpanishTag(listOf("en-US", "pt-BR", "fr-FR", "de-DE", "est", "eu-ES")))
    }

    @Test fun `R7 los errores de idioma se traducen a LanguageUnavailable`() {
        assertEquals(DictationError.LanguageUnavailable, mapRecognizerError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals(DictationError.LanguageUnavailable, mapRecognizerError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertEquals(DictationError.NoMatch, mapRecognizerError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(DictationError.NoMatch, mapRecognizerError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(DictationError.Busy, mapRecognizerError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        for (code in listOf(SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER, -1)) {
            assertEquals(DictationError.Other, mapRecognizerError(code))
        }
    }

    @Test fun `R4 el error de permisos se traduce a Permission`() {
        assertEquals(DictationError.Permission, mapRecognizerError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    @Test fun `R3 sin permiso la accion es solicitarlo y con permiso es escuchar`() {
        assertEquals(DictationAction.RequestPermission, dictationAction(hasPermission = false))
        assertEquals(DictationAction.Listen, dictationAction(hasPermission = true))
    }
}
