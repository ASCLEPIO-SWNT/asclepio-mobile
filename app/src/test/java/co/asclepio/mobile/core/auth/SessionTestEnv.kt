package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Sesión del paciente con Retrofit y DataStore reales; solo se sustituye el cifrador Android en la JVM. */
class SessionTestEnv(dir: File, scope: CoroutineScope? = null) {
    val server = MockWebServer().apply { start() }
    val clock = MutableClock(Instant.parse("2026-10-29T15:00:00Z"))
    private val ds = TestDataStore(dir.resolve("session.preferences_pb"))
    val dataStore get() = ds.dataStore
    val store = SessionStore(ds.dataStore, FakeTokenCipher())
    lateinit var manager: SessionManager
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor { manager.currentToken() })
        .readTimeout(2, TimeUnit.SECONDS).build()
    val retrofit = createRetrofit(server.url("/").toString(), client)
    val meApi: MeApi = retrofit.create(MeApi::class.java)
    init { manager = if (scope == null) SessionManager(store, meApi, clock) else SessionManager(store, meApi, clock, scope) }
    val tenant = RememberedTenant(TENANT, "IPS Uno", "site", "Sede Norte")

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body)
            .setHeader("Content-Type", if (code >= 400) "application/problem+json" else "application/json"))
    }

    /** IPS confirmada y sesión de paciente verificada (cada llamada abre una generación nueva). */
    suspend fun activate(token: String = TOKEN) {
        manager.saveTenant(tenant)
        val attempt = manager.beginLogin(AccessMode.PATIENT)
        check(manager.completeLogin(attempt, token, EXPIRES, patientMeDto()))
    }

    fun close() { manager.close(); ds.close(); server.shutdown() }

    companion object {
        const val TOKEN = "patient-token-private"
        const val TENANT = "t-patient"
        val EXPIRES: Instant = Instant.parse("2026-10-29T23:00:00Z")
        fun patientMe(tenantId: String = TENANT) =
            """{"kind":"PATIENT","id":"account","tenantId":"$tenantId","patientId":"patient","consentVersion":"v1","identityVerified":false}"""
        fun patientMeDto() = MeDto("PATIENT", "account", TENANT, "patient", "v1", false)
        fun staffMe(role: String) =
            """{"kind":"STAFF","id":"staff-id","tenantId":"$TENANT","email":"staff@ips.co","fullName":"Nombre privado","role":"$role"}"""
    }
}
