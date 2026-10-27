package co.asclepio.mobile.feature.status.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** Esquema HealthDto del contrato. */
@Serializable
data class HealthDto(val status: String, val time: String)

interface HealthApi {
    @GET("api/v1/health")
    suspend fun health(): HealthDto
}
