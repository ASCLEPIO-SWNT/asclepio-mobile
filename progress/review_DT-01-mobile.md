# Review DT-01-mobile — Retirar del APK las funciones de personal

Reviewer, 2026-11-09. Rama `chore/dt-01-mobile`, commits `f43b5f3..7dd5459` (11 commits, todos con `Refs: DT-01-mobile`). Árbol limpio (`git status --porcelain` vacío).

## Veredicto: APROBADO (el cierre a `done` depende de la QA manual de T12, que sigue pendiente del humano)

El código, las pruebas, los recursos y la documentación cumplen R1–R13. `./init.sh` completo sale en VERDE. El único pendiente es la prueba manual en emulador de T12: no se ejecutó porque no había `adb` disponible. No bloquea la revisión, pero T12 sigue `[ ]` y, según C6, el leader no debe marcar `done` hasta que el humano la haga y se marque `[x]`.

## Ejecución

- `./init.sh` (completo, sin `--quick`): `FAIL: 0 · WARN: 0 · Estado: VERDE`, exit 0. Incluye `gradlew lint testDebugUnitTest`.
- Resultados JVM (`app/build/test-results/testDebugUnitTest/*.xml`): 313 pruebas, 0 fallos, 0 errores, 0 omitidas. La suite ya no se cuelga.
- `./gradlew assembleDebugAndroidTest`: exit 0, así que las instrumentadas adaptadas compilan (D9).
- Lint: 0 errores y 13 avisos. Entre ellos, 3 `UnusedResources`: `tagline`, `ic_back` y `home_navigation`.

## Comprobaciones específicas

1. **Nada de personal en el APK.** Lo busqué con grep en todo el código, no solo con las pruebas R1/R2.
   - Producción: no aparecen `staff|AccessMode|StaffRole|Welcome|selectAccess|showWelcome|changingAccess|change_access|dashboard|clinical|queue` en `app/src/main`. Lo único que queda son comentarios de rechazo de STAFF (`Principal.kt:20`, `SessionManager.kt:99`), textos del paciente que nombran a recepción o el triage como estado de su solicitud (`status_waiting_triage`, `profile_locked`…) y `TextToSpeech.QUEUE_FLUSH`. Ninguno da acceso al personal.
   - Paquetes: en `app/src/main/java` solo quedan `core/{auth,model,network,tenants,ui}` y `feature/{account,encounters,patients,status,voice}`.
   - Recursos: no quedan `res/values*/staff_*.xml`, `dashboards.xml`, `clinical_*.xml` ni `drawable/ic_dashboard_*`.
   - Endpoints: las anotaciones Retrofit de `src/main` son exactamente las diez de R1. Todas existen en el contrato `../asclepio-api/openapi/asclepio-api.v1.json` (versión 0.7.0, misma mayor que el 0.4.0 consumido).
   - Pruebas (`app/src/test`, `app/src/androidTest`): no queda ninguna referencia a los símbolos retirados. STAFF solo aparece como fixture que se rechaza: `AccountApiTest:114`, `AutoLoginTest:99`, `LoginViewModelTest` R20, `SessionTestEnv.staffMe` y `PatientSessionLifecycleTest` R5/R6/R8.
2. **El comportamiento del paciente se conserva (R12).** La tabla `R12 → clases` de `progress/impl_DT-01-mobile.md` incluye todas las suites de `design.md` y todas pasan. Revisé el diff de las pruebas adaptadas y ninguna se debilitó:
   - `SessionManagerTest`: R1, R3 y R27 siguen comprobando `NoTenant`/`LoggedOut` y que no hay token. Solo se quitan los pasos `selectAccess`.
   - `UiNavigationTest`: R15, R11 y R18 conservan las aserciones del paciente. R15 cambia `Welcome` por `NoTenant`, y R18 añade que el paciente no monta `Login` ni `TenantPicker`.
   - `MyRequestsViewModelTest` R20: comprueba que, sin sesión de paciente, se lanza `SessionInvalidated` y no hay ninguna petición HTTP.
   - `SpeechGuideControllerTest`: solo cambia el destino por defecto.
   - `LocalizationCatalogTest` R21: solo quita prefijos que ya no existen; el umbral `>= 20` se mantiene y pasa.
   - `PatientTestEnv`: `beginLogin()` sin modo.
   - `ApiScopeTest` no se modificó.
3. **R5–R7.**
   - Restauración (`SessionManager.kt`, `refresh`, rama `toPrincipal == null`): publica `entryNotice` y luego `clearLocked()`. Eso borra el token y la expiración en RAM y en `SessionStore`, sube la generación, conserva la IPS y deja `LoggedOut` o `NoTenant`.
   - `PatientSessionLifecycleTest` R5 lo prueba con MockWebServer real. Cubre los cuatro roles, otro `kind`, un paciente incompleto, otra IPS y el caso sin IPS recordada. Comprueba que hay una sola petición por restauración, con el Bearer del token guardado, y que un segundo `refresh` no hace ninguna más (4 peticiones en total).
   - R6: `beginLogin`, `saveTenant` y `dismissEntryNotice` limpian el aviso.
   - UI: `AppNavigation.kt:180-183` muestra `EntryNotice` (texto estático con "Cerrar") solo en `LoggedOut`/`NoTenant`.
   - R7: `completeLogin` devuelve `false` antes de `store.saveSession` cuando el principal es nulo. `LoginViewModelTest` R20 cubre STAFF, otra IPS y otro `kind`. `error_not_patient` tiene el texto de R7 en los cinco idiomas (`PatientOnlyResourcesTest` R7).
4. **Desviaciones declaradas por el implementer.** Todas son aceptables:
   - **T5 y T6 en un solo commit (`4c68c8f`).** Está justificado: al borrar `AccessMode` desaparecen también `WelcomeScreen`, `selectAccess` y el cambio de acceso. El commit incluye las pruebas R3/R4 (`PatientOnlyNavigationTest`) y R8.
   - **`SessionLockRaceTest` migrada a `PatientSessionLifecycleTest` como R11 (`e835cb8`).** Encaja con lo que pide T4. La tabla "Pruebas antiguas → destino" registra el destino de cada uno de sus cinco casos.
   - **`guide_patient_login_2` reescrita en los cinco idiomas.** Hacía falta para cumplir R4, porque el texto anterior ofrecía "Paciente o Personal". El texto nuevo remite a la etiqueta real del botón (`login_change_tenant`) en cada idioma. Es un texto de interfaz, no clínico.
   - **`ErrorMessagesTest` R34.** Se volvió al texto de F-01-mobile R20 (D5). Antes fijaba el texto retirado; ahora verifica lo correcto.
   - **`authenticatedCall` eliminado.** `patientCall` hace el mismo `capture`/`finish` con Bearer y generación inmutables, y el guard `mode` pierde sentido porque `VerifiedPrincipal` solo tiene `Patient`. Las pruebas R11 de "Bearer de operación suspendida" y "respuesta y 401 antiguos" pasan.
   - **`tagline` sin uso.** La única que la usaba era `WelcomeScreen` (`f43b5f3:AccessScreens.kt:16`), así que este slice la deja muerta, y lint la marca como aviso. D8 manda conservarla expresamente, y por eso el implementer no podía borrarla sin desviarse de la spec. No bloquea, pero la propia justificación de D8 (lint UnusedResources y no dejar código muerto) pide borrarla. Ver la observación 1.
5. **T12.** Ya está hecho: la tabla R→prueba, el grep de `R<n> `, lint, `testDebugUnitTest` y `assembleDebugAndroidTest` en verde, e `init.sh` completo en VERDE. Falta la **prueba manual en emulador (teléfono y tablet)**: entrada sin IPS y con IPS, login con una cuenta de personal (aviso de R7) y restauración con un token de personal (aviso de R5 y "Cerrar"). Queda **pendiente del humano**.

## Trazabilidad R<n> → prueba (todas en verde)

| R | Pruebas |
|---|---|
| R1 | `PatientOnlyScopeTest` (1) |
| R2 | `PatientOnlyScopeTest` (2; el detector cubre anotaciones, `@HTTP`, literales, `/` inicial y `{id}`/`$id`) |
| R3 | `PatientOnlyNavigationTest` (3) |
| R4 | `PatientOnlyNavigationTest` (4) |
| R5 | `core/auth/PatientSessionLifecycleTest` (4) |
| R6 | `core/auth/PatientSessionLifecycleTest` (1) |
| R7 | `PatientOnlyResourcesTest` (2), además de `LoginViewModelTest` R20 |
| R8 | `core/auth/PatientSessionLifecycleTest` (2) |
| R9 | `PatientOnlyResourcesTest` (1) |
| R10 | `PatientOnlyResourcesTest` (1) |
| R11 | `core/auth/PatientSessionLifecycleTest` (14) |
| R12 | Regresión de las suites del paciente (PA-2, tabla en `progress/impl_DT-01-mobile.md`) |
| R13 | `PatientOnlyDocsTest` (3) |

## Tareas

T1–T11 están `[x]`. T12 sigue `[ ]` solo por la QA manual en emulador (ver arriba).

## Checkpoints

### C1 — Harness
- [x] Existen `AGENTS.md`, `CLAUDE.md`, `init.sh` (ejecutable), `feature_list.json`, `progress/current/` y `progress/history.md` (`ls`; init sección 2 `[OK]`).
- [x] Existen `docs/architecture.md`, `docs/conventions.md`, `docs/specs.md` y `docs/verification.md`.
- [x] `./init.sh` termina con exit 0 (VERDE).

### C2 — Estado
- [x] Como mucho una feature `in_progress`: solo DT-01-mobile (`feature_list.json`; done 4, retired 4, pending 1).
- [x] Toda feature `done` tiene pruebas que pasan (313/313).
- [x] `progress/current/` solo contiene `DT-01-mobile.md` (y `.gitkeep`).

### C3 — Arquitectura
- [x] Los paquetes son `core/{network,auth,ui,model,tenants}` y `feature/{account,patients,encounters,status,voice}`, sin nada de personal (`find app/src/main`; `PatientOnlyResourcesTest` R9 y `PatientOnlyNavigationTest` R4).
- [x] MVVM: ningún `feature/*/ui` importa Retrofit (grep).
- [x] Ninguna `feature/*` importa de otra (grep de imports `co.asclepio.mobile.feature.` entre features: vacío).
- [x] Sin dependencias nuevas (`git diff f43b5f3..HEAD -- app/build.gradle.kts gradle/` vacío).
- [x] Sin `Log.d`, `println`, `TODO` ni `FIXME` en `app/src/main` (grep vacío).

### C4 — Verificación
- [x] Las pruebas HTTP van contra MockWebServer real (`core/auth/SessionTestEnv.kt`, `PatientTestEnv`), sin mockear Retrofit.
- [x] `testDebugUnitTest`: 313 pruebas, todas en verde.
- [x] `lint`: 0 errores.

### C5 — Cierre de sesión
- [x] Sin archivos sin trackear; `.gitignore` cubre `build/`, `.gradle/` y `local.properties`.
- [ ] `progress/history.md` todavía no tiene la entrada de cierre de DT-01-mobile. Le toca al leader al cerrar; lo indica `progress/impl_DT-01-mobile.md`, desviación 8. No bloquea la revisión.
- [x] La última feature trabajada tiene el estado correcto antes del cierre (`in_progress`).

### C6 — SDD
- [x] Existe `specs/DT-01-mobile/{requirements,design,tasks}.md`.
- [x] EARS. Cada `R<n>` lleva su traza `F-0X.ACn`, y la traza complementaria a `ADR-0008.Ratificación` se acepta como excepción (PA-1, resuelta).
- [ ] Una feature `done` debe tener todas sus tareas `[x]`: T12 sigue `[ ]` (QA manual del humano). No bloquea la revisión, pero sí impide marcar `done`.
- [x] Cada `R<n>` tiene una prueba `R<n> ` que pasa. R12 se cubre por regresión (PA-2).

### C7 — Seguridad clínica y privacidad
- [x] El audio no sale del dispositivo: `feature/voice` solo tiene `README.md` y no hay código que suba audio.
- [x] Sin reconocimiento local la app funciona con texto. No hay dictado todavía (F-08 pendiente).
- [x] `complaint_source`: no aplica mientras no exista F-08, y este slice no lo cambia.
- [x] Sin datos personales en Logcat: no hay `Log.*` en `src/main`. Los `toString` de `Patient`, `LoginAttempt` y `AuthHandle` enmascaran.
- [x] HTTP en claro solo en debug (`app/src/debug/res/xml/network_security_config.xml`).
- [x] La app no envía horas legales (sin cambios; `PreRegistrationsApi` sin hora de llegada).
- [x] Solo paciente: `PatientOnlyScopeTest` R1/R2, y `GuardedRoute` con `canAccessRoute` antes de montar el destino o el VM (`AppNavigation.kt`).
- [x] El token no basta: un `/me` no paciente borra el token y muestra el aviso (R5/R7, `SessionManager.refresh`/`completeLogin`).
- [x] Generaciones: logout, expiración, 401 vigente, nuevo login y otra IPS invalidan; el Bearer es inmutable por operación (R11, 14 pruebas).
- [x] Persistencia sin cambios: IPS/sede y token cifrado/expiración (`design.md`, "Impacto en datos"; `SessionStore` sin cambios).
- [x] Consentimiento en el alta: sin cambios (`SignUpViewModelTest` en verde).

### C8 — Contrato
- [x] `consumes_contract: "0.4.0"`, y el snapshot es 0.7.0 (misma mayor). Las diez operaciones existen en el contrato (`jq .paths`).
- [x] Los DTO no cambian. `MeDto` conserva los opcionales `role`, `email` y `fullName` (D3).
- [x] Los errores `problem+json` se traducen a mensajes (sin cambios; `ApiCallTest` y `ErrorMessagesTest` en verde).

## Observaciones para el leader (no bloquean)

1. **`tagline`.** Este slice la deja sin uso en los cinco catálogos y lint lo avisa, pero D8 dice que se conserve. Hay que decidir: o se enmienda D8 y se borra `tagline` de `values*/strings.xml` en un commit pequeño (`LocalizationCatalogTest` R21 seguiría por encima de su umbral), o se deja tal cual y se registra en `history.md`. `ic_back` y `home_navigation` ya estaban sin uso antes del slice.
2. **T12, QA manual (humano).** Con `adb reverse tcp:3000 tcp:3000` y `API_BASE_URL=http://localhost:3000`, en teléfono y tablet:
   - entrada sin IPS (selección de IPS) y con IPS (login), sin bienvenida;
   - login con una cuenta de personal, que debe mostrar el aviso de R7;
   - restauración con un token de personal, que debe mostrar el aviso de R5 y quitarlo al pulsar "Cerrar".

   Después se registra en `progress/impl_DT-01-mobile.md` y se marca T12 `[x]`. Las instrumentadas compilan, pero no se ejecutaron en dispositivo.
3. **Cierre.** Falta la entrada en `progress/history.md` y el cambio de estado en `feature_list.json`, que corresponden al leader, igual que la actualización del ADR local 0002.
