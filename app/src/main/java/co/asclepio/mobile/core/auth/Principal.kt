package co.asclepio.mobile.core.auth

/** Identidad verificada en /me: la app es solo del paciente. Vive en RAM; nunca se serializa a navegación ni disco. */
sealed interface VerifiedPrincipal {
    val id: String
    val tenantId: String

    data class Patient(
        override val id: String,
        override val tenantId: String,
        val patientId: String,
        val consentVersion: String,
        val identityVerified: Boolean,
        val tenant: RememberedTenant,
    ) : VerifiedPrincipal {
        override fun toString() = "Patient(identity=***, tenant=***, identityVerified=$identityVerified)"
    }
}

/** Solo un paciente completo de la IPS recordada es un principal; cualquier otro /me (incluido STAFF) no concede acceso. */
fun MeDto.toPrincipal(tenant: RememberedTenant?): VerifiedPrincipal.Patient? {
    if (id.isBlank() || tenantId.isBlank() || !isPatient || tenant == null || tenantId != tenant.tenantId) return null
    if (patientId.isNullOrBlank() || consentVersion.isNullOrBlank() || identityVerified == null) return null
    return VerifiedPrincipal.Patient(id, tenantId, patientId, consentVersion, identityVerified, tenant)
}
