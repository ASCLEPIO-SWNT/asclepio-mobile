# impl DT-01-mobile — Retirar del APK las funciones de personal

Implementer, 2026-11-09, rama `chore/dt-01-mobile` (sin push). Spec aprobada por chat el 2026-11-09 (PA-1 y PA-2 resueltas). Sin cambios de contrato (sigue `consumes_contract: "0.4.0"`), sin migraciones y nada escrito en `../asclepio-api`. No se tocaron `.env`, `feature_list.json` ni `docs/adr/0002-pacientes-y-personal.md`.

## Commits (uno por tarea; T5 y T6 comparten uno)

| Commit | Tarea | Resumen |
|---|---|---|
| `ce0cfdf` | T1 | `refactor(staff-removal): retire clinical capture (F-03-mobile)` |
| `4793697` | T2 | `refactor(staff-removal): retire staff dashboards (UI-02-mobile)` |
| `9e5ef48` | T3 | `refactor(staff-removal): retire reception and staff encounters (F-02-staff-mobile)` |
| `e835cb8` | T4 | `test(staff-removal): migrate generic session cases to patient lifecycle` |
| `4c68c8f` | T5 + T6 | `refactor(staff-removal): retire staff access, welcome and access switching` |
| `cb7d7a4` | T7 | `feat(staff-removal): warn and clear a restored non-patient session` |
| `2d550fe` | T8 | `fix(staff-removal): point non-patient accounts to the staff web app` |
| `436db02` | T9 | `test(staff-removal): check retired packages and resources stay out` |
| `0607397` | T10 | `test(staff-removal): pin the patient-only network scope` |
| `7877355` | T11 | `docs(staff-removal): describe the patient-only app` |

Total desde `f43b5f3`: 165 archivos, +1034 / −9514. Cada commit compila producción, pruebas JVM y androidTest.

## Archivos tocados

**Borrados (producción):** `feature/{triage,dashboards,reception,staffencounters,staffaccount}/**`, `core/encounters/**` (incl. `ui/`), `core/auth/StaffDestinations.kt`, `core/ui/Dashboard{Components,DateTime,Read}.kt`, `res/drawable/ic_dashboard_*.xml` (8), `res/values*/{clinical_capture,clinical_guides,dashboards,staff_encounters,staff_reception,staff_registration,staff_voice_guide}.xml` (cinco idiomas).

**Simplificados (producción):**
- `AppContainer.kt`: sin APIs ni repositorios de personal.
- `AppNavigation.kt`: solo las ocho rutas del paciente; `rootRoute`/`canAccessRoute`/`routeGuideDestination` sin ramas staff ni `Welcome`; sin botón "Cambiar tipo de acceso"; nuevo `EntryNotice` (aviso de R5 con "Cerrar") sobre la pila de entrada en `LoggedOut`/`NoTenant`; sin `DashboardVisibility`.
- `core/auth/Principal.kt`: `VerifiedPrincipal` sellada con la única variante `Patient` (sin `mode`); `MeDto.toPrincipal` devuelve `VerifiedPrincipal.Patient?` (null para STAFF, otro `kind`, paciente incompleto u otra IPS). Sin `AccessMode` ni `StaffRole`.
- `core/auth/SessionManager.kt`: sin `Welcome`/`StaffEntry`, `selectedAccess`, `selectAccess`, `showWelcome`; `beginLogin(expectedGeneration)`, `LoginAttempt(generation, tenantId)`, `capture(expectedGeneration)`; `entryState()` = `LoggedOut(tenant)` o `NoTenant`; nuevo `entryNotice: StateFlow<UiMessage?>` (se publica al rechazar un `/me` 200 no paciente al restaurar; se limpia en `beginLogin`, `saveTenant` y `dismissEntryNotice`).
- `core/auth/PatientCall.kt`: `patientCall` hace directamente la captura/cierre (se quitó `authenticatedCall(mode)`, que solo usaba `patientCall`); `AuthHandle(generation, authorization)`.
- `core/ui/AccessScreens.kt` (sin `WelcomeScreen`/`AccessCard`), `core/ui/guide/GuideDestination.kt` (10 entradas del paciente), `core/encounters/StaffDataChanges.kt` y `EncounterListScreen.kt` (ajustes intermedios en T1, borrados en T3).
- `feature/account/data/AccountRepository.kt` (`beginLogin` sin modo), `feature/account/ui/{HomeScreen,HomeViewModel}.kt` (sin "Cambiar tipo de acceso" ni `changingAccess`).
- `res/values*/strings.xml` y `voice_guide.xml` (cinco idiomas): borradas las claves `staff_*`, `access_choose`, `access_patient*`, `access_staff*`, `change_access*`, `guide_staff_*`, `guide_welcome_*`; `error_not_patient` = texto de F-01-mobile R20 en los cinco idiomas; `guide_patient_login_2` reescrita (ver Desviaciones).

**Pruebas JVM nuevas:** `core/auth/PatientSessionLifecycleTest.kt`, `core/auth/SessionTestEnv.kt` (entorno de sesión del paciente con Retrofit/DataStore/MockWebServer reales), `PatientOnlyNavigationTest.kt`, `PatientOnlyResourcesTest.kt`, `PatientOnlyScopeTest.kt`, `PatientOnlyDocsTest.kt`.

**Pruebas JVM borradas:** `feature/{triage,dashboards,reception,staffencounters,staffaccount}/**`, `core/encounters/**`, `core/ui/DashboardDateTimeTest`, `DashboardRetentionViewModelTest`, `StaffEncountersTestEnv`, `StaffReceptionNavigationTest`, `StaffTestEnv`, `StaffNavigationTest`, `core/auth/StaffSessionLifecycleTest`, `core/auth/SessionLockRaceTest`, `feature/account/ui/StaffTransitionRegressionTest` (casos vigentes migrados, ver tabla).

**Pruebas JVM adaptadas (sin cambiar lo que verifican):** `SessionManagerTest` (R1/R3/R27 sin `Welcome` ni `selectAccess`), `UiNavigationTest` (sin STAFF/`Welcome`; R1/R23 de F-03 borradas), `MyRequestsViewModelTest` R20 ("sin sesión de paciente no hay HTTP ni filas"), `SpeechGuideControllerTest` (destino por defecto `PatientLogin`), `PatientTestEnv` (`beginLogin()`), `LocalizationCatalogTest` R21 (filtro sin `staff_error_`, `clinical_`, `dash_`), `ErrorMessagesTest` R34 (texto de `error_not_patient` de la tabla de F-01-mobile).

**androidTest:** borrado `DashboardScreensInstrumentedTest`; `UiConfigurationInstrumentedTest` (`@Before` sin `showWelcome`, R2 reescrito sobre la entrada del paciente, R6 de borrador staff borrado, `enterPatientLogin` sin tarjeta de paciente); `UiInstrumentedApi` (sin `auth/login` ni `/me` STAFF); `AndroidSpeechGuideEngineInstrumentedTest` (`GuideDestination.PatientLogin`). Compilan (`assembleDebugAndroidTest`); no se ejecutaron en dispositivo.

**Documentación:** `AGENTS.md`, `docs/architecture.md`, `docs/verification.md`, `CHECKPOINTS.md` (C3, C7), `docs/mobile-migration.md` (marcado "Sustituido el 2026-11-09 por ADR 0008 (Ratificación); ver DT-01-mobile"). `specs/DT-01-mobile/tasks.md`: T1–T11 `[x]`; T12 queda `[ ]` (falta la prueba manual en emulador).

## Tabla R<n> → archivo::prueba

Rutas relativas a `app/src/test/java/co/asclepio/mobile/`.

| R | Archivo::prueba |
|---|---|
| R1 | `PatientOnlyScopeTest.kt::R1 las fuentes de produccion declaran exactamente las operaciones del paciente y publicas` |
| R2 | `PatientOnlyScopeTest.kt::R2 ninguna fuente de produccion nombra una ruta de personal en anotaciones ni literales`; `PatientOnlyScopeTest.kt::R2 el detector senala archivo y ruta con cualquier metodo con o sin barra inicial y con id` |
| R3 | `PatientOnlyNavigationTest.kt::R3 sin sesion ni IPS recordada la app abre la seleccion de IPS sin eleccion de acceso`; `::R3 con IPS recordada y sin sesion la app abre el inicio de sesion del paciente`; `::R3 al cerrar sesion la raiz vuelve directamente al inicio de sesion del paciente` (además `AppNavigationTest` R1/R3 y `SessionManagerTest` R1/R3 de F-01-mobile) |
| R4 | `PatientOnlyNavigationTest.kt::R4 las rutas de navegacion son exactamente las del paciente`; `::R4 los destinos de guia son las pantallas del paciente verificacion y preferencias`; `::R4 el paciente solo monta sus rutas y la entrada solo las de entrada`; `::R4 ningun texto ofrece acceso de personal ni cambiar tipo de acceso` |
| R5 | `core/auth/PatientSessionLifecycleTest.kt::R5 restaurar un token de personal con cualquiera de los cuatro roles lo borra conserva la IPS y avisa`; `::R5 restaurar otro kind un paciente incompleto u otra IPS lo borra y avisa en el inicio de sesion`; `::R5 sin IPS recordada un token de paciente o de personal se borra y el aviso se muestra en la seleccion de IPS`; `::R5 un paciente valido de la IPS recordada se restaura sin aviso` |
| R6 | `core/auth/PatientSessionLifecycleTest.kt::R6 iniciar sesion confirmar la IPS o cerrar el aviso dejan de mostrarlo` |
| R7 | `PatientOnlyResourcesTest.kt::R7 el rechazo de una cuenta que no es de paciente remite a la web del personal en los cinco idiomas`; `::R7 ningun idioma conserva el texto retirado que indicaba un acceso de personal en la app` (además `feature/account/ui/LoginViewModelTest.kt::R20 si me es STAFF o de otra IPS…` de F-01-mobile, sin cambios) |
| R8 | `core/auth/PatientSessionLifecycleTest.kt::R8 el modelo de sesion solo tiene principal paciente sin modo de acceso ni rol de personal`; `::R8 me de personal con cualquiera de los cuatro roles u otro kind no se convierte en principal` |
| R9 | `PatientOnlyResourcesTest.kt::R9 las fuentes de produccion no contienen los paquetes ni archivos de personal` |
| R10 | `PatientOnlyResourcesTest.kt::R10 los cinco catalogos no contienen archivos claves ni drawables de personal y conservan el resto` |
| R11 | `core/auth/PatientSessionLifecycleTest.kt`: 14 pruebas `R11 …` (5xx/red al restaurar; vencida al restaurar y temporizador; 401 vigente; logout invalida login pendiente; respuesta y 401 antiguos; Bearer de operación suspendida; llamada sin sesión durante el nuevo login; token que vence al guardarse; `beginLogin` de generación anterior; restauración que vence durante 503; respuesta protegida que vence durante HTTP; prefill limpiado por logout/otra IPS; logout durante el alta; selector de IPS de generación anterior) |
| R12 | Regresión de las suites del paciente (PA-2), tabla siguiente |
| R13 | `PatientOnlyDocsTest.kt::R13 AGENTS architecture verification y CHECKPOINTS describen la app solo del paciente`; `::R13 CHECKPOINTS C3 lista solo paquetes del paciente y C7 no separa principales de personal`; `::R13 el plan de migracion movil queda como registro historico sustituido por ADR 0008` |

## R12 → clases de prueba (PA-2)

Todas en verde en la ejecución final (nº de pruebas entre paréntesis). Comportamiento cubierto: cuenta con consentimiento, inicio y cierre de sesión, "Mis datos", pre-registro con su estado, "Mis solicitudes", cinco idiomas, preferencias visuales y guía TTS del paciente.

| Ámbito | Clases |
|---|---|
| Sesión, token y red | `AppNavigationTest` (7), `core.auth.SessionManagerTest` (10), `core.auth.SessionStoreTest` (5), `core.auth.AuthInterceptorTest` (4), `core.auth.PatientCallTest` (3), `core.auth.AgeTest` (5), `core.network.ApiCallTest` (14) |
| Cuenta e inicio de sesión (F-01-mobile) | `feature.account.data.AccountApiTest` (6), `feature.account.ui.LoginViewModelTest` (12), `feature.account.ui.SignUpViewModelTest` (17), `feature.account.ui.AutoLoginTest` (5), `feature.account.ui.HomeViewModelTest` (7), `feature.account.ui.TenantPickerViewModelTest` (6), `feature.account.ui.ValidationTest` (11), `feature.account.ui.ErrorMessagesTest` (10) |
| "Mis datos" (F-02-mobile) | `feature.patients.ui.ProfileViewModelTest` (19), `feature.patients.ui.ProfileFormTest` (8), `feature.patients.ui.ProfileErrorMessagesTest` (6), `feature.patients.data.PatientProfileApiTest` (3) |
| Pre-registro, estado y "Mis solicitudes" (F-02-mobile) | `feature.encounters.data.PreRegistrationsApiTest` (5), `feature.encounters.ui.PreRegistrationViewModelTest` (18), `feature.encounters.ui.PreRegistrationStatusViewModelTest` (13), `feature.encounters.ui.PreRegistrationStatusTest` (2), `feature.encounters.ui.PreRegistrationFormTest` (5), `feature.encounters.ui.PreRegistrationErrorMessagesTest` (7), `PreRegistrationStringsTest` (4), `feature.encounters.ui.MyRequestsViewModelTest` (14) |
| Alcance y privacidad | `ApiScopeTest` (3, sin modificar), `PrivacyTest` (3), `PatientDataPrivacyTest` (2) |
| Idiomas, preferencias, guía y UI (UI-01-mobile) | `LocalizationCatalogTest` (3), `core.ui.UiIdentityTest` (2), `core.ui.UiPreferencesRepositoryTest` (6), `core.ui.WindowLayoutTest` (3), `core.ui.guide.SpeechGuideControllerTest` (12), `core.ui.PrivateDatePickerDraftTest` (5), `core.ui.MarkdownTextTest` (3), `UiNavigationTest` (3), `feature.status.ui.LoadStatusTest` (4) |

Las clases `PreRegistration*Test` de `design.md` son las seis de `feature/encounters` más `PreRegistrationStringsTest`.

## Pruebas antiguas → destino

| Prueba antigua | Destino |
|---|---|
| `StaffSessionLifecycleTest::R10 restauracion staff sin IPS…` | Borrada: el acceso staff ya no existe; el caso inverso (STAFF al restaurar se rechaza) es `PatientSessionLifecycleTest::R5 …cuatro roles…` |
| `StaffSessionLifecycleTest::R10 token paciente anterior requiere IPS recordada coincidente…` | Migrada a `PatientSessionLifecycleTest::R5 sin IPS recordada…` y `::R5 un paciente valido…` |
| `StaffSessionLifecycleTest::R11 5xx y red al restaurar…` | Migrada a `PatientSessionLifecycleTest::R11 5xx y red al restaurar…` |
| `StaffSessionLifecycleTest::R12 expiracion invalida ilegible vencida y timer…` | Migrada a `::R11 sesion vencida al restaurar y temporizador…` (ilegible ya en `SessionManagerTest` R27) |
| `StaffSessionLifecycleTest::R13 un 401 vigente…` | Migrada a `::R11 un 401 vigente…` |
| `StaffSessionLifecycleTest::R14 logout y cambio de acceso invalidan intentos pendientes…` | Migrada a `::R11 cerrar sesion invalida un inicio de sesion pendiente…` (el cambio de acceso ya no existe) |
| `StaffSessionLifecycleTest::R15 respuesta y 401 antiguos…` | Migrada a `::R11 respuesta y 401 antiguos…` (con `patientCall`) |
| `StaffSessionLifecycleTest::R18 una operacion suspendida conserva su Bearer…` | Migrada a `::R11 una operacion suspendida conserva su Bearer…` |
| `StaffSessionLifecycleTest::R19 staff no ejecuta funciones de paciente…` | Borrada: no hay llamadas staff; la ausencia de principal/llamadas staff la cubren R8 y R1/R2 |
| `SessionLockRaceTest::R18 llamada iniciada sin principal…` | Migrada a `::R11 una llamada iniciada sin sesion no espera al nuevo inicio de sesion…` |
| `SessionLockRaceTest::R12 token que vence mientras se persiste…` | Migrada a `::R11 un token que vence mientras se guarda…` |
| `SessionLockRaceTest::R15 beginLogin pendiente rechaza su generacion anterior…` | Migrada a `::R11 un beginLogin de la generacion anterior…` (con `logout` en lugar de `selectAccess`) |
| `SessionLockRaceTest::R12 restauracion que vence durante me con 503…` | Migrada a `::R11 restauracion que vence durante me con 503…` |
| `SessionLockRaceTest::R12 respuesta protegida que vence durante HTTP…` | Migrada a `::R11 respuesta protegida que vence durante HTTP…` |
| `StaffTransitionRegressionTest::R14 cambiar acceso o cerrar limpia el documento pendiente…` | Migrada a `::R11 cerrar sesion o confirmar otra IPS limpia el documento pendiente…` |
| `StaffTransitionRegressionTest::R15 cambiar acceso durante alta…` | Migrada a `::R11 cerrar sesion durante el alta…` |
| `StaffTransitionRegressionTest::R15 selector paciente anterior no cambia IPS…` | Migrada a `::R11 un selector de IPS de la generacion anterior…` |
| `UiNavigationTest::R1 clinical capture guard…`, `::R23 clinical guides…` | Borradas (F-03-mobile retirada) |
| `UiNavigationTest::R15/R11/R18` | Adaptadas: sin aserciones STAFF/`Welcome` |
| `MyRequestsViewModelTest::R20 lista es paciente only… para personal` | Adaptada: `R20 … sin sesion de paciente no llama HTTP ni almacena filas` |
| `DashboardOverviewViewModelTest` (R15 colgaba), `TriageTrayViewModelTest` (R2 colgaba) y el resto de pruebas de los paquetes staff | Borradas con sus paquetes; la suite termina |
| `androidTest/UiConfigurationInstrumentedTest::R6 … staff draft …` | Borrada; `R6 signup private fields…` se conserva |

## Desviaciones y notas para leader/reviewer

1. **T5 y T6 en un solo commit.** Borrar `AccessMode` (T5) obliga a borrar `WelcomeScreen`/`AccessCard`, `selectAccess`/`showWelcome` y el cambio de acceso del inicio (T6); separarlos exigía código intermedio desechable. Las pruebas R3/R4/R8 se escribieron antes de esa implementación.
2. **`SessionLockRaceTest` migrada, no adaptada.** `design.md` decía "adaptar"; `tasks.md` T4 dice migrar sus casos genéricos a `PatientSessionLifecycleTest` como `R11`. Sus nombres trazaban a R12/R15/R18 de F-01-staff-mobile (retirado), así que se migraron todos y se borró la clase.
3. **`guide_patient_login_2` reescrita en los cinco idiomas.** Decía "vuelve a la selección de Paciente o Personal", lo que contradice R4 (ningún texto que ofrezca acceso de personal). Ahora remite a "Cambiar IPS" (texto de guía de interfaz, no clínico). Misma clave, sin argumentos.
4. **`ErrorMessagesTest` R34 (F-01-mobile).** Fijaba el texto antiguo de `error_not_patient`; se devolvió al texto de la tabla de F-01-mobile (D5). Va en el commit de T9.
5. **`authenticatedCall` eliminado.** Solo lo usaba `patientCall`; quitarle el `mode` lo dejaba sin propósito. `AuthHandle` ya no lleva `principal`.
6. **`tagline` queda sin uso.** D8 manda conservarla, pero solo la usaba `WelcomeScreen`; lint la marca como `UnusedResources` (aviso, no error). No la borré porque R10 no la incluye; decide el leader. `ic_back` y `home_navigation` ya estaban sin uso antes del slice.
7. **Prueba manual en emulador (T12) no realizada.** En esta sesión `adb` no está en el PATH y no se me permitió buscarlo en el SDK, así que no ejercité teléfono ni tablet. Queda pendiente para el humano: entrada sin IPS y con IPS sin bienvenida, login con cuenta de personal (aviso R7), restauración con token de personal (aviso R5 y "Cerrar"), en teléfono y tablet con `adb reverse tcp:3000 tcp:3000`. T12 se deja `[ ]` por esto.
8. `feature_list.json`, el ADR local 0002 y el cierre en `progress/history.md` son del leader.

## Salida de las pruebas

`grep -rnE '\`R[0-9]+ ' app/src/test app/src/androidTest`: R1–R11 y R13 tienen pruebas `R<n> ` en las clases de arriba; R12 se cubre por regresión (PA-2).

`./gradlew lint testDebugUnitTest assembleDebugAndroidTest --rerun-tasks` (final, sobre `7877355`):
```
BUILD SUCCESSFUL (≈40 s, la suite ya no se cuelga)
testDebugUnitTest: 44 clases, 313 pruebas, 0 fallos, 0 errores
lint: 0 errors, 13 warnings (versiones de dependencias, ModifierParameter, ObsoleteSdkInt y UnusedResources de tagline/ic_back/home_navigation)
assembleDebugAndroidTest: OK
```

`./init.sh` (completo, sin `--quick`):
```
== 1. Entorno ==            [OK] JDK 21 · [OK] Gradle wrapper presente
== 2. Archivos del harness == [OK] Archivos base presentes · [OK] init.sh ejecutable · [OK] ../asclepio-api/openapi presente
== 3. Estado ==             [OK] feature_list.json coherente (done: 4, in_progress: 1, pending: 1, retired: 4)
== 4. Configuración local == [OK] local.properties define: API_BASE_URL
== 5. Calidad ==            [OK] gradlew lint testDebugUnitTest
FAIL: 0 · WARN: 0
Estado: VERDE
```
