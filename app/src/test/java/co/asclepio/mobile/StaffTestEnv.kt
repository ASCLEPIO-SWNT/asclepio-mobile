package co.asclepio.mobile

import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.network.createRetrofit
import co.asclepio.mobile.feature.staffaccount.data.StaffAccountRepository
import co.asclepio.mobile.feature.staffaccount.data.StaffAuthApi
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Retrofit y DataStore reales; únicamente se sustituye el cifrador Android en JVM. */
class StaffTestEnv(dir: File, scope: CoroutineScope? = null) {
    val server = MockWebServer().apply { start() }
    val clock = MutableClock(Instant.parse("2026-10-29T15:00:00Z"))
    private val ds = TestDataStore(dir.resolve("staff.preferences_pb"))
    val store = SessionStore(ds.dataStore, FakeTokenCipher())
    lateinit var manager: SessionManager
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor { manager.currentToken() })
        .readTimeout(2, TimeUnit.SECONDS).build()
    val retrofit = createRetrofit(server.url("/").toString(), client)
    val meApi: MeApi = retrofit.create(MeApi::class.java)
    init { manager = if (scope == null) SessionManager(store, meApi, clock) else SessionManager(store, meApi, clock, scope) }
    val repository = StaffAccountRepository(retrofit.create(StaffAuthApi::class.java), meApi, manager)
    val tenant = RememberedTenant("t-patient", "IPS paciente", "site", "Sede")

    fun enqueue(code: Int, body: String = "", retryAfter: String? = null) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).apply {
            setHeader("Content-Type", if (code >= 400) "application/problem+json" else "application/json")
            retryAfter?.let { setHeader("Retry-After", it) }
        })
    }
    suspend fun activate(mode: AccessMode = AccessMode.STAFF, token: String = TOKEN, role: String = "NURSE") {
        if (mode == AccessMode.PATIENT) manager.saveTenant(tenant)
        val attempt = manager.beginLogin(mode)
        val me = if (mode == AccessMode.STAFF) staffMeDto(role) else patientMeDto()
        check(manager.completeLogin(attempt, token, EXPIRES, me))
    }
    fun close() { manager.close(); ds.close(); server.shutdown() }
    companion object {
        const val TOKEN = "staff-token-private"
        val EXPIRES: Instant = Instant.parse("2026-10-29T23:00:00Z")
        const val LOGIN_OK = """{"accessToken":"staff-token-private","tokenType":"Bearer","expiresAt":"2026-10-29T23:00:00Z"}"""
        fun staffMe(role: String = "NURSE") = """{"kind":"STAFF","id":"staff-id","tenantId":"staff-tenant","email":"nurse@ips.co","fullName":"Nombre privado","role":"$role"}"""
        fun staffMeDto(role: String = "NURSE") = MeDto("STAFF", "staff-id", "staff-tenant", role = role, email = "nurse@ips.co", fullName = "Nombre privado")
        fun patientMe() = """{"kind":"PATIENT","id":"account","tenantId":"t-patient","patientId":"patient","consentVersion":"v1","identityVerified":false}"""
        fun patientMeDto() = MeDto("PATIENT", "account", "t-patient", "patient", "v1", false)
    }
}
