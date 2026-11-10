package co.asclepio.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import co.asclepio.mobile.core.ui.guide.*
import co.asclepio.mobile.feature.encounters.ui.MyRequestsScreen
import co.asclepio.mobile.feature.encounters.ui.MyRequestsViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import co.asclepio.mobile.core.ui.WelcomeScreen
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
import co.asclepio.mobile.feature.staffaccount.ui.*
import co.asclepio.mobile.feature.reception.ui.*
import co.asclepio.mobile.feature.staffencounters.ui.*
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.core.encounters.EncounterCount
import co.asclepio.mobile.feature.status.ui.StatusScreen
import co.asclepio.mobile.feature.status.ui.StatusViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Rutas de Navigation 3 (D3). */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Welcome : Route

    @Serializable data object StaffLogin : Route

    @Serializable data class Staff(val destination: StaffDestination) : Route
    @Serializable data object StaffRegistration : Route
    @Serializable data class StaffEncounterDetail(val id: String) : Route

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

/** Raíz de la pila según la sesión (R1, R3, R27, R28, R30, R31). `null` mientras se carga. */
fun rootRoute(state: SessionState): Route? = when (state) {
    SessionState.Loading -> null
    SessionState.NoTenant -> Route.TenantPicker
    is SessionState.LoggedOut -> Route.Login()
    is SessionState.Authenticated -> when (val principal = state.principal) {
        is VerifiedPrincipal.Patient -> Route.Home
        is VerifiedPrincipal.Staff -> Route.Staff(principal.role.home)
    }
    SessionState.Verifying, is SessionState.VerificationError -> null
    SessionState.Welcome -> Route.Welcome
    SessionState.StaffEntry -> Route.StaffLogin
}

/** Se evalúa antes de crear el Composable/VM de cualquier destino, también con una pila restaurada. */
fun canAccessRoute(state: SessionState, route: Route): Boolean = when (state) {
    is SessionState.Authenticated -> when (val principal = state.principal) {
        is VerifiedPrincipal.Patient -> route in setOf(Route.Home, Route.Profile, Route.PreRegistration, Route.MyRequests)
        is VerifiedPrincipal.Staff -> when (route) {
            is Route.Staff -> route.destination in principal.role.destinations
            Route.StaffRegistration -> principal.role == StaffRole.RECEPTION
            is Route.StaffEncounterDetail -> true
            else -> false
        }
    }
    SessionState.Welcome -> route == Route.Welcome
    SessionState.NoTenant -> route == Route.TenantPicker
    is SessionState.LoggedOut -> route is Route.Login || route in setOf(Route.TenantPicker, Route.SignUp) || (BuildConfig.DEBUG && route == Route.Status)
    SessionState.StaffEntry -> route == Route.StaffLogin
    else -> false
}

/** Closed resource catalog: no identity, patient data or form values can become guide text. */
fun routeGuideDestination(route: Route, role: StaffRole? = null): GuideDestination = when (route) {
    Route.Welcome -> GuideDestination.Welcome
    Route.StaffLogin -> GuideDestination.StaffLogin
    is Route.Staff -> when (route.destination) {
        StaffDestination.RECEPTION -> GuideDestination.StaffReception
        StaffDestination.ENCOUNTERS -> GuideDestination.StaffEncounters
        else -> GuideDestination.StaffPending
    }
    Route.StaffRegistration -> GuideDestination.StaffRegistration
    is Route.StaffEncounterDetail -> GuideDestination.StaffEncounterDetail
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
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(root)
    val currentRoute = backStack.lastOrNull() as? Route ?: root
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(currentRoute, locale, preferencesOpen, navigationIdentity(session)) {
        // Route names contain no parameters/private data. Distinct pending STAFF routes must also reset.
        guide.update(if (preferencesOpen) GuideDestination.Preferences else routeGuideDestination(currentRoute, ((session as? SessionState.Authenticated)?.principal as? VerifiedPrincipal.Staff)?.role), locale, navigationIdentity(session) + "|" + currentRoute.toString())
    }
    Column(Modifier.fillMaxSize()) {
        if (backStack.size > 1) TextButton(onClick = { backStack.removeAt(backStack.lastIndex) }) {
            Text(stringResource(R.string.navigate_back))
        }
        if (session is SessionState.LoggedOut || session == SessionState.NoTenant) {
            TextButton(onClick = { scope.launch { container.sessionManager.showWelcome() } }) { Text(stringResource(R.string.change_access)) }
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
                    entry<Route.Welcome> {
                        GuardedRoute(session, Route.Welcome, { backStack.resetTo(root) }) {
                            WelcomeScreen(widthSizeClass) { mode -> scope.launch { container.sessionManager.selectAccess(mode) } }
                        }
                    }
                    entry<Route.StaffLogin> {
                        GuardedRoute(session, Route.StaffLogin, { backStack.resetTo(root) }) {
                            val vm = viewModel { StaffLoginViewModel(container.staffAccountRepository) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            StaffLoginScreen(state, widthSizeClass, vm::updateForm, vm::submit, onChangeAccess = { scope.launch { container.sessionManager.showWelcome() } })
                        }
                    }
                    entry<Route.Staff> { route ->
                        GuardedRoute(session, route, { backStack.resetTo(root) }) {
                            val principal = (session as SessionState.Authenticated).principal as VerifiedPrincipal.Staff
                            val vm = viewModel { StaffHomeViewModel(container.sessionManager) }
                            val confirmation by vm.confirmation.collectAsStateWithLifecycle()
                            val operational: (@Composable () -> Unit)? = if (route.destination == StaffDestination.RECEPTION) ({
                                val reception = viewModel { ReceptionViewModel(container.receptionRepository, container.staffSitesRepository, container.staffDataChanges, container.dashboardDataRepository) }
                                val receptionState by reception.state.collectAsStateWithLifecycle()
                                DashboardVisibility(currentRoute == route, reception::setActive)
                                ReceptionScreen(
                                    receptionState, reception::refresh, reception::retrySites, reception::selectSite,
                                    reception::setIdentity, reception::setRejecting, reception::setReason, reception::confirm, reception::reject,
                                    onDirectRegistration = { backStack.add(Route.StaffRegistration) },
                                    onOpenEncounter = { backStack.add(Route.StaffEncounterDetail(it)) }, onScroll = reception::rememberScroll,
                                    dashboard = true, onRetryPending = reception::retryPending, summary = {
                                        val pending = when (val remote = receptionState.remote) {
                                            ReceptionRemote.Loading -> DashboardRead.Loading
                                            ReceptionRemote.Forbidden -> DashboardRead.Forbidden
                                            ReceptionRemote.NotFound -> DashboardRead.Error(UiMessage(R.string.staff_pr_gone))
                                            is ReceptionRemote.Error -> DashboardRead.Error(remote.message, remote.retryEnabled)
                                            is ReceptionRemote.Ready -> DashboardRead.Ready(EncounterCount(remote.items.size, false))
                                        }
                                        DashboardSummaryBar(listOf(
                                            DashboardSummaryCell(stringResource(R.string.dash_stat_preregistered), R.drawable.ic_dashboard_pending, pending, onRetry = reception::retryPending),
                                            DashboardSummaryCell(stringResource(R.string.dash_stat_waiting), R.drawable.ic_dashboard_clock, receptionState.waitingCount, NoticeTone.Warning, reception::retryWaiting),
                                        ), filter = { co.asclepio.mobile.core.encounters.ui.DashboardSiteFilter(receptionState.sites, receptionState.siteId, reception::selectSite, reception::retrySites) })
                                    },
                                )
                            }) else if (route.destination == StaffDestination.ENCOUNTERS) ({
                                val encounters = viewModel { StaffEncountersViewModel(container.encountersRepository, container.staffSitesRepository, container.staffDataChanges) }
                                val encountersState by encounters.state.collectAsStateWithLifecycle()
                                LifecycleEventEffect(Lifecycle.Event.ON_START) { encounters.setActive(true) }
                                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { encounters.setActive(false) }
                                DisposableEffect(encounters) { onDispose { encounters.setActive(false) } }
                                StaffEncountersScreen(
                                    encountersState, encounters::refresh, encounters::retrySites, encounters::selectSite, encounters::selectStatus,
                                    encounters::previousPage, encounters::nextPage,
                                    onOpen = { backStack.add(Route.StaffEncounterDetail(it)) }, onScroll = encounters::rememberScroll,
                                )
                            }) else null
                            StaffWorkspaceScreen(
                                principal, route.destination, widthSizeClass, confirmation,
                                onSelect = { destination ->
                                    val next = Route.Staff(destination)
                                    if (canAccessRoute(session, next)) {
                                        if (next in backStack) backStack.popTo(next) else backStack.add(next)
                                    }
                                },
                                onLogout = vm::requestLogout, onChangeAccess = vm::requestChangeAccess, onCancel = vm::cancel, onConfirm = vm::confirm,
                                content = operational,
                            )
                        }
                    }
                    entry<Route.StaffRegistration> {
                        GuardedRoute(session, Route.StaffRegistration, { backStack.resetTo(root) }) {
                            val vm = viewModel { DirectRegistrationViewModel(container.receptionRepository, container.encountersRepository, container.staffSitesRepository, container.clock) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            LaunchedEffect(state.createdEncounterId) {
                                state.createdEncounterId?.let { id -> vm.consumeCreatedEncounter(); backStack.add(Route.StaffEncounterDetail(id)) }
                            }
                            DirectRegistrationScreen(
                                state, vm::updateSearch, vm::search, vm::startDocumentedPatient, vm::startNnPatient,
                                vm::updatePatient, vm::updateDatePicker, vm::createPatient, vm::cancelPatientCreation,
                                vm::updateArrival, vm::registerArrival, vm::retrySites,
                                onReception = { backStack.resetTo(Route.Staff(StaffDestination.RECEPTION)) },
                            )
                        }
                    }
                    entry<Route.StaffEncounterDetail> { route ->
                        GuardedRoute(session, route, { backStack.resetTo(root) }) {
                            val principal = (session as SessionState.Authenticated).principal as VerifiedPrincipal.Staff
                            val vm = viewModel { StaffEncounterDetailViewModel(route.id, principal.role, container.encountersRepository, container.staffDataChanges) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.setActive(true) }
                            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.setActive(false) }
                            DisposableEffect(vm) { onDispose { vm.setActive(false) } }
                            StaffEncounterDetailScreen(state, vm::refresh, vm::setCancelling, vm::setReason, vm::cancel, onEncounters = {
                                val list = Route.Staff(StaffDestination.ENCOUNTERS)
                                if (list in backStack) backStack.popTo(list) else backStack.add(list)
                            })
                        }
                    }
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
                            HomeScreen(
                                state = state,
                                onOpenProfile = { backStack.add(Route.Profile) },
                                onOpenMyRequests = { backStack.add(Route.MyRequests) },
                                status = {
                                    PreRegistrationStatusPane(
                                        state = status,
                                        onRefresh = statusVm::refresh,
                                        onNewPreRegistration = { backStack.add(Route.PreRegistration) },
                                    )
                                },
                                onRequestChangeAccess = vm::requestChangeAccess,
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
                            val vm = viewModel { PreRegistrationViewModel(container.preRegistrationRepository, container.sessionManager) }
                            val state by vm.state.collectAsStateWithLifecycle()
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

@Composable
private fun GuardedRoute(session: SessionState, route: Route, onHome: () -> Unit, content: @Composable () -> Unit) {
    if (canAccessRoute(session, route)) {
        // Prevent hidden widget Savers from serializing passwords, identity or private drafts.
        // Navigation/VM decorators stay outside this boundary and retain RAM across configuration.
        CompositionLocalProvider(LocalSaveableStateRegistry provides null) { content() }
    } else AccessDeniedScreen(onHome)
}

/** Current entry plus foreground: transitions and preference recomposition cannot install another clock. */
@Composable
private fun DashboardVisibility(isCurrentRoute: Boolean, onActive: (Boolean) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onActive)
    val latestCurrent by rememberUpdatedState(isCurrentRoute)
    LaunchedEffect(isCurrentRoute, owner) {
        latestCallback(isCurrentRoute && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { latestCallback(latestCurrent) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { latestCallback(false) }
    DisposableEffect(owner) { onDispose { latestCallback(false) } }
}
