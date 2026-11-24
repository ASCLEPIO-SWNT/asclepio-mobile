package co.asclepio.mobile.feature.encounters.data

import co.asclepio.mobile.core.interview.SymptomInterviewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// DTOs escritos a mano según el snapshot 0.11.0 de ../asclepio-api/openapi/asclepio-api.v1.json: CreatePreRegistrationDto
// añade `interview` (SymptomInterviewDto, opcional); PatientPreRegistrationDto no cambia desde 0.4.0.

/** Origen del motivo (D8 de F-02, D5 de F-08): [VOICE_ON_DEVICE] desde que el borrador acepta un dictado local. */
@Serializable
enum class ComplaintSource {
    @SerialName("TYPED")
    TYPED,

    @SerialName("VOICE_ON_DEVICE")
    VOICE_ON_DEVICE,
}

/**
 * CreatePreRegistrationDto. El cuerpo lleva exactamente siteId, chiefComplaintText y complaintSource (R10), sin horas
 * (R22) ni `patientId`/`tenantId` (R26); `interview` (contrato 0.11.0, F-11-mobile R12, R14) solo va si hay entrevista
 * (`encodeDefaults = false`). El `toString()` enmascara el motivo y la entrevista (R27; R16 de F-11-mobile).
 */
@Serializable
data class CreatePreRegistrationRequest(
    val siteId: String,
    val chiefComplaintText: String,
    val complaintSource: ComplaintSource,
    val interview: SymptomInterviewDto? = null,
) {
    override fun toString(): String =
        "CreatePreRegistrationRequest(siteId=$siteId, chiefComplaintText=***, complaintSource=$complaintSource, interview=$interview)"
}

/** EncounterRefDto. `status` es texto para que un valor nuevo de la API no rompa la lista (D5). */
@Serializable
data class EncounterRefDto(val id: String, val status: String)

/**
 * PatientPreRegistrationDto: solo los campos del contrato; cualquier otro (un nivel de triage, una hora de llegada…)
 * se ignora al deserializar (D6, R21, R22). `status` es el estado efectivo calculado por la API (D5).
 */
@Serializable
data class PatientPreRegistrationDto(
    val id: String,
    val siteId: String,
    val siteName: String,
    val status: String,
    val preRegisteredAt: String,
    val expiresAt: String,
    val encounter: EncounterRefDto?,
)

/** Pre-registros del paciente autenticado (Bearer del paciente; el paciente sale del token, R26). */
interface PreRegistrationsApi {
    @POST("api/v1/patient/pre-registrations")
    suspend fun create(@Body body: CreatePreRegistrationRequest, @Header("Authorization") authorization: String): PatientPreRegistrationDto

    /** Ordenados por `preRegisteredAt` descendente (R12 de F-02-api): el primero es el último. */
    @GET("api/v1/patient/pre-registrations")
    suspend fun list(@Header("Authorization") authorization: String): List<PatientPreRegistrationDto>
}
