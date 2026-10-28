package co.asclepio.mobile.feature.patients.data

import co.asclepio.mobile.core.model.Sex
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

// DTOs escritos a mano según el snapshot 0.4.0 de ../asclepio-api/openapi/asclepio-api.v1.json.

/**
 * PatientProfileDto. `documentType` es texto libre en el contrato (puede ser TI o RC si lo creó recepción).
 * `birthDate` es `YYYY-MM-DD`. El `toString()` enmascara documento, nombre y fecha de nacimiento (R27).
 */
@Serializable
data class PatientProfileDto(
    val id: String,
    val documentType: String?,
    val documentNumber: String?,
    val fullName: String?,
    val birthDate: String?,
    val sex: Sex?,
    val editable: Boolean,
) {
    override fun toString(): String =
        "PatientProfileDto(id=$id, documentType=$documentType, documentNumber=***, fullName=***, birthDate=***, " +
            "sex=$sex, editable=$editable)"
}

/**
 * UpdatePatientProfileDto. Solo viajan los campos distintos de `null` (el `Json` común usa `encodeDefaults = false`),
 * así que el cuerpo lleva únicamente lo que cambió (D9, R3). No tiene documento, `patientId` ni `tenantId` (R26).
 */
@Serializable
data class UpdatePatientProfileRequest(
    val fullName: String? = null,
    val birthDate: String? = null,
    val sex: Sex? = null,
) {
    val isEmpty: Boolean get() = fullName == null && birthDate == null && sex == null

    override fun toString(): String =
        "UpdatePatientProfileRequest(fullName=${fullName?.let { "***" }}, birthDate=${birthDate?.let { "***" }}, sex=$sex)"
}

/** Datos propios del paciente (Bearer del paciente; el paciente sale del token, R26). */
interface PatientProfileApi {
    @GET("api/v1/patient/profile")
    suspend fun profile(): PatientProfileDto

    @PATCH("api/v1/patient/profile")
    suspend fun update(@Body body: UpdatePatientProfileRequest): PatientProfileDto
}
