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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
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

    @Serializable data object TenantPicker : Route

    @Serializable data object Login : Route

    @Serializable data object SignUp : Route

    @Serializable data object Home : Route

    /** "Mis datos" (F-02). */
    @Serializable data object Profile : Route

    /** Nuevo pre-registro (F-02). */
    @Serializable data object PreRegistration : Route

    /** Pantalla de estado de F-00, accesible desde el login solo en debug. */
    @Serializable data object Status : Route
}

/** Raíz de la pila según la sesión (R1, R3, R27, R28, R30, R31). `null` mientras se carga. */
fun rootRoute(state: SessionState): Route? = when (state) {
    SessionState.Loading -> null
    SessionState.NoTenant -> Route.TenantPicker
    is SessionState.LoggedOut -> Route.Login
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
        is VerifiedPrincipal.Patient -> route in setOf(Route.Home, Route.Profile, Route.PreRegistration)
        is VerifiedPrincipal.Staff -> route is Route.Staff && route.destination in principal.role.destinations
    }
    SessionState.Welcome -> route == Route.Welcome
    SessionState.NoTenant -> route == Route.TenantPicker
    is SessionState.LoggedOut -> route in setOf(Route.Login, Route.TenantPicker, Route.SignUp) || (BuildConfig.DEBUG && route == Route.Status)
    SessionState.StaffEntry -> route == Route.StaffLogin
    else -> false
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
fun AppNavigation(container: AppContainer, widthSizeClass: WindowWidthSizeClass) {
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
        SessionGraph(container, widthSizeClass, session, root)
    }
}

@Composable
private fun SessionGraph(container: AppContainer, widthSizeClass: WindowWidthSizeClass, session: SessionState, root: Route) {
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(root)
    Column(Modifier.fillMaxSize()) {
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
                            StaffWorkspaceScreen(
                                principal, route.destination, widthSizeClass, confirmation,
                                onSelect = { destination -> if (canAccessRoute(session, Route.Staff(destination))) backStack.add(Route.Staff(destination)) },
                                onLogout = vm::requestLogout, onChangeAccess = vm::requestChangeAccess, onCancel = vm::cancel, onConfirm = vm::confirm,
                            )
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
                                onConfirmed = { backStack.resetTo(Route.Login) },
                            )

                        }
                    }
                    entry<Route.Login> {
                        GuardedRoute(session, Route.Login, { backStack.resetTo(root) }) {
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
                    entry<Route.Profile> {
                        GuardedRoute(session, Route.Profile, { backStack.resetTo(root) }) {
                            val vm = viewModel { ProfileViewModel(container.profileRepository, container.clock) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            ProfileScreen(state = state, onFormChange = vm::updateForm, onSave = vm::save, onRetry = vm::retry)

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
    if (canAccessRoute(session, route)) content() else AccessDeniedScreen(onHome)
}
