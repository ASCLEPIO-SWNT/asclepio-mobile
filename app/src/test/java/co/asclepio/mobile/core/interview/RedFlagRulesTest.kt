package co.asclepio.mobile.core.interview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** Reglas deterministas de alarma (F-11-mobile T2) contra el fixture compartido con F-11-api (D5 de ADR 0010). */
class RedFlagRulesTest {
    private val fixtureBytes: ByteArray =
        checkNotNull(javaClass.getResource("/red-flag-cases.json")) { "Falta red-flag-cases.json" }.readBytes()

    private data class Case(val text: String, val expected: List<RedFlag>)

    private val cases: List<Case> = Json.parseToJsonElement(fixtureBytes.decodeToString()).jsonArray.map { element ->
        val o = element.jsonObject
        Case(o.getValue("text").jsonPrimitive.content, o.getValue("expected").jsonArray.map { RedFlag.valueOf(it.jsonPrimitive.content) })
    }

    @Test fun `R11 el fixture es copia literal del de la API (SHA-256 fijado)`() {
        val hex = MessageDigest.getInstance("SHA-256").digest(fixtureBytes).joinToString("") { "%02x".format(it) }
        assertEquals("2d75bdbd0ba08e8e1c9d29ccc40c94ab8c9ca003c34ee8c642409c4da83fe6b7", hex)
    }

    @Test fun `R11 las reglas aciertan el 100 por ciento del fixture compartido`() {
        assertTrue("El fixture debe traer casos con y sin alarma", cases.size >= 60 && cases.any { it.expected.isEmpty() })
        val failures = cases.filter { RedFlagRules.detect(it.text) != it.expected }.map { "${it.text} -> ${RedFlagRules.detect(it.text)}" }
        assertEquals(emptyList<String>(), failures)
    }

    @Test fun `R11 las senales se detectan en el motivo y en cualquier respuesta sin repetirse y en orden`() {
        assertEquals(
            listOf(RedFlag.CHEST_PAIN, RedFlag.LOSS_OF_CONSCIOUSNESS),
            RedFlagRules.detect("Me desmayé ayer", "Tengo dolor en el pecho", "otra vez me desmayé"),
        )
        assertEquals(emptyList<RedFlag>(), RedFlagRules.detect("Dolor de cabeza", "Desde ayer"))
    }

    @Test fun `R11 la normalizacion quita tildes mayusculas y espacios repetidos`() {
        assertEquals("dolor toracico fuerte", RedFlagRules.normalize("  DOLOR   Torácico fuerte "))
        assertEquals("hacerme dano", RedFlagRules.normalize("Hacerme daño"))
    }

    @Test fun `R11 los patrones son exactamente los de RED_FLAG_PATTERNS por senal`() {
        assertEquals(RedFlag.entries.toSet(), RedFlagRules.PATTERNS.keys)
        val counts = RedFlagRules.PATTERNS.mapValues { it.value.size }
        assertEquals(
            mapOf(
                RedFlag.CHEST_PAIN to 3, RedFlag.BREATHING_DIFFICULTY to 6, RedFlag.LOSS_OF_CONSCIOUSNESS to 4,
                RedFlag.SEIZURE to 2, RedFlag.SEVERE_BLEEDING to 6, RedFlag.STROKE_SIGNS to 9, RedFlag.SUICIDAL_IDEATION to 7,
            ),
            counts,
        )
    }

    @Test fun `R11 mentionsPain detecta dolor duele y doler normalizados`() {
        assertTrue(RedFlagRules.mentionsPain("Me DUELE la espalda"))
        assertTrue(RedFlagRules.mentionsPain("dolores fuertes"))
        assertFalse(RedFlagRules.mentionsPain("fiebre y tos"))
    }

    @Test fun `R11 merge conserva las de base y anade las nuevas sin repetir`() {
        assertEquals(
            listOf(RedFlag.SEIZURE, RedFlag.CHEST_PAIN),
            RedFlagRules.merge(listOf(RedFlag.SEIZURE), listOf(RedFlag.CHEST_PAIN, RedFlag.SEIZURE)),
        )
    }
}
