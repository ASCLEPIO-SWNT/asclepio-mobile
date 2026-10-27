package co.asclepio.mobile.core.network

import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET

@Serializable
private data class PingDto(val ok: Boolean)

private interface PingApi {
    @GET("api/v1/ping")
    suspend fun ping(): PingDto
}

// Retrofit real contra MockWebServer (CHECKPOINTS C4).
class ApiCallTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PingApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = createRetrofit(server.url("/").toString()).create(PingApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun problem(code: Int, body: String, contentType: String = "application/problem+json") =
        MockResponse().setResponseCode(code).setHeader("Content-Type", contentType).setBody(body)

    private suspend fun failure(): ApiError {
        val result = apiCall { api.ping() }
        assertTrue("se esperaba Failure: $result", result is ApiResult.Failure)
        return (result as ApiResult.Failure).error
    }

    @Test fun `R24 exito devuelve Success con el cuerpo`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        assertEquals(ApiResult.Success(PingDto(true)), apiCall { api.ping() })
    }

    @Test fun `R24 problem+json completo se convierte a ProblemDetails`() = runTest {
        server.enqueue(
            problem(
                400,
                """{"type":"about:blank","title":"Bad Request","status":400,"detail":"x","instance":"/api/v1/ping",
                   "errors":[{"field":"documentNumber","message":"m"}],"extra":"ignorado"}""",
            ),
        )
        val problem = (failure() as ApiError.Http).problem
        assertEquals(
            ProblemDetails(
                type = "about:blank",
                title = "Bad Request",
                status = 400,
                detail = "x",
                instance = "/api/v1/ping",
                errors = listOf(FieldError("documentNumber", "m")),
                retryAfterSeconds = null,
            ),
            problem,
        )
    }

    @Test fun `R24 problem+json sin campos opcionales ni errors se acepta`() = runTest {
        server.enqueue(problem(409, """{"type":"about:blank","title":"Conflict","status":409}"""))
        val problem = (failure() as ApiError.Http).problem
        assertEquals(409, problem.status)
        assertEquals("Conflict", problem.title)
        assertNull(problem.detail)
        assertNull(problem.instance)
        assertTrue(problem.errors.isEmpty())
    }

    @Test fun `R24 cuerpo vacio devuelve ProblemDetails con solo el codigo HTTP`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(ApiError.Http(ProblemDetails(status = 500)), failure())
    }

    @Test fun `R24 cuerpo HTML devuelve ProblemDetails con solo el codigo HTTP`() = runTest {
        server.enqueue(problem(502, "<html><body>Bad gateway</body></html>", "text/html"))
        assertEquals(ApiError.Http(ProblemDetails(status = 502)), failure())
    }

    @Test fun `R24 JSON que no es problem+json valido devuelve solo el codigo HTTP`() = runTest {
        server.enqueue(problem(503, """{"errors":"no-es-lista"}"""))
        assertEquals(ApiError.Http(ProblemDetails(status = 503)), failure())
    }

    @Test fun `R22 Retry-After presente se lee en segundos`() = runTest {
        server.enqueue(
            problem(429, """{"type":"about:blank","title":"Too Many Requests","status":429}""")
                .setHeader("Retry-After", "42"),
        )
        val problem = (failure() as ApiError.Http).problem
        assertEquals(42, problem.retryAfterSeconds)
        assertEquals(42, problem.retryAfterOrDefault())
    }

    @Test fun `R22 Retry-After ausente usa 60 segundos`() = runTest {
        server.enqueue(problem(429, """{"type":"about:blank","title":"Too Many Requests","status":429}"""))
        val problem = (failure() as ApiError.Http).problem
        assertNull(problem.retryAfterSeconds)
        assertEquals(60, problem.retryAfterOrDefault())
    }

    @Test fun `R22 Retry-After no numerico, cero o negativo usa 60 segundos`() = runTest {
        for (value in listOf("abc", "0", "-5", "Wed, 21 Oct 2026 07:28:00 GMT", "1.5")) {
            server.enqueue(problem(429, "").setHeader("Retry-After", value))
            val problem = (failure() as ApiError.Http).problem
            assertNull(value, problem.retryAfterSeconds)
            assertEquals(value, 60, problem.retryAfterOrDefault())
        }
    }

    @Test fun `R22 un 429 se traduce al mensaje de demasiados intentos con los segundos`() = runTest {
        server.enqueue(problem(429, "").setHeader("Retry-After", "17"))
        val error = failure()
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(17)), error.commonMessage())
        assertEquals(17, error.rateLimitSeconds())
    }

    @Test fun `R23 sin respuesta HTTP se traduce a Network y al mensaje sin conexion`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val error = failure()
        assertEquals(ApiError.Network, error)
        assertEquals(UiMessage(R.string.error_network), error.commonMessage())
    }

    @Test fun `R23 servidor apagado se traduce a Network`() = runTest {
        server.shutdown()
        assertEquals(ApiError.Network, failure())
    }

    @Test fun `R23 5xx y codigos no tratados se traducen al mensaje general sin textos de la API`() = runTest {
        for (code in listOf(500, 503, 404, 418)) {
            server.enqueue(problem(code, """{"type":"about:blank","title":"Texto de la API","status":$code,"detail":"detalle de la API"}"""))
            val error = failure()
            assertEquals(UiMessage(R.string.error_general), error.commonMessage())
            assertFalse(error.commonMessage().args.any { it.toString().contains("API") })
            assertNull(error.rateLimitSeconds())
        }
    }

    @Test fun `R23 un 200 con cuerpo no valido se traduce al mensaje general`() = runTest {
        server.enqueue(MockResponse().setBody("""{"otro":1}"""))
        val error = failure()
        assertEquals(ApiError.InvalidBody, error)
        assertEquals(UiMessage(R.string.error_general), error.commonMessage())
    }
}
