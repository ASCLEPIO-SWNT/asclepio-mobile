package co.asclepio.mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** UI-01: validates every text resource file, not just the historical strings.xml. */
class LocalizationCatalogTest {
    private val resources = File("src/main/res")
    private val locales = listOf("en", "pt", "fr", "de")

    @Test fun `R4 los cinco catalogos agregan todos los XML con claves y argumentos equivalentes`() {
        val source = readCatalog(File(resources, "values"))
        assertTrue("El catálogo base debe incluir los textos existentes", source.size >= 127)
        for (locale in locales) {
            val folder = File(resources, "values-$locale")
            assertTrue("Falta $folder", folder.isDirectory)
            val failures = differences(source, readCatalog(folder))
            assertTrue("$locale: ${failures.joinToString()}", failures.isEmpty())
        }
    }

    @Test fun `R21 errores validaciones y etiquetas accesibles no dependen de fallback espanol`() {
        val source = readCatalog(File(resources, "values"))
        val controlled = source.filterKeys { key ->
            key.name.startsWith("error_") || key.name.startsWith("staff_error_") ||
                key.name.startsWith("field_") || key.name.startsWith("a11y_") ||
                key.name.startsWith("guide_") || key.name.startsWith("voice_") ||
                key.name.startsWith("preferences_") || key.name.startsWith("status_") ||
                key.name.startsWith("date_") || key.name.startsWith("ui_") ||
                key.name.startsWith("clinical_") || key.name.startsWith("dash_")
        }.filterValues { it.translatable }
        assertTrue("Debe comprobar errores y campos reales", controlled.size >= 20)
        for (locale in locales) {
            val catalog = readCatalog(File(resources, "values-$locale"))
            for ((key, entry) in controlled) {
                assertTrue("$locale no contiene $key", catalog.containsKey(key))
                assertTrue("$locale tiene $key vacío", catalog.getValue(key).forms.values.all { it.isNotBlank() })
                assertTrue("$locale cambia argumentos de $key", differences(mapOf(key to entry), mapOf(key to catalog.getValue(key))).isEmpty())
            }
        }
    }

    @Test fun `R4 el agregador admite constantes y recursos no textuales y valida arrays y plurales`() {
        val root = kotlin.io.path.createTempDirectory("catalog-validation").toFile()
        try {
            val base = File(root, "values").apply { mkdirs() }
            val localized = File(root, "values-en").apply { mkdirs() }
            File(base, "strings.xml").writeText("""<resources>
                <string name="brand" translatable="false">Asclepio</string>
                <color name="brand_color">#34B2A7</color>
                <string name="title">Título</string>
                <style name="Theme"><item name="fontFamily">sans</item></style>
            </resources>""")
            File(base, "more.xml").writeText("""<resources>
                <string-array name="steps"><item>Uno</item><item>%1${'$'}s</item></string-array>
                <plurals name="count"><item quantity="one">%1${'$'}d resultado</item><item quantity="other">%1${'$'}d resultados</item></plurals>
            </resources>""")
            File(localized, "all.xml").writeText("""<resources>
                <string name="title">Title</string>
                <string-array name="steps"><item>One</item><item>%1${'$'}s</item></string-array>
                <plurals name="count"><item quantity="one">%1${'$'}d result</item><item quantity="other">%1${'$'}d results</item></plurals>
            </resources>""")
            assertTrue(differences(readCatalog(base), readCatalog(localized)).isEmpty())
            File(localized, "all.xml").writeText(File(localized, "all.xml").readText().replace("%1${'$'}s", "%1${'$'}d"))
            assertFalse("Un array traducido no puede cambiar el tipo del argumento", differences(readCatalog(base), readCatalog(localized)).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    private data class Key(val type: String, val name: String)
    private data class Entry(val translatable: Boolean, val formatted: Boolean, val forms: Map<String, String>)

    private fun readCatalog(folder: File): Map<Key, Entry> {
        val entries = linkedMapOf<Key, Entry>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        folder.listFiles { file -> file.extension == "xml" }?.sortedBy { it.name }?.forEach { file ->
            val root = factory.newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                val type = if (node.tagName == "item") node.getAttribute("type") else node.tagName
                if (type !in setOf("string", "string-array", "plurals")) continue
                val key = Key(type, node.getAttribute("name"))
                check(key.name.isNotBlank()) { "Recurso sin nombre en $file" }
                val forms = if (type == "string") mapOf("value" to node.textContent) else {
                    val items = node.getElementsByTagName("item")
                    (0 until items.length).associate { itemIndex ->
                        val item = items.item(itemIndex) as Element
                        val form = if (type == "plurals") item.getAttribute("quantity") else itemIndex.toString()
                        check(form.isNotBlank()) { "Plural sin quantity en $file/$key" }
                        form to item.textContent
                    }.also { check(it.size == items.length) { "Formas duplicadas en $file/$key" } }
                }
                check(entries.put(key, Entry(node.getAttribute("translatable") != "false", node.getAttribute("formatted") != "false", forms)) == null) {
                    "Clave duplicada $key en $folder"
                }
            }
        }
        return entries
    }

    private fun signatures(entry: Entry): Map<String, List<String>> = entry.forms.mapValues { (_, text) ->
        if (!entry.formatted) emptyList() else FORMAT.findAll(text).map { it.value }.filter { it != "%%" }.toList()
    }

    private fun differences(source: Map<Key, Entry>, target: Map<Key, Entry>): List<String> = buildList {
        val required = source.filterValues { it.translatable }
        val missing = required.keys - target.keys
        val extra = target.keys - source.keys
        if (missing.isNotEmpty()) add("Faltan $missing")
        if (extra.isNotEmpty()) add("Sobran $extra")
        for ((key, actual) in target) {
            val expected = source[key] ?: continue
            if (!expected.translatable) {
                if (actual.forms != expected.forms) add("Constante modificada $key")
                continue
            }
            if (!actual.translatable || actual.formatted != expected.formatted) add("Atributos incompatibles $key")
            if (actual.forms.values.any { it.isBlank() }) add("Texto vacío $key")
            if (key.type == "plurals") {
                // Retain source categories; additional CLDR categories use the source 'other' signature.
                if ("other" !in actual.forms || !actual.forms.keys.containsAll(expected.forms.keys)) add("Faltan cantidades de $key")
                if (!actual.forms.keys.all { it in QUANTITIES }) add("Cantidad inválida en $key")
                for ((quantity, signature) in signatures(actual)) {
                    val expectedSignature = signatures(expected)[quantity] ?: signatures(expected)["other"]
                    if (signature != expectedSignature) add("Argumentos distintos $key/$quantity")
                }
            } else {
                if (actual.forms.keys != expected.forms.keys) add("Cardinalidad distinta $key")
                if (signatures(actual) != signatures(expected)) add("Argumentos distintos $key")
            }
            for ((form, text) in actual.forms) {
                val base = expected.forms[form] ?: expected.forms["other"] ?: continue
                if (base.startsWith("@") && text != base) add("Referencia modificada $key/$form")
            }
        }
    }

    private companion object {
        val FORMAT = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]""")
        val QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")
    }
}
