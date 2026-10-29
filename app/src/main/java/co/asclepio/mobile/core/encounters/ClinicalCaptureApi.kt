package co.asclepio.mobile.core.encounters

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Tres consumos publicados0.4.0. Nunca envía actor, hora o tenant en el cuerpo. */
interface ClinicalCaptureApi {
    @GET("api/v1/encounters/{id}/clinical-capture")
    suspend fun getCapture(@Path("id") id: String, @Header("Authorization") authorization: String): ClinicalCaptureDto

    @POST("api/v1/encounters/{id}/vital-signs")
    suspend fun recordVitalSigns(@Path("id") id: String, @Body body: CreateVitalSignsRequest, @Header("Authorization") authorization: String): VitalSignsDto

    @POST("api/v1/encounters/{id}/symptom-findings")
    suspend fun recordFindings(@Path("id") id: String, @Body body: CreateSymptomFindingsRequest, @Header("Authorization") authorization: String): SymptomFindingListDto
}
