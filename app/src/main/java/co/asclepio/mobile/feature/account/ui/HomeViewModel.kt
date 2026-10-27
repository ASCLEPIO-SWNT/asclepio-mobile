package co.asclepio.mobile.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Inicio (R31): sin documento ni nombre del paciente. */
data class HomeUiState(
    val tenant: RememberedTenant? = null,
    val identityVerified: Boolean? = null,
    val verifying: Boolean = true,
    val showLogoutDialog: Boolean = false,
) {
    val showIdentityNotice: Boolean get() = identityVerified == false
}

class HomeViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(tenant = sessionManager.rememberedTenant()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // R28: al abrir con sesión activa se pide /me; un 401 borra la sesión y la navegación vuelve al login.
        viewModelScope.launch {
            val me = sessionManager.verifySession()
            _state.update { it.copy(identityVerified = me?.identityVerified, verifying = false) }
        }
    }

    fun requestLogout() = _state.update { it.copy(showLogoutDialog = true) }

    fun cancelLogout() = _state.update { it.copy(showLogoutDialog = false) }

    /** R30: borra la sesión sin llamar a ningún endpoint (el contrato no tiene logout). */
    fun confirmLogout() {
        _state.update { it.copy(showLogoutDialog = false) }
        viewModelScope.launch { sessionManager.logout() }
    }
}
