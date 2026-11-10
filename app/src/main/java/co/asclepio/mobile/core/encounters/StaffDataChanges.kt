package co.asclepio.mobile.core.encounters

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class StaffResource { Pending, Encounters, Detail }

/** Solo invalidación de recursos de una generación; sin DTOs, motivos, pacientes ni listas. */
data class StaffDataChange(val generation: Long, val resources: Set<StaffResource>, val encounterId: String? = null)

class StaffDataChanges {
    private val mutable = MutableSharedFlow<StaffDataChange>(extraBufferCapacity = 16)
    val changes: SharedFlow<StaffDataChange> = mutable.asSharedFlow()
    internal suspend fun publish(change: StaffDataChange) { mutable.emit(change) }
}
