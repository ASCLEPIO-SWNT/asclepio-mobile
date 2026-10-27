package co.asclepio.mobile.feature.account.data

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall

class TenantRepository(private val publicApi: PublicApi) {
    suspend fun tenants(): ApiResult<List<PublicTenantDto>> = apiCall { publicApi.tenants() }
}
