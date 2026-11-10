package co.asclepio.mobile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alcance de red de la app solo del paciente (DT-01-mobile): lee las fuentes de producción, no la reflexión, para
 * que una API nueva o un literal suelto también cuenten.
 */
class PatientOnlyScopeTest {
    private val main = File("src/main/java")

    private val patientOperations = setOf(
        "GET api/v1/health", "GET api/v1/public/tenants", "GET api/v1/public/consent",
        "POST api/v1/auth/patient/accounts", "POST api/v1/auth/patient/login", "GET api/v1/me",
        "GET api/v1/patient/profile", "PATCH api/v1/patient/profile",
        "POST api/v1/patient/pre-registrations", "GET api/v1/patient/pre-registrations",
    )

    /** Rutas de personal del contrato (consumidas hasta 0.4.0 y presentes en 0.7.0), con cualquier método. */
    private val staffRoutes = listOf(
        "api/v1/auth/login", "api/v1/patients", "api/v1/pre-registrations",
        "api/v1/pre-registrations/{id}/confirm", "api/v1/pre-registrations/{id}/reject",
        "api/v1/encounters", "api/v1/encounters/{id}", "api/v1/encounters/{id}/cancel",
        "api/v1/encounters/{id}/vital-signs", "api/v1/encounters/{id}/symptom-findings",
        "api/v1/encounters/{id}/clinical-capture", "api/v1/encounters/{id}/start-care",
        "api/v1/encounters/{id}/close", "api/v1/encounters/{id}/triage-suggestions",
        "api/v1/encounters/{id}/triage-decision", "api/v1/queue",
    )

    private val annotation = Regex("""@(GET|POST|PATCH|PUT|DELETE)\(\s*"([^"]*)"\s*\)""")
    private val literal = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\\\n]|\\\\.)*\"")
    private val apiPath = Regex("""api/v1/[A-Za-z0-9_\-/{}$.]*""")

    private fun sources(root: File): Map<String, String> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.relativeTo(root).path to it.readText() }

    /** Operaciones Retrofit declaradas (método + ruta sin `/` inicial); `@HTTP` cuenta como operación no prevista. */
    private fun operations(files: Map<String, String>): Set<String> = files.values.flatMap { text ->
        annotation.findAll(text).map { "${it.groupValues[1]} ${it.groupValues[2].removePrefix("/")}" }.toList() +
            Regex("""@HTTP\(""").findAll(text).map { "HTTP ?" }.toList()
    }.toSet()

    private fun segments(path: String) = path.trimEnd('/').split('/')

    /**
     * Una ruta de personal o una subruta suya, también como base concatenable (`"api/v1/encounters/" + id`). Un prefijo
     * común con el paciente (`/api/v1/auth/` en el interceptor) no es una ruta de personal.
     */
    private fun isStaff(path: String): Boolean {
        val candidate = segments(path)
        return staffRoutes.any { route ->
            val expected = segments(route)
            candidate.size >= expected.size &&
                expected.indices.all { i -> if (expected[i] == "{id}") candidate[i].isNotEmpty() else candidate[i] == expected[i] }
        }
    }

    /** `archivo -> ruta` de cada literal (anotación o cadena) que nombra una ruta de personal. */
    private fun staffReferences(files: Map<String, String>): List<String> = files.flatMap { (file, text) ->
        literal.findAll(text).flatMap { apiPath.findAll(it.value) }.map { it.value }.filter(::isStaff).map { "$file -> $it" }.toList()
    }

    @Test fun `R1 las fuentes de produccion declaran exactamente las operaciones del paciente y publicas`() {
        val files = sources(main)
        assertTrue("Sin fuentes en $main", files.isNotEmpty())
        assertEquals(patientOperations, operations(files))
    }

    @Test fun `R2 ninguna fuente de produccion nombra una ruta de personal en anotaciones ni literales`() {
        val references = staffReferences(sources(main))
        assertTrue("Rutas de personal en src/main: ${references.joinToString()}", references.isEmpty())
    }

    @Test fun `R2 el detector senala archivo y ruta con cualquier metodo con o sin barra inicial y con id`() {
        val files = mapOf(
            "a/StaffApi.kt" to """
                @POST("/api/v1/auth/login") suspend fun login()
                @HTTP(method = "DELETE", path = "api/v1/encounters/{encounterId}/cancel") suspend fun cancel()
            """.trimIndent(),
            "b/Urls.kt" to "val queue = \"/api/v1/queue\"\nval detail = \"api/v1/encounters/\" + id\nval capture = \"api/v1/encounters/\$id/clinical-capture\"",
            "c/Raw.kt" to "val body = \"\"\"{\"url\":\"http://localhost:3000/api/v1/patients?documentNumber=1\"}\"\"\"",
            "d/Patient.kt" to """
                @GET("api/v1/patient/pre-registrations") suspend fun mine()
                @POST("api/v1/auth/patient/login") suspend fun login()
                val profile = "/api/v1/patient/profile"
                val public = path.startsWith("/api/v1/auth/")
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "a/StaffApi.kt -> api/v1/auth/login",
                "a/StaffApi.kt -> api/v1/encounters/{encounterId}/cancel",
                "b/Urls.kt -> api/v1/queue",
                "b/Urls.kt -> api/v1/encounters/",
                "b/Urls.kt -> api/v1/encounters/\$id/clinical-capture",
                "c/Raw.kt -> api/v1/patients",
            ),
            staffReferences(files),
        )
        assertTrue("HTTP ?" in operations(files))
        assertTrue("POST api/v1/auth/login" in operations(files))
    }
}
