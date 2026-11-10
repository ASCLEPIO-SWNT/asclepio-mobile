# Arquitectura — asclepio-mobile

Kotlin + Jetpack Compose, **MVVM**, Retrofit + kotlinx.serialization y layouts adaptativos con `WindowSizeClass` (teléfono y tablet). Alcance: **una APK solo del paciente** (ADR 0008 de la API, sección "Ratificación", 2026-11-09): cuenta, sesión, "Mis datos", pre-registro con su estado, "Mis solicitudes", preferencias, cinco idiomas, guía TTS y, cuando llegue, el dictado en el dispositivo. El personal usa la web; el ADR local 0002 quedó sustituido y DT-01-mobile retiró del APK las funciones de personal.

## Paquetes
```
app/src/main/java/co/asclepio/mobile/
├── AsclepioApp.kt       # Application: crea el AppContainer
├── AppContainer.kt      # DI manual: Retrofit, APIs, SessionStore, SessionManager, repositorios
├── AppNavigation.kt     # Navigation 3 (NavDisplay): une las features; pila y ámbito VM aislados por generación autenticada de SessionManager.state
├── MainActivity.kt
├── core/
│   ├── network/     # ApiClient (Retrofit + OkHttp + kotlinx.serialization), ProblemDetails, ApiError + apiCall
│   ├── auth/        # TokenCipher + KeystoreTokenCipher (AES-256-GCM), SessionStore (DataStore), SessionManager
│   │                # (estados de entrada NoTenant/LoggedOut y aviso entryNotice), AuthInterceptor, MeApi + MeDto,
│   │                # VerifiedPrincipal (única variante Patient), AccessTokenDto, Age (mayoría de edad en America/Bogota),
│   │                # AuthHandle y patientCall (Bearer/generación inmutables; un 401 vigente cierra solo su generación)
│   ├── tenants/     # TenantsApi (GET /api/v1/public/tenants) + PublicTenantDto/PublicSiteDto (F-01 y F-02)
│   ├── model/       # Sex, FULL_NAME_MAX y la conversión del DatePicker (alta de F-01 y "Mis datos" de F-02)
│   └── ui/          # Theme, DateTime, UiMessage (@StringRes), WindowLayout (paneMode), MarkdownText,
│                    # AccessScreens (verificación de sesión y acceso denegado), preferencias, guía TTS (guide/)
│                    # y PrivateDatePicker (fecha en RAM, sin Savers internos)
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
- Los DTOs de sesión compartidos viven en `core/auth`, sin dependencias core→feature. `/me` solo se convierte en un paciente completo de la IPS recordada antes de exponer `Authenticated`; cualquier otro cuerpo no da principal.
- El inicio (`feature/account`) recibe el estado del pre-registro (`feature/encounters`) como un slot `@Composable` que compone `AppNavigation` (D3 de F-02-mobile).

**Prohibidas**
- `feature/A` → `feature/B`. Lo compartido va a `core/`. Solo `AppNavigation.kt` (paquete raíz) conoce varias features.
- `core/` → `feature/`.
- Un Composable que llama al repositorio o a Retrofit.
- Enviar audio a cualquier servidor, o usar ASR en la nube (ADR 0006 de la API).
- Enviar horas legales a la API.
- Añadir rutas, pantallas o APIs de personal (DT-01-mobile), o construir un destino/VM antes de comprobar su guard.
- Leer el token actual dentro de una operación originada en otra generación; cada Retrofit protegido recibe la cabecera explícita capturada por `patientCall`.

## Sesión y navegación

`SessionManager` conserva las seis claves DataStore existentes: IPS/sede públicas recordadas y token cifrado/expiración. Principal y perfil solo viven en RAM. Sin sesión, la raíz es directamente la selección de IPS (`NoTenant`) o el inicio de sesión del paciente (`LoggedOut`); no hay pantalla de elección de acceso. La restauración consulta `/me` antes de abrir la raíz y exige un paciente de la IPS recordada: si `/me` responde 200 con otro principal (incluido STAFF), borra el token y su expiración, conserva la IPS y publica `entryNotice` ("Esta cuenta no es de paciente…"), que `AppNavigation` muestra sobre la pila de entrada hasta que el usuario inicia sesión, confirma la IPS o lo cierra. Un error de red o 5xx conserva el cifrado pero no permite acceso y presenta Reintentar/Cerrar sesión. La comprobación de primer plano conserva la generación y evita duplicar `/me` si ya se verificó y no venció.

Logout, expiración, 401 vigente, un nuevo inicio de sesión o la confirmación de otra IPS invalidan la generación, limpian token y prefill privado, y cambian la identidad de la composición de Navigation 3 con sus decoradores de VM. Los resultados anteriores se descartan. Las rutas son `TenantPicker`, `Login`, `SignUp`, `Home`, `Profile`, `MyRequests`, `PreRegistration` y `Status` (solo `debug`); `canAccessRoute` permite al paciente autenticado solo Home/Profile/PreRegistration/MyRequests y a la entrada solo las suyas.

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
