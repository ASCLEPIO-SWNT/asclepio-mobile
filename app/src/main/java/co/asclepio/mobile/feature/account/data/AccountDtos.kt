package co.asclepio.mobile.feature.account.data

import kotlinx.serialization.Serializable

// DTOs escritos a mano según el snapshot 0.2.1 de ../asclepio-api/openapi/asclepio-api.v1.json.

@Serializable
enum class DocumentType { CC, CE, PA }

@Serializable
enum class Sex { M, F, X }

/** PublicSiteDto. */
@Serializable
data class PublicSiteDto(val id: String, val name: String)

/** PublicTenantDto. */
@Serializable
data class PublicTenantDto(val id: String, val name: String, val sites: List<PublicSiteDto>)

/** ConsentDto: `text` en Markdown. */
@Serializable
data class ConsentDto(val version: String, val text: String)

/** CreatePatientAccountDto. Sin propiedades opcionales: el cuerpo lleva exactamente estos campos (R10). */
@Serializable
data class CreatePatientAccountRequest(
    val tenantId: String,
    val documentType: DocumentType,
    val documentNumber: String,
    val fullName: String,
    val birthDate: String,
    val sex: Sex,
    val password: String,
    val consentAccepted: Boolean,
    val consentVersion: String,
) {
    override fun toString(): String =
        "CreatePatientAccountRequest(tenantId=$tenantId, documentType=$documentType, documentNumber=***, " +
            "fullName=***, birthDate=***, sex=$sex, password=***, consentAccepted=$consentAccepted, " +
            "consentVersion=$consentVersion)"
}

/** PatientAccountCreatedDto. */
@Serializable
data class PatientAccountCreatedDto(val id: String, val patientId: String)

/** PatientLoginDto. */
@Serializable
data class PatientLoginRequest(
    val tenantId: String,
    val documentType: DocumentType,
    val documentNumber: String,
    val password: String,
) {
    override fun toString(): String =
        "PatientLoginRequest(tenantId=$tenantId, documentType=$documentType, documentNumber=***, password=***)"
}

/** AccessTokenDto: JWT de 8 h, sin refresh token. */
@Serializable
data class AccessTokenDto(val accessToken: String, val tokenType: String, val expiresAt: String) {
    override fun toString(): String = "AccessTokenDto(accessToken=***, tokenType=$tokenType, expiresAt=$expiresAt)"
}
