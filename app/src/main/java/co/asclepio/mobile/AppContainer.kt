package co.asclepio.mobile

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import co.asclepio.mobile.core.auth.AuthInterceptor
import co.asclepio.mobile.core.auth.KeystoreTokenCipher
import co.asclepio.mobile.core.auth.MeApi
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.SessionStore
import co.asclepio.mobile.core.network.createRetrofit
import co.asclepio.mobile.core.tenants.TenantsApi
import co.asclepio.mobile.feature.account.data.AccountRepository
import co.asclepio.mobile.feature.account.data.PatientAuthApi
import co.asclepio.mobile.feature.account.data.PublicApi
import co.asclepio.mobile.feature.account.data.TenantRepository
import co.asclepio.mobile.feature.encounters.data.PreRegistrationRepository
import co.asclepio.mobile.feature.encounters.data.PreRegistrationsApi
import co.asclepio.mobile.feature.patients.data.PatientProfileApi
import co.asclepio.mobile.feature.patients.data.PatientProfileRepository
import co.asclepio.mobile.feature.status.data.HealthApi
import okhttp3.OkHttpClient
import java.time.Clock
import co.asclepio.mobile.core.ui.UiPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual (D4). */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiDataStore = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("asclepio_ui")
    }
    val uiPreferencesRepository = UiPreferencesRepository(uiDataStore, applicationScope)

    // files/datastore/asclepio_session.preferences_pb (excluido de copias de seguridad).
    private val dataStore = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("asclepio_session")
    }
    private val sessionStore = SessionStore(dataStore, KeystoreTokenCipher())

    private val okHttp = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor { sessionManager.currentToken() })
        .build()
    private val retrofit = createRetrofit(BuildConfig.API_BASE_URL, okHttp)

    val healthApi: HealthApi = retrofit.create(HealthApi::class.java)
    private val publicApi: PublicApi = retrofit.create(PublicApi::class.java)
    private val tenantsApi: TenantsApi = retrofit.create(TenantsApi::class.java)
    private val authApi: PatientAuthApi = retrofit.create(PatientAuthApi::class.java)
    private val meApi: MeApi = retrofit.create(MeApi::class.java)
    private val profileApi: PatientProfileApi = retrofit.create(PatientProfileApi::class.java)
    private val preRegistrationsApi: PreRegistrationsApi = retrofit.create(PreRegistrationsApi::class.java)

    val sessionManager = SessionManager(sessionStore, meApi, clock)
    val tenantRepository = TenantRepository(tenantsApi)
    val accountRepository = AccountRepository(publicApi, authApi, meApi, sessionManager)
    val profileRepository = PatientProfileRepository(profileApi, sessionManager)
    val preRegistrationRepository = PreRegistrationRepository(preRegistrationsApi, tenantsApi, sessionManager)
}
