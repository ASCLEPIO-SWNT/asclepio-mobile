package co.asclepio.mobile.feature.account.ui

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
import co.asclepio.mobile.feature.account.data.AccountRepository
import co.asclepio.mobile.feature.account.data.PatientAuthApi
import co.asclepio.mobile.feature.account.data.PublicApi
import co.asclepio.mobile.feature.account.data.TenantRepository
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.time.Instant

/** Grafo real (Retrofit, DataStore, SessionManager) contra MockWebServer; solo el cifrador es falso (D1). */
class AccountTestEnv(dir: File, readTimeoutMs: Long = 10_000) {
    val server = MockWebServer().apply { start() }
    val clock = MutableClock(Instant.parse("2026-10-26T17:00:00Z"))
    private val ds = TestDataStore(dir.resolve("session.preferences_pb"))
    val store = SessionStore(ds.dataStore, FakeTokenCipher())
    lateinit var sessionManager: SessionManager
    private val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor { sessionManager.currentToken() })
        .readTimeout(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
    private val retrofit = createRetrofit(server.url("/").toString(), client)
    val publicApi: PublicApi = retrofit.create(PublicApi::class.java)
    val tenantsApi: TenantsApi = retrofit.create(TenantsApi::class.java)
    val meApi: MeApi = retrofit.create(MeApi::class.java)
    val authApi: PatientAuthApi = retrofit.create(PatientAuthApi::class.java)

    init {
        sessionManager = SessionManager(store, meApi, clock)
    }

    val tenantRepository = TenantRepository(tenantsApi)
    val accountRepository = AccountRepository(publicApi, authApi, meApi, sessionManager)

    val tenant = RememberedTenant("t-1", "IPS Uno", "s-1", "Sede Norte")

    suspend fun rememberTenant() {
        sessionManager.saveTenant(tenant)
    }

    fun enqueue(code: Int, body: String = "", retryAfter: String? = null) {
        val response = MockResponse().setResponseCode(code).setBody(body)
            .setHeader("Content-Type", if (code >= 400) "application/problem+json" else "application/json")
        retryAfter?.let { response.setHeader("Retry-After", it) }
        server.enqueue(response)
    }

    fun problem(code: Int, extra: String = "") =
        """{"type":"about:blank","title":"Título de la API","status":$code,"detail":"Detalle de la API"$extra}"""

    fun close() {
        sessionManager.close()
        ds.close()
        server.shutdown()
    }

    companion object {
        const val TOKEN = "eyJhbGciOiJIUzI1NiJ9.paciente.firma"
        const val EXPIRES_AT = "2026-10-27T01:00:00.000Z"
        const val LOGIN_OK = """{"accessToken":"$TOKEN","tokenType":"Bearer","expiresAt":"$EXPIRES_AT"}"""
        fun me(kind: String = "PATIENT", tenantId: String = "t-1", identityVerified: Boolean = false) =
            if (kind == "PATIENT") {
                """{"kind":"PATIENT","id":"a-1","tenantId":"$tenantId","patientId":"p-1","consentVersion":"2026-10-v1","identityVerified":$identityVerified}"""
            } else {
                """{"kind":"$kind","id":"u-1","tenantId":"$tenantId","email":"x@y.co","fullName":"Personal","role":"ADMIN"}"""
            }
    }
}
