package co.asclepio.mobile.core.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    private val source = """
        # Consentimiento informado
        ## 1. Responsable
        Asclepio trata tus datos con **finalidad asistencial** y según la **Ley 1581**.
        - Derecho a conocer
        - Derecho a **rectificar**
        1. Primero
        2. Segundo
    """.trimIndent()

    private fun boldRanges(text: androidx.compose.ui.text.AnnotatedString) =
        text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.text.substring(it.start, it.end) }

    @Test fun `R6 convierte titulos, negrita y listas a estilo sin mostrar los simbolos`() {
        val result = markdownToAnnotated(source)
        assertEquals(
            """
            Consentimiento informado
            1. Responsable
            Asclepio trata tus datos con finalidad asistencial y según la Ley 1581.
            • Derecho a conocer
            • Derecho a rectificar
            1. Primero
            2. Segundo
            """.trimIndent(),
            result.text,
        )
        assertFalse(result.text.contains("#"))
        assertFalse(result.text.contains("**"))
        assertFalse(result.text.lines().any { it.startsWith("- ") })
    }

    @Test fun `R6 los titulos y el texto en negrita llevan estilo en negrita`() {
        val result = markdownToAnnotated(source)
        val bold = boldRanges(result)
        assertTrue(bold.contains("Consentimiento informado"))
        assertTrue(bold.contains("1. Responsable"))
        assertTrue(bold.contains("finalidad asistencial"))
        assertTrue(bold.contains("Ley 1581"))
        assertTrue(bold.contains("rectificar"))
        val headingSizes = result.spanStyles.filter { result.text.substring(it.start, it.end) == "Consentimiento informado" }
            .map { it.item.fontSize }
        assertTrue(headingSizes.any { it != androidx.compose.ui.unit.TextUnit.Unspecified })
    }

    @Test fun `R6 un texto sin Markdown se conserva igual y un asterisco suelto no se pierde`() {
        val plain = "Texto normal con 2 * 3 y sin cierre **abierto"
        assertEquals(plain, markdownToAnnotated(plain).text)
    }
}
