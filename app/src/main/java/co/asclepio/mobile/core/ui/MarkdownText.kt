package co.asclepio.mobile.core.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*]\\s+(.*)$")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")

/**
 * Conversor mínimo de Markdown (D12, R6): títulos (`#`), negrita (`**…**`) y listas (`- `, `1. `).
 * Los símbolos de Markdown no se muestran; el resto queda como texto.
 */
fun markdownToAnnotated(markdown: String): AnnotatedString = buildAnnotatedString {
    markdown.lines().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val heading = HEADING.matchEntire(line)
        val bullet = BULLET.matchEntire(line)
        when {
            heading != null -> {
                val size = when (heading.groupValues[1].length) {
                    1 -> 22.sp
                    2 -> 19.sp
                    else -> 17.sp
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size)) {
                    appendInline(heading.groupValues[2])
                }
            }
            bullet != null -> {
                append("• ")
                appendInline(bullet.groupValues[1])
            }
            else -> appendInline(line) // incluye las listas numeradas: el número es contenido
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var last = 0
    for (match in BOLD.findAll(text)) {
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
