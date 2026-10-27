package co.asclepio.mobile.feature.account.data

import retrofit2.http.Body
import retrofit2.http.POST

/** Alta e inicio de sesión del paciente (sin Authorization, R29). */
interface PatientAuthApi {
    @POST("api/v1/auth/patient/accounts")
    suspend fun createAccount(@Body body: CreatePatientAccountRequest): PatientAccountCreatedDto

    @POST("api/v1/auth/patient/login")
    suspend fun login(@Body body: PatientLoginRequest): AccessTokenDto
}
