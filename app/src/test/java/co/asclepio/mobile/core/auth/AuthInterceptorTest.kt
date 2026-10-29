package co.asclepio.mobile.core.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private lateinit var server: MockWebServer
    private var token: String? = "tok-123"
    private val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor { token }).build()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun send(method: String, path: String, authorization: String? = null): String? {
        server.enqueue(MockResponse().setBody("{}"))
        val body = if (method == "POST") "{}".toRequestBody("application/json".toMediaType()) else null
        val request = Request.Builder().url(server.url(path)).method(method, body)
            .apply { authorization?.let { header("Authorization", it) } }.build()
        client.newCall(request).execute().close()
        return server.takeRequest().getHeader("Authorization")
    }

    @Test fun `R29 nunca anade Authorization a los cuatro endpoints publicos y de auth`() {
        assertNull(send("GET", "/api/v1/public/tenants"))
        assertNull(send("GET", "/api/v1/public/consent"))
        assertNull(send("POST", "/api/v1/auth/patient/accounts"))
        assertNull(send("POST", "/api/v1/auth/patient/login"))
    }

    @Test fun `R29 anade Bearer a las rutas fuera de public y auth`() {
        assertEquals("Bearer tok-123", send("GET", "/api/v1/me"))
        assertEquals("Bearer tok-123", send("GET", "/api/v1/encounters/mine"))
    }

    @Test fun `R18 auth personal y public eliminan incluso una cabecera explicita y me conserva la suya`() {
        assertNull(send("POST", "/api/v1/auth/login", authorization = "Bearer anterior"))
        assertNull(send("GET", "/api/v1/public/tenants", authorization = "Bearer anterior"))
        assertEquals("Bearer operacion", send("GET", "/api/v1/me", authorization = "Bearer operacion"))
    }

    @Test fun `R29 sin sesion no anade cabecera y no sobrescribe una cabecera explicita`() {
        token = null
        assertNull(send("GET", "/api/v1/me"))
        token = "tok-123"
        assertEquals("Bearer explicito", send("GET", "/api/v1/me", authorization = "Bearer explicito"))
    }
}
