package co.asclepio.mobile.feature.staffaccount.data

import co.asclepio.mobile.core.auth.AccessTokenDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** StaffLoginDto 0.4.0: sin tenant, sede ni metadatos adicionales. */
@Serializable
data class StaffLoginRequest(val email: String, val password: String) {
    override fun toString() = "StaffLoginRequest(email=***, password=***)"
}
interface StaffAuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: StaffLoginRequest): AccessTokenDto
}
