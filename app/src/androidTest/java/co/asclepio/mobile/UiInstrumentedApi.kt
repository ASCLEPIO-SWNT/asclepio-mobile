package co.asclepio.mobile

import java.time.Instant
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource

/** Device-local, synthetic HTTP fixture. No endpoint override, production hook, real account or DB. */
class UiInstrumentedApi : ExternalResource() {
    private val server = MockWebServer()
    override fun before() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath
                val method = request.method
                val (status, body) = when {
                    method == "GET" && path == "/api/v1/public/tenants" -> 200 to TENANTS
                    method == "GET" && path == "/api/v1/public/consent" -> 200 to """{"version":"ui01-test","text":"## Consentimiento sintético\nSólo para las pruebas de la app."}"""
                    method == "POST" && path == "/api/v1/auth/patient/login" -> 200 to access("android-synthetic-patient")
                    method == "GET" && path == "/api/v1/me" -> 200 to PATIENT
                    method == "GET" && path == "/api/v1/patient/profile" -> 200 to PROFILE
                    method == "GET" && path == "/api/v1/patient/pre-registrations" -> 200 to "[]"
                    else -> 404 to """{"type":"about:blank","title":"Not found","status":404}"""
                }
                return MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setHeader("Connection", "close").setBody(body)
            }
        }
        // Root removes adb reverse for this runner, then restores it for the separate preview fixture.
        server.start(3000)
    }
    override fun after() { server.shutdown() }
    private fun access(token: String) = """{"accessToken":"$token","tokenType":"Bearer","expiresAt":"${Instant.now().plusSeconds(8*3600)}"}"""

    companion object {
        const val TENANT = "5d0c0d4e-6a3b-4b8e-9c1f-0000000000aa"
        const val SITE = "7a1b2c3d-0000-4000-8000-000000000001"
        const val TENANTS = """[{"id":"$TENANT","name":"IPS QA Android","sites":[{"id":"$SITE","name":"Sede QA Android"}]}]"""
        const val PATIENT = """{"kind":"PATIENT","id":"a1100000-0000-4000-8000-000000000001","tenantId":"$TENANT","patientId":"b1100000-0000-4000-8000-000000000001","consentVersion":"ui01-test","identityVerified":false}"""
        const val PROFILE = """{"id":"b1100000-0000-4000-8000-000000000001","documentType":"CC","documentNumber":"DEV-UI01-PATIENT","fullName":"Nombre Sintético Inicial","birthDate":"1990-05-17","sex":"X","editable":true}"""
    }
}
