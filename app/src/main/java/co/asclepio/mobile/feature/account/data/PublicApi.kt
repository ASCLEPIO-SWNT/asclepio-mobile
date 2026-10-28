package co.asclepio.mobile.feature.account.data

import retrofit2.http.GET

/** Endpoints públicos (sin Authorization, R29). `GET /public/tenants` está en `core/tenants/TenantsApi`. */
interface PublicApi {
    @GET("api/v1/public/consent")
    suspend fun consent(): ConsentDto
}
