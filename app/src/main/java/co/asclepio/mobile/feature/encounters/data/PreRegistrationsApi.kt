package co.asclepio.mobile.feature.encounters.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// DTOs escritos a mano según el snapshot 0.4.0 de ../asclepio-api/openapi/asclepio-api.v1.json.

/** Origen del motivo (D8). En F-02 siempre es [TYPED]; F-08 añadirá el dictado en el dispositivo. */
@Serializable
enum class ComplaintSource {
    @SerialName("TYPED")
    TYPED,

    @SerialName("VOICE_ON_DEVICE")
    VOICE_ON_DEVICE,
}

/**
 * CreatePreRegistrationDto. Sin valores por defecto: el cuerpo lleva exactamente estos tres campos (R10),
 * sin horas (R22) ni `patientId`/`tenantId` (R26). El `toString()` enmascara el motivo (R27).
 */
@Serializable
data class CreatePreRegistrationRequest(
    val siteId: String,
    val chiefComplaintText: String,
    val complaintSource: ComplaintSource,
) {
    override fun toString(): String =
        "CreatePreRegistrationRequest(siteId=$siteId, chiefComplaintText=***, complaintSource=$complaintSource)"
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
