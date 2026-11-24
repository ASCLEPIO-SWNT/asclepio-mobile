package co.asclepio.mobile

import android.app.ActivityManager
import android.content.Context
import androidx.work.WorkManager
import co.asclepio.mobile.feature.interview.DeviceCapability
import co.asclepio.mobile.feature.interview.INTERVIEW_PROMPT_ASSET
import co.asclepio.mobile.feature.interview.LiteRtInterviewer
import co.asclepio.mobile.feature.interview.data.ServerInterviewer
import co.asclepio.mobile.feature.interview.data.ServerNoticeStore
import co.asclepio.mobile.feature.interview.data.SymptomInterviewApi
import co.asclepio.mobile.feature.interview.model.AssistantModelRepository
import co.asclepio.mobile.feature.interview.model.BUILD_MODEL
import co.asclepio.mobile.feature.interview.model.ModelStore
import co.asclepio.mobile.feature.interview.stripPromptComment
import co.asclepio.mobile.feature.interview.ui.InterviewDependencies
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
import co.asclepio.mobile.feature.encounters.queue.QueuePositionStream
import co.asclepio.mobile.feature.patients.data.PatientProfileApi
import co.asclepio.mobile.feature.patients.data.PatientProfileRepository
import co.asclepio.mobile.feature.status.data.HealthApi
import co.asclepio.mobile.core.voice.OnDeviceRecognizer
import co.asclepio.mobile.feature.voice.AndroidOnDeviceRecognizer
import okhttp3.OkHttpClient
import java.time.Clock
import co.asclepio.mobile.core.ui.UiPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual (D4). */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
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

    /** F-10: stream SSE de la posición en la fila sobre el mismo OkHttp (sin logs, R10). */
    val queuePositionStream = QueuePositionStream(okHttp, BuildConfig.API_BASE_URL, sessionManager)

    /** F-08: un reconocedor local por formulario de pre-registro; el ViewModel lo libera en `onCleared`. */
    fun newOnDeviceRecognizer(): OnDeviceRecognizer = AndroidOnDeviceRecognizer(appContext)

    // ---------------------------------------------------------------- F-11-mobile: entrevista de síntomas

    /** Solo la marca "aviso del servidor visto" (R6); el DataStore de preferencias visuales se reescribe entero. */
    private val interviewDataStore = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("asclepio_interview")
    }
    private val serverNoticeStore = ServerNoticeStore(interviewDataStore)
    private val symptomInterviewApi: SymptomInterviewApi = retrofit.create(SymptomInterviewApi::class.java)

    /** Modelo en filesDir/models/ (no va en el APK) y su descarga en WorkManager (R1–R5). */
    private val modelStore = ModelStore(appContext.filesDir, BUILD_MODEL)
    val assistantModelRepository = AssistantModelRepository(WorkManager.getInstance(appContext), modelStore)

    /** "Equipo capaz" de esta sesión de la app (R6, D10). */
    val deviceCapability = DeviceCapability(
        totalMemBytes = {
            val info = ActivityManager.MemoryInfo()
            appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            info.totalMem
        },
        modelVerified = modelStore::isVerified,
    )

    private val interviewPrompt: String by lazy {
        stripPromptComment(appContext.assets.open(INTERVIEW_PROMPT_ASSET).bufferedReader().use { it.readText() })
    }

    /** Dependencias de una entrevista: motor local (un Engine por entrevista, D7) o el del servidor. */
    fun interviewDependencies(): InterviewDependencies = InterviewDependencies(
        capability = deviceCapability,
        noticeSeen = serverNoticeStore::seen,
        markNoticeSeen = serverNoticeStore::markSeen,
        localInterviewer = { LiteRtInterviewer(modelStore.modelFile.path, appContext.cacheDir.path, interviewPrompt) },
        serverInterviewer = { ServerInterviewer(symptomInterviewApi, sessionManager) },
        downloadModel = assistantModelRepository::download,
        modelVerified = modelStore::isVerified,
    )
}
