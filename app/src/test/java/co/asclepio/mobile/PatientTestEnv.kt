package co.asclepio.mobile

import co.asclepio.mobile.core.auth.AuthInterceptor
import co.asclepio.mobile.core.auth.FakeTokenCipher
import co.asclepio.mobile.core.auth.MeApi
import co.asclepio.mobile.core.auth.MutableClock
import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.SessionStore
import co.asclepio.mobile.core.auth.TestDataStore
import co.asclepio.mobile.core.network.createRetrofit
import co.asclepio.mobile.core.tenants.TenantsApi
import co.asclepio.mobile.feature.encounters.data.PreRegistrationRepository
import co.asclepio.mobile.feature.encounters.data.PreRegistrationsApi
import co.asclepio.mobile.feature.patients.data.PatientProfileApi
import co.asclepio.mobile.feature.patients.data.PatientProfileRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.io.File
import java.time.Instant

/**
 * Grafo real de F-02 (Retrofit, DataStore, SessionManager, repositorios) contra MockWebServer, que responde como el
 * contrato 0.4.0. Solo el cifrador del token es falso (D1 de F-01).
 */
class PatientTestEnv(dir: File) {
    val server = MockWebServer().apply { start() }
    val clock = MutableClock(Instant.parse("2026-10-27T15:00:00Z"))
    private val ds = TestDataStore(dir.resolve("session.preferences_pb"))
    val store = SessionStore(ds.dataStore, FakeTokenCipher())
    lateinit var sessionManager: SessionManager
    private val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor { sessionManager.currentToken() }).build()
    private val retrofit = createRetrofit(server.url("/").toString(), client)
    val meApi: MeApi = retrofit.create(MeApi::class.java)
    val tenantsApi: TenantsApi = retrofit.create(TenantsApi::class.java)
    val profileApi: PatientProfileApi = retrofit.create(PatientProfileApi::class.java)
    val preRegistrationsApi: PreRegistrationsApi = retrofit.create(PreRegistrationsApi::class.java)

    init {
        sessionManager = SessionManager(store, meApi, clock)
    }

    val profileRepository = PatientProfileRepository(profileApi, sessionManager)
    val preRegistrationRepository = PreRegistrationRepository(preRegistrationsApi, tenantsApi, sessionManager)

    val tenant = RememberedTenant(TENANT_ID, "IPS Uno", SITE_NORTE, "Sede Norte")

    /** IPS recordada y sesión activa del paciente. */
    suspend fun login() {
        store.saveTenant(tenant)
        store.saveSession(TOKEN, Instant.parse("2026-10-28T01:00:00Z"))
        sessionManager.refresh()
    }

    fun enqueue(code: Int, body: String = "", retryAfter: String? = null) {
        val response = MockResponse().setResponseCode(code).setBody(body)
            .setHeader("Content-Type", if (code >= 400) "application/problem+json" else "application/json")
        retryAfter?.let { response.setHeader("Retry-After", it) }
        server.enqueue(response)
    }

    /** Sin respuesta HTTP (la conexión se corta). */
    fun enqueueDisconnect() = server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

    fun problem(code: Int, vararg fields: String): String {
        val errors = if (fields.isEmpty()) "" else
            ""","errors":[${fields.joinToString(",") { """{"field":"$it","message":"Mensaje de la API"}""" }}]"""
        return """{"type":"about:blank","title":"Título de la API","status":$code,"detail":"Detalle de la API"$errors}"""
    }

    fun close() {
        ds.close()
        server.shutdown()
    }

    companion object {
        const val TOKEN = "eyJhbGciOiJIUzI1NiJ9.paciente.firma"
        const val TENANT_ID = "11111111-1111-1111-1111-111111111111"
        const val SITE_NORTE = "22222222-2222-2222-2222-222222222222"
        const val SITE_SUR = "33333333-3333-3333-3333-333333333333"

        fun profile(
            editable: Boolean = true,
            documentType: String? = "CC",
            documentNumber: String? = "DEV-1020304050",
            fullName: String? = "DEV Paciente Prueba",
            birthDate: String? = "1990-05-17",
            sex: String? = "F",
        ): String {
            fun q(v: String?) = v?.let { "\"$it\"" } ?: "null"
            return """{"id":"44444444-4444-4444-4444-444444444444","documentType":${q(documentType)},""" +
                """"documentNumber":${q(documentNumber)},"fullName":${q(fullName)},"birthDate":${q(birthDate)},""" +
                """"sex":${q(sex)},"editable":$editable}"""
        }

        fun preRegistration(
            status: String = "PENDING",
            encounterStatus: String? = null,
            siteName: String = "Sede Norte",
            extra: String = "",
        ): String {
            val encounter = encounterStatus?.let { """{"id":"55555555-5555-5555-5555-555555555555","status":"$it"}""" } ?: "null"
            return """{"id":"66666666-6666-6666-6666-666666666666","siteId":"$SITE_NORTE","siteName":"$siteName",""" +
                """"status":"$status","preRegisteredAt":"2026-10-27T14:30:00.000Z","expiresAt":"2026-10-27T20:30:00.000Z",""" +
                """"encounter":$encounter$extra}"""
        }

        fun tenants(sites: String = """[{"id":"$SITE_NORTE","name":"Sede Norte"},{"id":"$SITE_SUR","name":"Sede Sur"}]""") =
            """[{"id":"99999999-9999-9999-9999-999999999999","name":"Otra IPS","sites":[{"id":"s-x","name":"Sede X"}]},""" +
                """{"id":"$TENANT_ID","name":"IPS Uno","sites":$sites}]"""

        fun RecordedRequest.jsonBody(): JsonObject = Json.parseToJsonElement(body.readUtf8()).jsonObject
    }
}
