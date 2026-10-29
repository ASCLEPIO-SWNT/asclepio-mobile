package co.asclepio.mobile.feature.staffaccount.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SessionConfirmation { LOGOUT, CHANGE_ACCESS }

/** Sólo confirmación local; este bloque no inicia consultas de recepción/clínica ni paciente. */
class StaffHomeViewModel(private val session: SessionManager) : ViewModel() {
    private val generation = session.generation
    private val _confirmation = MutableStateFlow<SessionConfirmation?>(null)
    val confirmation = _confirmation.asStateFlow()
    fun requestLogout() { _confirmation.value = SessionConfirmation.LOGOUT }
    fun requestChangeAccess() { _confirmation.value = SessionConfirmation.CHANGE_ACCESS }
    fun cancel() { _confirmation.value = null }
    fun confirm() {
        val action = _confirmation.value ?: return
        _confirmation.value = null
        viewModelScope.launch {
            if (action == SessionConfirmation.LOGOUT) session.logout(generation) else session.showWelcome(generation)
        }
    }
}
