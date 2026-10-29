package co.asclepio.mobile.core.auth

enum class AccessMode { PATIENT, STAFF }
enum class StaffRole { RECEPTION, NURSE, PHYSICIAN, ADMIN }

/** Identidad verificada en /me. Vive en RAM; nunca se serializa a navegación ni disco. */
sealed interface VerifiedPrincipal {
    val id: String
    val tenantId: String
    val mode: AccessMode

    data class Patient(
        override val id: String,
        override val tenantId: String,
        val patientId: String,
        val consentVersion: String,
        val identityVerified: Boolean,
        val tenant: RememberedTenant,
    ) : VerifiedPrincipal {
        override val mode = AccessMode.PATIENT
        override fun toString() = "Patient(identity=***, tenant=***, identityVerified=$identityVerified)"
    }

    data class Staff(
        override val id: String,
        override val tenantId: String,
        val fullName: String,
        val role: StaffRole,
    ) : VerifiedPrincipal {
        override val mode = AccessMode.STAFF
        override fun toString() = "Staff(identity=***, fullName=***, role=$role)"
    }
}

/** Datos requeridos de la variante publicada; valores desconocidos nunca conceden acceso. */
fun MeDto.toPrincipal(tenant: RememberedTenant?): VerifiedPrincipal? {
    if (id.isBlank() || tenantId.isBlank()) return null
    return when (kind) {
        "PATIENT" -> if (tenant != null && tenantId == tenant.tenantId && !patientId.isNullOrBlank() &&
            !consentVersion.isNullOrBlank() && identityVerified != null
        ) VerifiedPrincipal.Patient(id, tenantId, patientId, consentVersion, identityVerified, tenant) else null
        "STAFF" -> {
            val knownRole = StaffRole.entries.firstOrNull { it.name == role } ?: return null
            if (fullName.isNullOrBlank() || email.isNullOrBlank() || !email.contains('@')) null else
                VerifiedPrincipal.Staff(id, tenantId, fullName, knownRole)
        }
        else -> null
    }
}
