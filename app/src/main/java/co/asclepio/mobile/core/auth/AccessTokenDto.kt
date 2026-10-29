package co.asclepio.mobile.core.auth

import kotlinx.serialization.Serializable

/** AccessTokenDto 0.4.0; ambos accesos usan el mismo token único, sin refresh token. */
@Serializable
data class AccessTokenDto(val accessToken: String, val tokenType: String, val expiresAt: String) {
    override fun toString() = "AccessTokenDto(accessToken=***, tokenType=$tokenType, expiresAt=$expiresAt)"
}
