# Arquitectura — asclepio-mobile

Kotlin + Jetpack Compose, **MVVM**, Retrofit + kotlinx.serialization y layouts adaptativos con `WindowSizeClass` (teléfono y tablet). Alcance: **app del paciente** (cuenta, pre-registro y dictado; ADR 0008 de la API).

## Paquetes
```
app/src/main/java/co/asclepio/mobile/
├── AsclepioApp.kt       # Application: crea el AppContainer
├── AppContainer.kt      # DI manual: Retrofit, APIs, SessionStore, SessionManager, repositorios
├── AppNavigation.kt     # Navigation 3 (NavDisplay): une las features; pila reiniciada según SessionManager.state
├── MainActivity.kt
├── core/
│   ├── network/     # ApiClient (Retrofit + OkHttp + kotlinx.serialization), ProblemDetails, ApiError + apiCall
│   ├── auth/        # TokenCipher + KeystoreTokenCipher (AES-256-GCM), SessionStore (DataStore), SessionManager,
│   │                # AuthInterceptor, MeApi + MeDto, Age (mayoría de edad en America/Bogota),
│   │                # PatientCall (`SessionManager.patientCall`: un 401 de /patient/… cierra la sesión, F-02)
│   ├── tenants/     # TenantsApi (GET /api/v1/public/tenants) + PublicTenantDto/PublicSiteDto (F-01 y F-02)
│   ├── model/       # Sex, FULL_NAME_MAX y la conversión del DatePicker (alta de F-01 y "Mis datos" de F-02)
│   └── ui/          # Theme, DateTime, UiMessage (@StringRes), WindowLayout (paneMode), MarkdownText
└── feature/
    ├── status/      # data/ (HealthApi) y ui/ (StatusScreen, StatusViewModel): GET /api/v1/health (F-00)
    ├── account/     # cuenta e inicio de sesión del paciente (F-01): data/ (PublicApi con el consentimiento,
    │                # PatientAuthApi, DTOs, TenantRepository, AccountRepository) y ui/ (TenantPicker, Login, SignUp,
    │                # Home con el slot de estado y "Mis datos", formularios y errores)
    ├── patients/    # "Mis datos" (F-02): data/ (PatientProfileApi, PatientProfileDto, UpdatePatientProfileRequest,
    │                # PatientProfileRepository) y ui/ (ProfileScreen, ProfileViewModel, ProfileForm + validación y diff,
    │                # ProfileErrors)
    ├── encounters/  # pre-registro y su estado (F-02): data/ (PreRegistrationsApi, DTOs, ComplaintSource,
    │                # PreRegistrationRepository) y ui/ (PreRegistrationScreen/ViewModel, PreRegistrationStatusPane/
    │                # ViewModel, PreRegistrationForm + validación, statusText/isInProgress, PreRegistrationErrors)
    └── voice/       # dictado en el dispositivo (F-08)
```
Cada `feature/<f>/` contiene `data/` (API y repositorio), `ui/` (Composables y `<X>ViewModel`) y, si aplica, `model/`.

## Dependencias

**Permitidas**
- `feature/*` → `core/*`.
- `ui` (Composable) → `ViewModel` → repositorio → servicio Retrofit.
- El inicio (`feature/account`) recibe el estado del pre-registro (`feature/encounters`) como un slot `@Composable` que compone `AppNavigation` (D3 de F-02-mobile).

**Prohibidas**
- `feature/A` → `feature/B`. Lo compartido va a `core/`. Solo `AppNavigation.kt` (paquete raíz) conoce varias features.
- `core/` → `feature/`.
- Un Composable que llama al repositorio o a Retrofit.
- Enviar audio a cualquier servidor, o usar ASR en la nube (ADR 0006 de la API).
- Enviar horas legales a la API.

## Plataforma
- `minSdk 31`, `compileSdk`/`targetSdk 37` (ver `docs/adr/0001-minsdk-31.md`). AGP 9 con Kotlin integrado y catálogo de versiones en `gradle/libs.versions.toml`.
- `API_BASE_URL` se lee de `local.properties` hacia `BuildConfig` (por defecto `http://localhost:3000`, con `adb reverse tcp:3000 tcp:3000`: Android 17 / targetSdk 37 bloquea la red local, como `10.0.2.2`, sin `ACCESS_LOCAL_NETWORK`).
- El tráfico en claro solo se permite en `debug` (`app/src/debug/AndroidManifest.xml` y `res/xml/network_security_config.xml`).
- El permiso `RECORD_AUDIO` se declara en el manifiesto y se solicita en el momento de uso.

## Qué es "buen trabajo" aquí
- El código está en el paquete correcto y sigue MVVM con `StateFlow<UiState>` (`Loading`, `Success` y `Error`).
- Cada `R<n>` tiene su prueba `R<n> …` (con espacio; `:` no es válido en Kotlin).
- El layout funciona en teléfono y en tablet.
- El audio no sale del dispositivo.
