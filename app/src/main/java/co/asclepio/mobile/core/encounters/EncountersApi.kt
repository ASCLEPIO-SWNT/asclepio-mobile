package co.asclepio.mobile.core.encounters

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EncountersApi {
    @GET("api/v1/encounters")
    suspend fun list(
        @Header("Authorization") authorization: String,
        @Query("status") status: String?,
        @Query("siteId") siteId: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): EncounterListDto

    @GET("api/v1/encounters/{id}")
    suspend fun detail(@Path("id") id: String, @Header("Authorization") authorization: String): EncounterDto

    @POST("api/v1/encounters")
    suspend fun createDirect(@Body body: CreateDirectEncounterBody, @Header("Authorization") authorization: String): EncounterDto

    @POST("api/v1/encounters/{id}/cancel")
    suspend fun cancel(@Path("id") id: String, @Body body: StaffReasonRequest, @Header("Authorization") authorization: String): EncounterDto
}
