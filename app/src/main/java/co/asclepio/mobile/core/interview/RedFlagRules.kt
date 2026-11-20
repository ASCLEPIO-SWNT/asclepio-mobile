package co.asclepio.mobile.core.interview

import java.text.Normalizer
import java.util.Locale

/**
 * Reglas deterministas de señales de alarma (F-11-mobile R11, F-11.AC3). Port literal de `normalizeText`,
 * `RED_FLAG_PATTERNS`, `detectRedFlags`, `mergeRedFlags` y `mentionsPain` de
 * `../asclepio-api/src/modules/interview/interview.rules.ts`; ambos lados se prueban con el mismo fixture
 * (`red-flag-cases.json`). Patrones PROVISIONALES (PA-F11-1): en la duda se escala.
 */
object RedFlagRules {
    private val MARKS = Regex("""\p{M}+""")

    /** Los espacios de `\s` de JavaScript (java.util.regex `\s` solo cubre ASCII). */
    private val SPACES = Regex("[\\s\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+")

    /** Minúsculas, sin tildes (NFD sin marcas) y con los espacios colapsados. */
    fun normalize(text: String): String =
        SPACES.replace(MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT), " ").trim()

    /** Mismos patrones y orden que RED_FLAG_PATTERNS (sintaxis común a JS y java.util.regex). */
    val PATTERNS: Map<RedFlag, List<String>> = linkedMapOf(
        RedFlag.CHEST_PAIN to listOf(
            """\b(dolor|duele|dolia|dolio|presion|opresion|peso|aprieta|apretado|oprimido)\b[^.,;]{0,30}\bpecho\b""",
            """\bpecho\b[^.,;]{0,15}\b(apretado|oprimido|duele|dolia|dolor|aprieta)\b""",
            """\bdolor toracico\b""",
        ),
        RedFlag.BREATHING_DIFFICULTY to listOf(
            """\b(no puedo|no puede|no podia|me cuesta|le cuesta|dificultad para|problemas para)\s+respirar\b""",
            """\bahog(o|a|ado|ada|ando|arme)\b""",
            """\bfalta\s+(de\s+|el\s+)?aire\b""",
            """\bsin aire\b""",
            """\bno me entra (el\s+)?aire\b""",
            """\bdisnea\b""",
        ),
        RedFlag.LOSS_OF_CONSCIOUSNESS to listOf(
            """\bdesmay\w*""",
            """\bperd\w*\s+(el\s+|la\s+)?(de\s+)?(conocimiento|conciencia|sentido)\b""",
            """\binconscien\w*""",
            """\bno supe de mi\b""",
        ),
        RedFlag.SEIZURE to listOf("""\bconvuls\w*""", """\b(ataque|crisis)\s+(de\s+)?epilep\w*"""),
        RedFlag.SEVERE_BLEEDING to listOf(
            """\bsangrado\s+(muy\s+)?(abundante|fuerte|intens\w*)""",
            """\bhemorragia\w*""",
            """\b(sangra|sangro|sangrando)\s+(mucho|muchisimo|demasiado|bastante|sin parar)\b""",
            """\bmucha sangre\b""",
            """\bno (para|deja) de sangrar\b""",
            """\bvomit\w*\s+(con\s+)?sangre\b""",
        ),
        RedFlag.STROKE_SIGNS to listOf(
            """\b(cara|boca)\b[^.,;]{0,20}\b(torcid|chuec|caid|cayo)\w*""",
            """\b(torci|cayo)\w*\s+(la\s+)?(cara|boca)\b""",
            """\b(debil\w*|durm\w*|duerm\w*|dormid\w*|adormec\w*|entumi\w*|hormigueo|no siento|paraliz\w*|no (puedo|puede|podia) mover)\b[^.,;]{0,40}\b(un lado|medio cuerpo|mitad del cuerpo|mitad de la cara|media cara|lado (derecho|izquierdo)|(brazo|pierna|mano|pie)s? (derech|izquierd)\w*)""",
            """\b(brazo|pierna|mano|pie)s? (derech|izquierd)\w*[^.,;]{0,20}\b(debil\w*|dormid\w*|adormec\w*|entumi\w*|paraliz\w*)""",
            """\bno (puedo|puede|podia) mover (el|la|mi|su)\s+(brazo|pierna|mano|cara)\b""",
            """\b(dificultad para|no puedo|no puede|no podia|me cuesta|le cuesta)\s+hablar\b""",
            """\bhabl\w*\s+(enredad|arrastrad|rar|trabad)\w*""",
            """\btrab\w*\s+la\s+lengua\b""",
            """\blengua\s+trabad\w*""",
        ),
        RedFlag.SUICIDAL_IDEATION to listOf(
            """\bsuicid\w*""",
            """\b(quiero|quisiera|ganas de)\s+morir\w*""",
            """\bmatarme\b""",
            """\bquitarme la vida\b""",
            """\bacabar con mi vida\b""",
            """\bno quiero (seguir\s+)?(vivir|viviendo)\b""",
            """\bhacerme dano\b""",
        ),
    )

    private val COMPILED: List<Pair<RedFlag, List<Regex>>> = RedFlag.entries.map { flag -> flag to PATTERNS.getValue(flag).map(::Regex) }

    /** Señales de uno o varios textos, sin repetir y en el orden de [RedFlag]. */
    fun detect(vararg texts: String): List<RedFlag> {
        val normalized = texts.map(::normalize)
        return COMPILED.filter { (_, res) -> normalized.any { t -> res.any { it.containsMatchIn(t) } } }.map { it.first }
    }

    /** Señales del motivo y de todas las respuestas dadas (R11). */
    fun detect(complaint: String, turns: List<Turn>): List<RedFlag> =
        detect(complaint, *turns.mapNotNull { it.answer }.toTypedArray())

    /** Las de [base] en su orden y después las nuevas, sin quitar ninguna. */
    fun merge(base: List<RedFlag>, extra: List<RedFlag>): List<RedFlag> = base + extra.filter { it !in base }.distinct()

    private val PAIN = Regex("""\b(dolor|duele|duelen|doler)\w*""")

    /** true si el texto normalizado menciona dolor. */
    fun mentionsPain(text: String): Boolean = PAIN.containsMatchIn(normalize(text))
}
