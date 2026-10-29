package co.asclepio.mobile

import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.createRetrofit
import co.asclepio.mobile.core.tenants.TenantsApi
import co.asclepio.mobile.feature.reception.data.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import co.asclepio.mobile.core.network.appJson
import okhttp3.OkHttpClient
import java.io.File
import java.time.Instant

/** Retrofit, repositorios y sesión reales; solo reloj y cifrador de prueba del harness existente. */
class StaffEncountersTestEnv(dir: File) {
    private val patient = PatientTestEnv(dir)
    val server get() = patient.server
    val sessionManager get() = patient.sessionManager
    val store get() = patient.store
    private val retrofit = createRetrofit(server.url("/").toString(), OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor { sessionManager.currentToken() }).build())
    val changes = StaffDataChanges()
    val encounters = EncountersRepository(retrofit.create(EncountersApi::class.java), sessionManager, changes)
    val reception = ReceptionRepository(retrofit.create(ReceptionApi::class.java), sessionManager, changes)
    val sites = StaffSitesRepository(retrofit.create(TenantsApi::class.java), sessionManager)

    suspend fun login(role: StaffRole = StaffRole.RECEPTION, token: String = "staff-token") {
        sessionManager.selectAccess(AccessMode.STAFF)
        val attempt = sessionManager.beginLogin(AccessMode.STAFF)
        check(sessionManager.completeLogin(attempt, token, Instant.parse("2026-10-28T01:00:00Z"),
            MeDto("STAFF", STAFF, TENANT, role = role.name, email = "demo@example.com", fullName = "DEV Personal")))
    }
    suspend fun patientLogin() = patient.login()
    fun enqueue(status: Int, body: String, retryAfter: String? = null) = patient.enqueue(status, body, retryAfter)
    fun problem(status: Int) = patient.problem(status)
    fun close() = patient.close()

    companion object {
        const val TENANT = "99999999-9999-9999-9999-999999999999"
        const val SITE = "22222222-2222-2222-2222-222222222222"
        const val STAFF = "77777777-7777-7777-7777-777777777777"
        const val PATIENT = "44444444-4444-4444-4444-444444444444"
        const val ENCOUNTER = "55555555-5555-5555-5555-555555555555"
        const val PENDING = "66666666-6666-6666-6666-666666666666"
        fun patient() = """{"id":"$PATIENT","documentType":"TI","documentNumber":"DEV-123","fullName":"DEV José Müller","birthDate":"2010-05-17","estimatedAgeYears":null,"sex":"F","isNN":false}"""
        fun encounter(status: String = "WAITING_TRIAGE") = """{"id":"$ENCOUNTER","siteId":"$SITE","siteName":"Sede Norte","status":"$status","origin":"DIRECT","arrivalAt":"2026-10-27T15:00:00.000Z","preRegistrationId":null,"preRegisteredAt":null,"chiefComplaintText":"Motivo privado literal","complaintSource":"TYPED","cancellationReason":null,"registeredBy":{"id":"$STAFF","fullName":"DEV Personal"},"patient":${patient()},"events":[{"id":"90071992547409931234","type":"STATUS_CHANGED","fromStatus":"REGISTERED","toStatus":"WAITING_TRIAGE","payload":{},"actor":{"type":"STAFF","id":"$STAFF","fullName":"DEV Personal"},"occurredAt":"2026-10-27T15:00:00.000Z"},{"id":"90071992547409931235","type":"FUTURE_EVENT","fromStatus":null,"toStatus":null,"payload":{"private":"no imprimir"},"actor":{"type":"SYSTEM","id":null,"fullName":null},"occurredAt":"2026-10-27T15:00:01.000Z"}]}"""
        fun summary() = JsonObject(appJson.parseToJsonElement(encounter()).jsonObject.filterKeys { it != "events" }).toString()
        fun pending() = """{"id":"$PENDING","siteId":"$SITE","siteName":"Sede Norte","preRegisteredAt":"2026-10-27T14:30:00.000Z","expiresAt":"2026-10-27T20:30:00.000Z","chiefComplaintText":"Motivo privado literal","complaintSource":"VOICE_ON_DEVICE","identityVerified":true,"patient":${patient()}}"""
        fun rejected() = """{"id":"$PENDING","status":"REJECTED","resolvedAt":"2026-10-27T15:00:00.000Z"}"""
    }
}
