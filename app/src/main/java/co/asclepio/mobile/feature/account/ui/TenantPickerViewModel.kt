package co.asclepio.mobile.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.PublicTenantDto
import co.asclepio.mobile.feature.account.data.TenantRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TenantPickerUiState {
    data object Loading : TenantPickerUiState

    data class Success(
        val tenants: List<PublicTenantDto>,
        val selectedTenantId: String? = null,
        val selectedSiteId: String? = null,
        val saving: Boolean = false,
        val confirmed: Boolean = false,
    ) : TenantPickerUiState {
        val selectedTenant: PublicTenantDto? get() = tenants.firstOrNull { it.id == selectedTenantId }

        /** La sede es obligatoria para continuar (R2). */
        val canContinue: Boolean get() = selectedSiteId != null && !saving

        /** Una IPS sin sedes se muestra deshabilitada (R1). */
        fun isEnabled(tenant: PublicTenantDto): Boolean = tenant.sites.isNotEmpty()
    }

    /** Lista vacía (R4) o fallo (R5); `retryEnabled` es `false` mientras dura un 429 (R22). */
    data class Error(val message: UiMessage, val retryEnabled: Boolean) : TenantPickerUiState
}

class TenantPickerViewModel(
    private val repository: TenantRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<TenantPickerUiState>(TenantPickerUiState.Loading)
    val state: StateFlow<TenantPickerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() {
        val current = _state.value
        if (current is TenantPickerUiState.Error && current.retryEnabled) load()
    }

    private fun load() {
        _state.value = TenantPickerUiState.Loading
        viewModelScope.launch {
            when (val result = repository.tenants()) {
                is ApiResult.Success -> _state.value = if (result.value.isEmpty()) {
                    TenantPickerUiState.Error(UiMessage(R.string.error_no_tenants), retryEnabled = true)
                } else {
                    TenantPickerUiState.Success(result.value)
                }
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    val error = TenantPickerUiState.Error(tenantsErrorMessage(result.error), retryEnabled = wait == null)
                    _state.value = error
                    if (wait != null) {
                        delay(wait * 1_000L)
                        _state.update { if (it == error) error.copy(retryEnabled = true) else it }
                    }
                }
            }
        }
    }

    fun selectTenant(tenantId: String) = _state.update { s ->
        if (s !is TenantPickerUiState.Success || s.saving) return@update s
        val tenant = s.tenants.firstOrNull { it.id == tenantId }
        if (tenant == null || !s.isEnabled(tenant) || s.selectedTenantId == tenantId) s
        else s.copy(selectedTenantId = tenantId, selectedSiteId = null)
    }

    fun selectSite(siteId: String) = _state.update { s ->
        if (s is TenantPickerUiState.Success && !s.saving && s.selectedTenant?.sites?.any { it.id == siteId } == true) {
            s.copy(selectedSiteId = siteId)
        } else {
            s
        }
    }

    /** Guarda la IPS recordada sin el documento (R2). */
    fun confirm() {
        val s = _state.value as? TenantPickerUiState.Success ?: return
        if (!s.canContinue) return
        val tenant = s.selectedTenant ?: return
        val site = tenant.sites.first { it.id == s.selectedSiteId }
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            sessionManager.saveTenant(RememberedTenant(tenant.id, tenant.name, site.id, site.name))
            _state.update { (it as? TenantPickerUiState.Success)?.copy(saving = false, confirmed = true) ?: it }
        }
    }
}
