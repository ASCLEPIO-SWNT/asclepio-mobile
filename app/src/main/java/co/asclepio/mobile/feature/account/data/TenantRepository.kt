package co.asclepio.mobile.feature.account.data

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.tenants.PublicTenantDto
import co.asclepio.mobile.core.tenants.TenantsApi

class TenantRepository(private val tenantsApi: TenantsApi) {
    suspend fun tenants(): ApiResult<List<PublicTenantDto>> = apiCall { tenantsApi.tenants() }
}
