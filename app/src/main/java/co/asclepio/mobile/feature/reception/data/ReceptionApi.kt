package co.asclepio.mobile.feature.reception.data

import co.asclepio.mobile.core.encounters.EncounterDto
import co.asclepio.mobile.core.encounters.StaffPatientDto
import co.asclepio.mobile.core.encounters.StaffReasonRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ReceptionApi {
    @GET("api/v1/pre-registrations")
    suspend fun pending(@Query("siteId") siteId: String?, @Header("Authorization") authorization: String): List<PendingPreRegistrationDto>

    @POST("api/v1/pre-registrations/{id}/confirm")
    suspend fun confirm(@Path("id") id: String, @Body body: ConfirmPreRegistrationRequest, @Header("Authorization") authorization: String): EncounterDto

    @POST("api/v1/pre-registrations/{id}/reject")
    suspend fun reject(@Path("id") id: String, @Body body: StaffReasonRequest, @Header("Authorization") authorization: String): RejectedPreRegistrationDto

    @GET("api/v1/patients")
    suspend fun search(@Query("documentType") documentType: String, @Query("documentNumber") documentNumber: String, @Header("Authorization") authorization: String): List<StaffPatientDto>

    @POST("api/v1/patients")
    suspend fun createPatient(@Body body: CreateStaffPatientRequest, @Header("Authorization") authorization: String): StaffPatientDto
}
