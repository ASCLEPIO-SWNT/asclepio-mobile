package co.asclepio.mobile.feature.status.ui

import co.asclepio.mobile.feature.status.data.HealthApi
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Se prueba contra un servidor HTTP falso que sigue el contrato (CHECKPOINTS C4), sin mockear Retrofit.
class LoadStatusTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HealthApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = createRetrofit(server.url("/").toString()).create(HealthApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `health ok muestra la hora del servidor en Bogota`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok","time":"2026-10-27T00:42:00.000Z"}"""))
        assertEquals(StatusUiState.Success("26/10/2026 19:42:00"), loadStatus(api))
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test fun `respuesta problem+json se traduce a Error con el codigo HTTP`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"about:blank","title":"Service Unavailable","status":503}"""),
        )
        assertEquals(StatusUiState.Error(UiMessage(R.string.status_http_error, listOf(503))), loadStatus(api))
    }

    @Test fun `API inalcanzable se traduce a Error sin conexion`() = runTest {
        server.shutdown()
        val result = loadStatus(api)
        assertTrue(result is StatusUiState.Error && result.message == UiMessage(R.string.error_network))
    }
    @Test fun `R4 diagnostic errors retain resource ids instead of Spanish text or backend messages`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"unexpected","time":"2026-10-27T00:42:00.000Z"}"""))
        assertEquals(StatusUiState.Error(UiMessage(R.string.error_general)), loadStatus(api))
    }
}
