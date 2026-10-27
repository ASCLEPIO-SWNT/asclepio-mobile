package co.asclepio.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** Esquema HealthDto del contrato 0.1.0. */
@Serializable
data class HealthDto(val status: String, val time: String)

interface HealthApi {
    @GET("api/v1/health")
    suspend fun health(): HealthDto
}
