package co.asclepio.mobile.core.auth

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * `GET /api/v1/me`: oneOf StaffMeDto | PatientMeDto con discriminador `kind` (D10).
 * Clase plana: un `kind` desconocido no rompe la deserialización. `email` y `fullName` del personal no se declaran,
 * así que se ignoran y nunca se guardan.
 */
@Serializable
data class MeDto(
    val kind: String,
    val id: String,
    val tenantId: String,
    val patientId: String? = null,
    val consentVersion: String? = null,
    val identityVerified: Boolean? = null,
    val role: String? = null,
) {
    val isPatient: Boolean get() = kind == KIND_PATIENT

    companion object {
        const val KIND_PATIENT = "PATIENT"
    }
}

interface MeApi {
    /** Con la sesión guardada: el [AuthInterceptor] añade la cabecera. */
    @GET("api/v1/me")
    suspend fun me(): MeDto

    /** Justo tras el login, antes de guardar la sesión: la cabecera se pasa explícitamente (D9, R19). */
    @GET("api/v1/me")
    suspend fun meWithToken(@Header("Authorization") authorization: String): MeDto
}
