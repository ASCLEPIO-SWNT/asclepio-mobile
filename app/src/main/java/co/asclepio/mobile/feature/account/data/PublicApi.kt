package co.asclepio.mobile.feature.account.data

import retrofit2.http.GET

/** Endpoints públicos (sin Authorization, R29). */
interface PublicApi {
    @GET("api/v1/public/tenants")
    suspend fun tenants(): List<PublicTenantDto>

    @GET("api/v1/public/consent")
    suspend fun consent(): ConsentDto
}
