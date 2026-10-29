package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.core.auth.AccessMode
import co.asclepio.mobile.core.auth.AuthHandle
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.auth.VerifiedPrincipal
import co.asclepio.mobile.core.auth.authenticatedCall
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.ProblemDetails

internal class ScopedStaffResult<T>(val generation: Long, val result: ApiResult<T>)
private sealed interface StaffPermission<out T> {
    data object Denied : StaffPermission<Nothing>
    class Allowed<T>(val value: T) : StaffPermission<T>
}

/** El rol se comprueba en el AuthHandle capturado, nunca en un perfil leído antes de esperar al mutex. */
internal suspend fun <T> SessionManager.staffRequest(
    allowedRoles: Set<StaffRole>,
    block: suspend (AuthHandle) -> T,
): ScopedStaffResult<T> {
    var capturedGeneration: Long? = null
    val scoped = authenticatedCall(AccessMode.STAFF) { handle ->
        capturedGeneration = handle.generation
        val principal = handle.principal as VerifiedPrincipal.Staff
        if (principal.role !in allowedRoles) StaffPermission.Denied else StaffPermission.Allowed(block(handle))
    }
    val result: ApiResult<T> = when (scoped) {
        is ApiResult.Failure -> scoped
        is ApiResult.Success -> when (val permission = scoped.value) {
            StaffPermission.Denied -> ApiResult.Failure(ApiError.Http(ProblemDetails(status = 403)))
            is StaffPermission.Allowed -> ApiResult.Success(permission.value)
        }
    }
    return ScopedStaffResult(checkNotNull(capturedGeneration), result)
}

internal val CONSULTATION_ROLES = StaffRole.entries.toSet()
internal val RECEPTION_ROLES = setOf(StaffRole.RECEPTION)
internal val CANCELLATION_ROLES = setOf(StaffRole.RECEPTION, StaffRole.NURSE)
