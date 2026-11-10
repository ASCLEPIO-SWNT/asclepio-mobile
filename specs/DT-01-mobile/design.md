# DT-01-mobile — Diseño

## Resumen

Slice de limpieza: el APK pasa a ser **solo del paciente** (ADR 0008 de la API, ratificado el 2026-11-09). Se borra todo lo que añadieron F-01-staff-mobile, F-02-staff-mobile, F-03-mobile y UI-02-mobile y se simplifican las piezas compartidas (sesión, navegación, guía, recursos) para que solo conozcan al paciente. No hay código muerto, flags ni ramas `if (staff)`.

Capas afectadas (según `docs/architecture.md`):

| Capa | Se borra | Se simplifica | Se conserva |
|---|---|---|---|
| `feature/` | `staffaccount/`, `staffencounters/`, `reception/`, `triage/`, `dashboards/` (código y pruebas) | `account/ui/HomeScreen.kt` y `HomeViewModel.kt` (sin "Cambiar tipo de acceso") | `account/`, `patients/`, `encounters/`, `status/` |
| `core/` | `encounters/` completo (incl. `ui/`), `auth/StaffDestinations.kt`, `ui/DashboardComponents.kt`, `ui/DashboardDateTime.kt`, `ui/DashboardRead.kt` | `auth/Principal.kt`, `auth/SessionManager.kt`, `auth/PatientCall.kt`, `ui/AccessScreens.kt` (sin `WelcomeScreen`), `ui/guide/GuideDestination.kt` | `network/`, `model/`, `tenants/`, el resto de `auth/` y `ui/` (tema, preferencias, cinco idiomas, guía TTS, `PrivateDatePicker`) |
| raíz | — | `AppContainer.kt` (sin APIs ni repositorios staff), `AppNavigation.kt` (sin rutas staff ni `Welcome`) | `MainActivity.kt`, `AsclepioApp.kt` |
| `res/` | `values*/staff_*.xml`, `values*/dashboards.xml`, `values*/clinical_*.xml`, claves staff/acceso en `strings.xml` y `voice_guide.xml`, `drawable/ic_dashboard_*.xml` | `error_not_patient` en los cinco idiomas | demás catálogos |

Inventario verificado contra el código (2026-11-09). Además de lo que lista `progress/current/DT-01-mobile.md`:
- `res/drawable/ic_dashboard_{cancelled,care,classified,clock,closed,error,locked,pending}.xml` solo los usan `DashboardComponents.kt` y `AppNavigation.kt` (ramas staff): se borran.
- `res/values*/staff_voice_guide.xml` (no listado) también es solo-staff.
- `error_not_patient` hoy dice "Vuelve y elige Acceso del personal" en los cinco idiomas: hay que corregirlo (R7).
- `HomeScreen`/`HomeViewModel` (paciente) tienen "Cambiar tipo de acceso" y `changingAccess`; `AppNavigation` muestra ese botón en `LoggedOut`/`NoTenant`. Se eliminan (D2).
- `README.md` ya describe la app solo del paciente: no se toca. `docs/conventions.md`, `docs/ui-resources.md`, `init.sh` y `.claude/` no mencionan al personal.
- Pruebas JVM mixtas que usan STAFF o `Welcome` pero protegen comportamiento del paciente: `core/auth/SessionManagerTest`, `core/auth/SessionLockRaceTest`, `core/auth/StaffSessionLifecycleTest`, `feature/account/ui/StaffTransitionRegressionTest`, `UiNavigationTest`, `feature/encounters/ui/MyRequestsViewModelTest` (R20), `core/ui/guide/SpeechGuideControllerTest`, `PatientTestEnv` (D7).
- Pruebas instrumentadas: `DashboardScreensInstrumentedTest` se borra; `UiConfigurationInstrumentedTest` (R2 bienvenida y R6 borrador staff, `showWelcome()` en `@Before`), `UiInstrumentedApi` (fixtures `auth/login` y `/me` STAFF) y `AndroidSpeechGuideEngineInstrumentedTest` (`GuideDestination.Welcome`) se adaptan (D9).
- Las dos pruebas que cuelgan la suite (`DashboardOverviewViewModelTest` R15 y `TriageTrayViewModelTest` R2, sondeo staff) desaparecen con sus paquetes.

## Decisiones

| # | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D1 | Se elimina la bienvenida (`Route.Welcome`, `SessionState.Welcome`, `WelcomeScreen`, `GuideDestination.Welcome`): sin sesión, la raíz vuelve a ser `TenantPicker` (sin IPS) o `Login` (con IPS), como en F-01-mobile R1/R3 (R3). `SessionManager.entryState()` queda `tenant?.let(LoggedOut) ?: NoTenant`. | Conservar la bienvenida con una sola tarjeta "Paciente". | Una pantalla con una sola opción es un paso inútil y código sin propósito. El flujo directo ya está probado (`AppNavigationTest` R1/R3 de F-01-mobile). Las preferencias y la guía siguen accesibles desde la barra de `MainActivity` en las pantallas de entrada. |
| D2 | Se elimina "Cambiar tipo de acceso": botón de `AppNavigation` en `LoggedOut`/`NoTenant`, acción y diálogo de `HomeScreen`, `HomeViewModel.requestChangeAccess`/`changingAccess`, `SessionManager.selectAccess`/`showWelcome`. El inicio conserva solo "Cerrar sesión" (F-01-mobile R30) y el login conserva "Cambiar de IPS" (F-01-mobile R3). | Mantener "Cambiar tipo de acceso" como alias de cerrar sesión. | Ya no hay otro tipo de acceso. Un alias duplicaría el cierre de sesión con un texto engañoso. |
| D3 | Modelo de sesión: se borran `AccessMode`, `StaffRole`, `VerifiedPrincipal.Staff`, `VerifiedPrincipal.mode`, `SessionState.StaffEntry`, `selectedAccess` y el parámetro `mode` de `LoginAttempt`/`beginLogin`/`capture`/`authenticatedCall`. `VerifiedPrincipal` se conserva como `sealed interface` con la única variante `Patient` para no tocar el código ni las pruebas del paciente que la nombran. `MeDto.toPrincipal` devuelve `Patient` o `null`. `MeDto` conserva `role`, `email` y `fullName` opcionales (contrato tolerante; `toString` sigue enmascarando). | Renombrar a `PatientPrincipal`; o mantener `AccessMode` con un solo valor. | Renombrar causa churn en el código del paciente sin beneficio; un enum de un valor es código muerto. Mantener los campos opcionales de `MeDto` evita un fallo de deserialización si `/me` devuelve un STAFF, que debe rechazarse con R5/R7, no con error de red. |
| D4 | Restauración con `/me` 200 no paciente (R5): `SessionManager.refresh()` borra la sesión (`clearLocked`, ya conserva la IPS) y publica un aviso de entrada `entryNotice: StateFlow<UiMessage?>` con `R.string.error_not_patient`. `AppNavigation` lo muestra con `UiNotice` (tono advertencia, acción "Cerrar") encima de la pila de entrada mientras el estado sea `LoggedOut` o `NoTenant`. Se limpia en `beginLogin`, `saveTenant` y al cerrarlo (R6). El aviso es un recurso estático, sin datos del `/me`. | (a) Borrado silencioso (comportamiento actual para un paciente inválido). (b) `VerificationError` con "Reintentar". | (a) El usuario no entiende por qué perdió la sesión. (b) Reintentar nunca tendrá éxito y conservaría un token de personal cifrado en el dispositivo. Cubre también la actualización de un dispositivo que guardaba una sesión de personal: al abrir, se borra. |
| D5 | `error_not_patient` vuelve al texto aprobado en F-01-mobile R20: "Esta cuenta no es de paciente. Usa la aplicación web del personal." y su traducción equivalente en en/pt/fr/de (texto de interfaz, no clínico). La usan el login (R7, sin cambiar `AccountRepository`) y el aviso de R5. El recurso se referencia desde `core` por `R.string.error_not_patient`; `notPatientMessage` de `feature/account` sigue apuntando al mismo recurso. | Mantener el texto actual ("elige Acceso del personal"). | Remite a una entrada que ya no existe. |
| D6 | Pruebas de alcance nuevas en `PatientOnlyScopeTest` (R1, R2): recorren todos los `.kt` de `src/main/java`, extraen `@GET/@POST/@PATCH/@PUT/@DELETE("…")` y comparan el conjunto (método + ruta) con la lista exacta de R1; además buscan las rutas de personal de R2 en cualquier literal de cadena (normalizando el `/` inicial y los `{id}`). `ApiScopeTest` (F-02-mobile R26) no se toca. | Ampliar `ApiScopeTest`. | `ApiScopeTest` traza a F-02-mobile; mezclar trazas rompe la tabla `R<n> → prueba`. Una lista exacta (R1) detecta además endpoints futuros no previstos. |
| D7 | Pruebas JVM: se borran los archivos solo-staff. En los mixtos se migran a paciente los casos cuyo comportamiento sigue existiendo (generaciones, 401, expiración, Bearer por operación, prefill, restauración 5xx) y se borran los que solo existían por STAFF o el cambio de acceso. Los casos migrados que verifican R11 o R5 pasan a `PatientSessionLifecycleTest` con prefijo `R11 `/`R5 `; los que siguen verificando un requisito de F-01-mobile conservan su nombre en su clase. | `@Ignore` o dejar las pruebas staff. | Código muerto; además dos de ellas cuelgan la suite. |
| D8 | Recursos: se borran archivos y claves de R10 en los cinco idiomas; `access_denied`, `access_go_home`, `session_verification_title`, `navigate_back`, `tagline` y `error_not_patient` se conservan (los usan pantallas del paciente o `GuardedRoute`). `LocalizationCatalogTest` R21 quita de su filtro los prefijos que ya no existen (`staff_error_`, `clinical_`, `dash_`); sus umbrales (`>= 127` claves base, `>= 20` controladas) siguen cumpliéndose (≈170 claves base tras la limpieza; el implementer lo confirma). | Dejar las claves sin uso. | `lint` (UnusedResources) y la regla de no dejar código muerto. |
| D9 | Instrumentadas: se borra `DashboardScreensInstrumentedTest`; en `UiConfigurationInstrumentedTest` se borra el R6 de borrador staff, el R2 se reescribe sobre la entrada del paciente (una sola marca, sin "Ingresar como personal") y `@Before` deja de llamar a `showWelcome()`; `UiInstrumentedApi` pierde los fixtures `auth/login` y `/me` STAFF; `AndroidSpeechGuideEngineInstrumentedTest` usa `GuideDestination.PatientLogin` y sus textos. Deben compilar (`./gradlew assembleDebugAndroidTest`); ejecutarlas en dispositivo no es parte de `init.sh` y se registra aparte si se hace. | No tocarlas. | Dejarían de compilar al borrar símbolos staff. |
| D10 | Documentación: `AGENTS.md`, `docs/architecture.md`, `docs/verification.md`, `docs/mobile-migration.md` y `CHECKPOINTS.md` (C3 paquetes, C7 separación por principal) se reescriben en "solo paciente"; `docs/mobile-migration.md` queda como registro histórico marcado "Sustituido el 2026-11-09 por ADR 0008 (Ratificación); ver DT-01-mobile". Lo verifica `PatientOnlyDocsTest` (R13) leyendo esos archivos desde `../`. El ADR local 0002 y `feature_list.json` (descripción del proyecto y estados de los slices retirados) los actualiza el **leader**, no el implementer. | Dejar la documentación para después. | Los agentes leen `AGENTS.md` y `CHECKPOINTS.md` antes de actuar: si dicen "pacientes y personal", el próximo slice reintroduce el alcance retirado. |

## Impacto en contrato

Sin impacto en contrato. El slice sigue declarando `consumes_contract: "0.4.0"` (snapshot vigente 0.7.0, misma mayor). No cambia ningún DTO del paciente ni se regeneran tipos; solo se dejan de consumir los endpoints de personal listados en `requirements.md`. `GET api/v1/patient/pre-registrations/{id}` existe en el contrato pero no se consume hoy ni lo añade este slice.

## Impacto en datos

Sin impacto en datos. No hay migraciones ni cambios en la API. En el dispositivo, DataStore conserva las mismas claves (IPS/sede públicas, token cifrado y expiración) y `asclepio_ui` no cambia. Un token de personal que siga guardado tras actualizar la app se borra en la primera restauración (R5).

## Riesgos y pruebas

**Pruebas nuevas (JVM, `app/src/test`)**

| Clase | Requisitos | Qué comprueba |
|---|---|---|
| `PatientOnlyScopeTest` | R1, R2 | Conjunto exacto de operaciones Retrofit en `src/main`; ninguna ruta de personal en anotaciones ni literales. |
| `PatientOnlyNavigationTest` | R3, R4 | `rootRoute` para `NoTenant`/`LoggedOut`; subclases selladas de `Route` y entradas de `GuideDestination` iguales a las listas de R4; `canAccessRoute` del paciente; ausencia de las claves de cambio de acceso. |
| `PatientSessionLifecycleTest` | R5, R6, R8, R11 | Restauración con `/me` STAFF (los cuatro roles), otro `kind`, paciente incompleto y otro tenant con MockWebServer: token y expiración borrados en memoria y disco, IPS conservada, sin `Authenticated`, una sola petición, `entryNotice` publicado y limpiado por `beginLogin`/`saveTenant`/cierre. `VerifiedPrincipal` sin otra variante que `Patient` y `toPrincipal` nulo para STAFF. Casos migrados de generaciones, 401, expiración y Bearer por operación. |
| `PatientOnlyResourcesTest` | R7, R9, R10 | `error_not_patient` en `values` igual al texto de R7 y en los cinco idiomas sin el texto retirado; ausencia de paquetes y archivos de R9; ausencia de archivos, prefijos, claves y drawables de R10 en los cinco `values*`. |
| `PatientOnlyDocsTest` | R13 | Los cinco documentos no contienen `staffaccount`, `StaffDestinations`, `F-01-staff-mobile` como vigente ni "pacientes y personal"; `0002` solo aparece junto a "sustituido". |

R7 también la cubre `LoginViewModelTest` R20 de F-01-mobile (sin cambios: compara el `UiMessage`). R12 se verifica por regresión con las suites del paciente (PA-2): `AppNavigationTest`, `SessionManagerTest`, `SessionStoreTest`, `AuthInterceptorTest`, `PatientCallTest`, `AccountApiTest`, `LoginViewModelTest`, `SignUpViewModelTest`, `AutoLoginTest`, `HomeViewModelTest`, `TenantPickerViewModelTest`, `ValidationTest`, `ErrorMessagesTest`, `ProfileViewModelTest`, `ProfileFormTest`, `PatientProfileApiTest`, `PreRegistration*Test`, `MyRequestsViewModelTest`, `ApiScopeTest`, `PrivacyTest`, `PatientDataPrivacyTest`, `LocalizationCatalogTest`, `UiIdentityTest`, `UiPreferencesRepositoryTest`, `WindowLayoutTest`, `SpeechGuideControllerTest`, `PrivateDatePickerDraftTest`, `MarkdownTextTest`, `AgeTest`, `ApiCallTest`, `LoadStatusTest`.

**Pruebas del paciente que se adaptan (sin cambiar lo que verifican)**
- `SessionManagerTest` R1/R3/R27: el estado inicial sin sesión es `NoTenant`/`LoggedOut` (ya no `Welcome`); se quitan las llamadas a `selectAccess`.
- `SessionLockRaceTest`: los casos con `AccessMode.STAFF` pasan a paciente; el R15 que usaba `selectAccess` usa `logout`/`saveTenant`.
- `UiNavigationTest`: se quitan las aserciones STAFF/`Welcome` y las pruebas R1/R23 de F-03-mobile (captura clínica).
- `MyRequestsViewModelTest` R20: el caso "personal" se sustituye por "sin sesión de paciente no hay HTTP ni filas".
- `SpeechGuideControllerTest`: el destino por defecto pasa de `Welcome` a `PatientLogin`.
- `PatientTestEnv`: `beginLogin()` sin `AccessMode`.

**Riesgos**
- *Borrar algo compartido.* Mitigación: antes de borrar un símbolo de `core/ui` o un recurso, `grep` de usos en el código que queda; `./gradlew lint` (UnusedResources) y la compilación lo confirman.
- *Suite que cuelga.* Se elimina con los paquetes; la tarea final exige que `./gradlew lint testDebugUnitTest` termine y que `./init.sh` completo salga en verde.
- *Pérdida de cobertura de sesión.* D7 migra a paciente los casos genéricos antes de borrar los archivos mixtos; el implementer deja en `progress/impl_DT-01-mobile.md` la tabla "prueba antigua → destino (migrada a X / borrada porque Y)".
- *Fuera de alcance:* `feature_list.json` (estados de F-01-staff-mobile, F-02-staff-mobile, F-03-mobile y UI-02-mobile, hoy `in_progress`) y el ADR local 0002 son del leader.
