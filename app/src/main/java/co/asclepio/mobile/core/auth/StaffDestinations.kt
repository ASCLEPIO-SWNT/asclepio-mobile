package co.asclepio.mobile.core.auth

import kotlinx.serialization.Serializable

@Serializable
enum class StaffDestination { RECEPTION, TRIAGE, MEDICINE, ADMINISTRATION, QUEUE, ENCOUNTERS, REPORTS }

val StaffRole.home: StaffDestination get() = when (this) {
    StaffRole.RECEPTION -> StaffDestination.RECEPTION
    StaffRole.NURSE -> StaffDestination.TRIAGE
    StaffRole.PHYSICIAN -> StaffDestination.MEDICINE
    StaffRole.ADMIN -> StaffDestination.ADMINISTRATION
}
val StaffRole.destinations: Set<StaffDestination> get() = when (this) {
    StaffRole.RECEPTION -> setOf(home, StaffDestination.ENCOUNTERS)
    StaffRole.NURSE, StaffRole.PHYSICIAN -> setOf(home, StaffDestination.QUEUE, StaffDestination.ENCOUNTERS)
    StaffRole.ADMIN -> setOf(home, StaffDestination.REPORTS, StaffDestination.QUEUE, StaffDestination.ENCOUNTERS)
}
