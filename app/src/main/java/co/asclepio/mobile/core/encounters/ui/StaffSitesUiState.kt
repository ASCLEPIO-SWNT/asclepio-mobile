package co.asclepio.mobile.core.encounters.ui

import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.UiMessage

data class StaffSitesUiState(
    val sites: List<PublicSiteDto> = emptyList(),
    val loading: Boolean = true,
    val message: UiMessage? = null,
    val retryEnabled: Boolean = true,
) {
    val available: Boolean get() = !loading && message == null && sites.isNotEmpty()
}
