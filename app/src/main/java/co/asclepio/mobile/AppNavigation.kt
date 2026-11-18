package co.asclepio.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.SystemClock
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import co.asclepio.mobile.feature.encounters.queue.QueuePositionViewModel
import co.asclepio.mobile.feature.encounters.ui.QueuePositionCard
import co.asclepio.mobile.feature.encounters.ui.followsQueue
import androidx.compose.ui.platform.LocalConfiguration
import co.asclepio.mobile.core.ui.guide.*
import co.asclepio.mobile.feature.encounters.ui.MyRequestsScreen
import co.asclepio.mobile.feature.encounters.ui.MyRequestsViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.ui.AccessDeniedScreen
import co.asclepio.mobile.core.ui.SessionVerificationScreen
import co.asclepio.mobile.core.ui.EntryBackdrop
import co.asclepio.mobile.feature.account.ui.HomeScreen
import co.asclepio.mobile.feature.account.ui.HomeViewModel
import co.asclepio.mobile.feature.account.ui.LoginScreen
import co.asclepio.mobile.feature.account.ui.LoginViewModel
import co.asclepio.mobile.feature.account.ui.SignUpScreen
import co.asclepio.mobile.feature.account.ui.SignUpViewModel
import co.asclepio.mobile.feature.account.ui.TenantPickerScreen
import co.asclepio.mobile.feature.account.ui.TenantPickerViewModel
import co.asclepio.mobile.feature.encounters.ui.PreRegistrationScreen
import co.asclepio.mobile.feature.encounters.ui.PreRegistrationStatusPane
import co.asclepio.mobile.feature.encounters.ui.PreRegistrationStatusViewModel
import co.asclepio.mobile.feature.encounters.ui.PreRegistrationViewModel
import co.asclepio.mobile.feature.patients.ui.ProfileScreen
import co.asclepio.mobile.feature.patients.ui.ProfileViewModel
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.feature.status.ui.StatusScreen
import co.asclepio.mobile.feature.status.ui.StatusViewModel
import co.asclepio.mobile.feature.voice.startDictationStoppingGuide
import co.asclepio.mobile.feature.voice.ui.DictationControls
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Rutas de Navigation 3 (D3): solo las del paciente. */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object TenantPicker : Route

    /** Public entry identity: confirming even the same IPS discards the previous login VM/draft. */
    @Serializable data class Login(val entryGeneration: Long = 0L) : Route

    @Serializable data object SignUp : Route

    @Serializable data object Home : Route

    /** "Mis datos" (F-02). */
    @Serializable data object Profile : Route

    @Serializable data object MyRequests : Route

    /** Nuevo pre-registro (F-02). */
    @Serializable data object PreRegistration : Route

    /** Pantalla de estado de F-00, accesible desde el login solo en debug. */
    @Serializable data object Status : Route
}

/** Raíz de la pila según la sesión (R1, R3, R27, R28, R30, R31). `null` mientras se carga o verifica. */
fun rootRoute(state: SessionState): Route? = when (state) {
    SessionState.Loading, SessionState.Verifying, is SessionState.VerificationError -> null
    SessionState.NoTenant -> Route.TenantPicker
    is SessionState.LoggedOut -> Route.Login()
    is SessionState.Authenticated -> Route.Home
}

/** Se evalúa antes de crear el Composable/VM de cualquier destino, también con una pila restaurada. */
fun canAccessRoute(state: SessionState, route: Route): Boolean = when (state) {
    is SessionState.Authenticated -> route in setOf(Route.Home, Route.Profile, Route.PreRegistration, Route.MyRequests)
    SessionState.NoTenant -> route == Route.TenantPicker
    is SessionState.LoggedOut -> route is Route.Login || route in setOf(Route.TenantPicker, Route.SignUp) || (BuildConfig.DEBUG && route == Route.Status)
    else -> false
}

/** Closed resource catalog: no identity, patient data or form values can become guide text. */
fun routeGuideDestination(route: Route): GuideDestination = when (route) {
    Route.TenantPicker -> GuideDestination.TenantPicker
    is Route.Login -> GuideDestination.PatientLogin
    Route.SignUp -> GuideDestination.SignUp
    Route.Home -> GuideDestination.Home
    Route.Profile -> GuideDestination.Profile
    Route.PreRegistration -> GuideDestination.PreRegistration
    Route.MyRequests -> GuideDestination.MyRequests
    Route.Status -> GuideDestination.Status
}

/** Auth A→B reinicia pila y ámbito VM aunque ambos tengan el mismo principal/rol. */
fun navigationIdentity(state: SessionState): String = when (state) {
    is SessionState.Authenticated -> "session-${state.generation}"
    else -> "entry-${rootRoute(state)}"
}

private fun NavBackStack<NavKey>.resetTo(route: Route) {
    add(route)
    while (size > 1) removeAt(0)
}

/** Quita las pantallas que hay encima de la última aparición de [route] (R12, R13 de F-02: vuelta al inicio). */
fun MutableList<NavKey>.popTo(route: Route) {
    val index = lastIndexOf(route)
    if (index < 0) return
    while (lastIndex > index) removeAt(lastIndex)
}

/** Une las features; es el único lugar que conoce varias (vive fuera de feature/ y core/). */
@Composable
fun AppNavigation(container: AppContainer, widthSizeClass: WindowWidthSizeClass, guide: SpeechGuideController, preferencesOpen: Boolean = false) {
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { guide.onBackground() }
    if (rootRoute(session) == null) LaunchedEffect(session, locale, preferencesOpen) {
        guide.update(if (preferencesOpen) GuideDestination.Preferences else GuideDestination.SessionVerification, locale, navigationIdentity(session))
    }
    // R27: al abrir la app y al volver a primer plano se comprueba la sesión (expiración y descifrado).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { scope.launch { container.sessionManager.refresh() } }

    if (session is SessionState.VerificationError) {
        SessionVerificationScreen(
            (session as SessionState.VerificationError).message,
            onRetry = { scope.launch { container.sessionManager.refresh() } },
            onLogout = { scope.launch { container.sessionManager.logout() } },
        )
        return
    }
    val root = rootRoute(session)
    if (root == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    key(navigationIdentity(session)) {
        if (session is SessionState.Authenticated) {
            SessionGraph(container, widthSizeClass, session, root, guide, preferencesOpen)
        } else EntryBackdrop {
            SessionGraph(container, widthSizeClass, session, root, guide, preferencesOpen)
        }
    }
}

@Composable
private fun SessionGraph(container: AppContainer, widthSizeClass: WindowWidthSizeClass, session: SessionState, root: Route, guide: SpeechGuideController, preferencesOpen: Boolean) {
    val backStack = rememberNavBackStack(root)
    val currentRoute = backStack.lastOrNull() as? Route ?: root
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(currentRoute, locale, preferencesOpen, navigationIdentity(session)) {
        // Route names contain no private data; the session identity resets the guide between generations.
        guide.update(if (preferencesOpen) GuideDestination.Preferences else routeGuideDestination(currentRoute), locale, navigationIdentity(session) + "|" + currentRoute.toString())
    }
    Column(Modifier.fillMaxSize()) {
        if (backStack.size > 1) TextButton(onClick = { backStack.removeAt(backStack.lastIndex) }) {
            Text(stringResource(R.string.navigate_back))
        }
        if (session is SessionState.LoggedOut || session == SessionState.NoTenant) {
            val notice by container.sessionManager.entryNotice.collectAsStateWithLifecycle()
            notice?.let { EntryNotice(it, container.sessionManager::dismissEntryNotice) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<Route.TenantPicker> {
                        GuardedRoute(session, Route.TenantPicker, { backStack.resetTo(root) }) {
                            val vm = viewModel { TenantPickerViewModel(container.tenantRepository, container.sessionManager) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            TenantPickerScreen(
                                state = state,
                                widthSizeClass = widthSizeClass,
                                onSelectTenant = vm::selectTenant,
                                onSelectSite = vm::selectSite,
                                onConfirm = vm::confirm,
                                onRetry = vm::retry,
                                onConfirmed = { backStack.resetTo(Route.Login(container.sessionManager.generation)) },
                            )

                        }
                    }
                    entry<Route.Login> { route ->
                        GuardedRoute(session, route, { backStack.resetTo(root) }) {
                            val vm = viewModel { LoginViewModel(container.accountRepository, container.sessionManager) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            LoginScreen(
                                state = state,
                                onFormChange = vm::updateForm,
                                onSubmit = vm::submit,
                                onChangeTenant = { backStack.add(Route.TenantPicker) },
                                onGoToSignUp = { backStack.add(Route.SignUp) },
                                onOpenStatus = if (BuildConfig.DEBUG) ({ backStack.add(Route.Status) }) else null,
                            )

                        }
                    }
                    entry<Route.SignUp> {
                        GuardedRoute(session, Route.SignUp, { backStack.resetTo(root) }) {
                            val vm = viewModel { SignUpViewModel(container.accountRepository, container.sessionManager, container.clock) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            SignUpScreen(
                                state = state,
                                widthSizeClass = widthSizeClass,
                                onFormChange = vm::updateForm,
                                onSubmit = vm::submit,
                                onRetryConsent = vm::retryConsent,
                                onGoToLogin = { if (backStack.lastOrNull() == Route.SignUp) backStack.removeAt(backStack.lastIndex) },
                                onNavigatedToLogin = vm::onNavigatedToLogin,
                                onDatePickerChange = vm::updateDatePicker,
                            )

                        }
                    }
                    entry<Route.Home> {
                        GuardedRoute(session, Route.Home, { backStack.resetTo(root) }) {
                            val vm = viewModel { HomeViewModel(container.sessionManager) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            // F-02 (D3): el estado del pre-registro va ligado a la entrada Home.
                            val statusVm = viewModel { PreRegistrationStatusViewModel(container.preRegistrationRepository) }
                            val status by statusVm.state.collectAsStateWithLifecycle()
                            // R17 de F-02: al mostrarse el inicio (también al volver de otra pantalla) y al volver a primer plano.
                            LifecycleEventEffect(Lifecycle.Event.ON_START) { statusVm.refresh() }
                            // F-10 (D3): la tarjeta de fila va ligada a la entrada Home, como el estado del pre-registro.
                            val queueVm = viewModel { QueuePositionViewModel(container.queuePositionStream::stream, SystemClock::elapsedRealtime) }
                            val queue by queueVm.state.collectAsStateWithLifecycle()
                            var followQueue by remember { mutableStateOf(false) }
                            LaunchedEffect(status) { status.followsQueue()?.let { followQueue = it } }
                            val lifecycleOwner = LocalLifecycleOwner.current
                            LaunchedEffect(queueVm, followQueue) {
                                // R1, R2: el stream solo vive mientras la pantalla está al menos en STARTED; al bajar se
                                // cancela la recolección y con ella la conexión. Sin servicio ni trabajo en segundo plano.
                                if (followQueue) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { queueVm.follow() }
                            }
                            HomeScreen(
                                state = state,
                                onOpenProfile = { backStack.add(Route.Profile) },
                                onOpenMyRequests = { backStack.add(Route.MyRequests) },
                                status = {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        PreRegistrationStatusPane(
                                            state = status,
                                            onRefresh = statusVm::refresh,
                                            onNewPreRegistration = { backStack.add(Route.PreRegistration) },
                                        )
                                        if (followQueue || queue.ended) QueuePositionCard(queue, SystemClock::elapsedRealtime)
                                    }
                                },
                                onRequestLogout = vm::requestLogout,
                                onCancelLogout = vm::cancelLogout,
                                onConfirmLogout = vm::confirmLogout,
                            )

                        }
                    }
                    entry<Route.MyRequests> {
                        GuardedRoute(session, Route.MyRequests, { backStack.resetTo(root) }) {
                            val vm = viewModel { MyRequestsViewModel(container.preRegistrationRepository) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.refresh() }
                            MyRequestsScreen(state, vm::refresh)
                        }
                    }
                    entry<Route.Profile> {
                        GuardedRoute(session, Route.Profile, { backStack.resetTo(root) }) {
                            val vm = viewModel { ProfileViewModel(container.profileRepository, container.clock) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            ProfileScreen(state = state, onFormChange = vm::updateForm, onSave = vm::save, onRetry = vm::retry, onDatePickerChange = vm::updateDatePicker)

                        }
                    }
                    entry<Route.PreRegistration> {
                        GuardedRoute(session, Route.PreRegistration, { backStack.resetTo(root) }) {
                            val vm = viewModel {
                                PreRegistrationViewModel(
                                    container.preRegistrationRepository,
                                    container.sessionManager,
                                    recognizer = container.newOnDeviceRecognizer(),
                                )
                            }
                            val state by vm.state.collectAsStateWithLifecycle()
                            // F-08 R10: segundo plano. R10/R11: salir de la pantalla, rotación, idioma/tema o cambio de
                            // sesión (key(navigationIdentity)) desechan esta composición; el ViewModel conserva el borrador.
                            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.cancelDictation() }
                            DisposableEffect(vm) { onDispose { vm.cancelDictation() } }
                            // F-08 R13: si la guía empieza a hablar, se cancela la escucha vigente.
                            LaunchedEffect(vm, guide) {
                                guide.state.collect { if (it == SpeechGuideState.Speaking) vm.cancelDictation() }
                            }
                            PreRegistrationScreen(
                                state = state,
                                widthSizeClass = widthSizeClass,
                                onSelectSite = vm::selectSite,
                                onComplaintChange = vm::updateComplaint,
                                onSubmit = vm::submit,
                                onRetrySites = vm::retrySites,
                                onViewStatus = { backStack.popTo(Route.Home) },
                                onCreated = {
                                    vm.onCreatedHandled()
                                    backStack.popTo(Route.Home)
                                },
                                dictation = {
                                    DictationControls(
                                        state = state.dictation,
                                        message = state.dictationMessage,
                                        enabled = !state.submitting && !state.created,
                                        // F-08 R13 (D8): se reconstruye en cada composición con la guía vigente.
                                        onStart = { startDictationStoppingGuide(guide::stop, vm::startDictation) },
                                        onPermissionDenied = vm::onDictationPermissionDenied,
                                        onStop = vm::stopDictation,
                                        onCancel = vm::cancelDictation,
                                    )
                                },
                            )

                        }
                    }
                    entry<Route.Status> {
                        GuardedRoute(session, Route.Status, { backStack.resetTo(root) }) {
                            val vm = viewModel { StatusViewModel(container.healthApi) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            StatusScreen(
                                state = state,
                                widthSizeClass = widthSizeClass,
                                apiBaseUrl = BuildConfig.API_BASE_URL,
                                contract = BuildConfig.CONSUMED_CONTRACT,
                                onRetry = vm::refresh,
                            )

                        }
                    }
                },
            )
        }
    }
}

/** Aviso de R5 encima de la pila de entrada; texto estático, sin datos del /me rechazado. */
@Composable
private fun EntryNotice(message: UiMessage, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        UiNotice(message.text(), NoticeTone.Warning)
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun GuardedRoute(session: SessionState, route: Route, onHome: () -> Unit, content: @Composable () -> Unit) {
    if (canAccessRoute(session, route)) {
        // Prevent hidden widget Savers from serializing passwords, identity or private drafts.
        // Navigation/VM decorators stay outside this boundary and retain RAM across configuration.
        CompositionLocalProvider(LocalSaveableStateRegistry provides null) { content() }
    } else AccessDeniedScreen(onHome)
}
