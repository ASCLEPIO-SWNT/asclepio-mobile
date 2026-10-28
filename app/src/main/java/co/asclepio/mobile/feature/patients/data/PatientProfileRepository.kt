package co.asclepio.mobile.feature.patients.data

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.patientCall
import co.asclepio.mobile.core.network.ApiResult

/** Perfil del paciente autenticado. Un 401 cierra la sesión (R23). Nada se guarda en disco (R27). */
class PatientProfileRepository(private val api: PatientProfileApi, private val sessionManager: SessionManager) {
    suspend fun profile(): ApiResult<PatientProfileDto> = sessionManager.patientCall { api.profile() }

    suspend fun update(request: UpdatePatientProfileRequest): ApiResult<PatientProfileDto> =
        sessionManager.patientCall { api.update(request) }
}
