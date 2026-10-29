package co.asclepio.mobile.core.auth

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header

/** Variante plana tolerante a campos desconocidos; toPrincipal exige la variante publicada completa. */
@Serializable
data class MeDto(
    val kind: String,
    val id: String,
    val tenantId: String,
    val patientId: String? = null,
    val consentVersion: String? = null,
    val identityVerified: Boolean? = null,
    val role: String? = null,
    val email: String? = null,
    val fullName: String? = null,
) {
    override fun toString() = "MeDto(kind=$kind, identity=***, patient=***, email=***, fullName=***, role=$role)"

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
