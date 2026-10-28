# F-02-mobile — Diseño

## Resumen
Tras iniciar sesión, el paciente ve en el **inicio** el estado de su último pre-registro y puede abrir **"Mis datos"** (consultar y, mientras no esté verificado, corregir nombre, fecha de nacimiento y sexo) o enviar un **nuevo pre-registro** (sede de una lista + motivo escrito). Recepción confirma en la web (ADR 0008); la app no muestra ni calcula la hora de llegada ni el nivel de triage.

Capas (docs/architecture.md):
```
co/asclepio/mobile/
├── AppContainer.kt        # + PatientProfileApi, PreRegistrationsApi y sus repositorios
├── AppNavigation.kt       # + rutas Profile y PreRegistration; compone el inicio con el estado (D3)
├── core/
│   ├── tenants/  TenantsApi (GET /public/tenants) + PublicTenantDto, PublicSiteDto   ← se mueven desde feature/account (D2)
│   ├── model/    Sex, FULL_NAME_MAX, conversión del DatePicker (D6 de F-01)          ← se mueven desde feature/account (D2)
│   └── auth/     SessionManager: + manejo del 401 de endpoints del paciente (D7)
└── feature/
    ├── account/   HomeScreen recibe un slot de estado y la acción "Mis datos" (D3); importa de core/tenants y core/model
    ├── patients/  data/ (PatientProfileApi, DTOs, PatientProfileRepository)
    │              ui/   (ProfileScreen, ProfileViewModel, ProfileForm + validación)
    └── encounters/data/ (PreRegistrationsApi, DTOs, PreRegistrationRepository)
                   ui/   (PreRegistrationScreen/ViewModel, PreRegistrationStatusPane/ViewModel,
                          PreRegistrationForm + validación, statusText (función pura))
```

## Decisiones
| # | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D1 | **Dos features**: `feature/patients` (perfil) y `feature/encounters` (formulario y estado del pre-registro), como ya prevé docs/architecture.md. | Una sola `feature/preregistration` con todo. | Sigue el mapa ya acordado y la separación de la API (`modules/patients` y `modules/encounters`); F-08 solo tocará `feature/encounters` y `feature/voice`. |
| D2 | **Lo compartido sube a `core/`**: `TenantsApi.tenants()` con `PublicTenantDto`/`PublicSiteDto` a `core/tenants/`, y `Sex`, `FULL_NAME_MAX` y `datePickerMillisToLocalDate`/`toDatePickerMillis` a `core/model/`. `feature/account` pasa a importarlos de ahí; `GET /public/consent` y `DocumentType` (solo CC, CE y PA del alta) se quedan en `feature/account`. Las pruebas de F-01 siguen pasando, con solo cambios de `import`. | (a) Que `feature/encounters` importe `feature/account.data.PublicApi`; (b) duplicar los DTO y la interfaz en cada feature. | (a) está prohibido (`feature/A → feature/B`). (b) duplica código y pruebas para el mismo endpoint. |
| D3 | **Inicio con slot**: `HomeScreen` (de `feature/account`) recibe `status: @Composable () -> Unit` y `onOpenProfile: () -> Unit`, y elimina el texto "Pronto podrás pre-registrarte desde aquí". `AppNavigation` pasa como `status` el `PreRegistrationStatusPane` de `feature/encounters` (con su propio ViewModel, ligado a la entrada `Home`) y las rutas nuevas `Route.Profile` y `Route.PreRegistration`. Tras el 201 (R12) se vuelve a `Home` quitando `PreRegistration` de la pila y el estado se refresca con el pre-registro de la respuesta. | Mover el inicio a `feature/encounters`; que `HomeScreen` importe el panel de estado. | La regla de dependencias solo permite que `AppNavigation` conozca varias features. El cierre de sesión y el aviso de identidad (F-01) siguen donde están. |
| D4 | **El estado se lee de `GET /patient/pre-registrations`** (solo el primer elemento, PA-M02-3) y el formulario usa la vista 201 del `POST`. No se usa `GET /patient/pre-registrations/{id}`. | Guardar el id del último pre-registro y pedir el detalle. | La lista ya devuelve el último con el estado efectivo calculado por la API (R10 y R12 de F-02-api); guardar un id añade estado local sin beneficio. |
| D5 | **Estados como `String`, no `enum`**: `PatientPreRegistrationDto.status` y `EncounterRefDto.status` se declaran `String`, y la función pura `statusText(dto): @StringRes Int` y `isInProgress(dto): Boolean` aplican las tablas de PA-M02-2 y la definición de "en curso"; un valor desconocido da "Estado no disponible…" y cuenta como en curso (R18, R19). La app no compara `expiresAt` con el reloj del dispositivo: el `EXPIRED` lo decide la API (R22). | `enum class` con `@SerialName`. | Un valor nuevo de la API rompería la deserialización de toda la lista (`InvalidBody`) y el paciente dejaría de ver su estado. |
| D6 | **Sin nivel ni hora de llegada por construcción**: los DTO del paciente solo declaran los campos del contrato (`id`, `siteId`, `siteName`, `status`, `preRegisteredAt`, `expiresAt`, `encounter { id, status }`) y el `Json` común ya tiene `ignoreUnknownKeys = true`; el `UiState` del estado solo contiene `siteName`, el `@StringRes` del estado, las horas mostradas (`formatBogota`) y `canCreate` (R21, R22). | Pasar el DTO completo al Composable. | Si la API añadiera un campo sensible, nunca llegaría a la UI. |
| D7 | **401 de un endpoint del paciente → `SessionManager.logout()`** (R23), desde los repositorios de `patients` y `encounters` a través de una función de `core/auth` (`suspend fun <T> SessionManager.patientCall(block): ApiResult<T>`, que envuelve `apiCall` y llama a `logout()` ante un 401). `AppNavigation` ya reinicia la pila a `Login` al cambiar el estado. | Tratar el 401 en cada ViewModel; un `Authenticator` de OkHttp. | Un único punto probado; el `Authenticator` está pensado para reintentar con un token nuevo, y aquí no hay refresh token. |
| D8 | **`complaintSource` preparado para F-08**: `enum class ComplaintSource { TYPED, VOICE_ON_DEVICE }` con `@SerialName`; `PreRegistrationForm` tiene `complaintSource = ComplaintSource.TYPED` y en este slice ningún camino lo cambia (R10). F-08 añadirá el dictado que lo fije en `VOICE_ON_DEVICE`. No se añade UI de micrófono ni se usa `RECORD_AUDIO`. | Enviar `"TYPED"` como literal; añadir ya el botón de dictado deshabilitado. | Deja el punto de extensión sin código especulativo de voz. |
| D9 | **Perfil: `PATCH` con solo lo cambiado** (R3, R4): `UpdatePatientProfileRequest(fullName: String? = null, birthDate: String? = null, sex: Sex? = null)` con valor por defecto `null`; como el `Json` común ya usa `encodeDefaults = false` (F-01), los campos que se quedan en su valor por defecto no viajan. La diferencia se calcula con una función pura `diff(loaded, form)`. | Enviar siempre los tres campos. | La API audita los campos cambiados (`metadata.fields`, R2 de F-02-api); enviar los tres registraría cambios que no ocurrieron. |
| D10 | **Validación local** en funciones puras `validateProfile(form, clock)` (reutiliza `isAdult` de `core/auth` y `FULL_NAME_MAX`) y `validatePreRegistration(form)` (sede y motivo de 1 a 2 000 tras `trim`, `CHIEF_COMPLAINT_MAX = 2000` del contrato). | Validar solo en la API. | Mismo criterio que D17 de F-01: experiencia de uso; la API valida igual. |
| D11 | **Sedes**: el formulario pide `GET /public/tenants` cada vez que se abre y filtra por el `tenantId` recordado (R8, R9). La sede recordada solo se usa para preseleccionar; elegir otra no la modifica (PA-M02-5). | Guardar la lista de sedes en DataStore. | La lista es pequeña y puede cambiar; una sede borrada se detecta igualmente con el 422 (R14). |
| D12 | **Errores**: funciones puras por pantalla (`profileErrorMessage`, `preRegistrationErrorMessage`, `statusErrorMessage`) sobre `ApiError.commonMessage()` de `core/network`, con el mapeo de `errors[].field` de R7 y R15; 403 y 404 caen en el error general (R24). El 429 reutiliza el bloqueo con `Retry-After` de D15 de F-01. | Mostrar el `detail` de la API. | D8 de F-01: la UI nunca muestra textos de la API. |
| D13 | **Privacidad** (R27): `ProfileForm`, `PreRegistrationForm`, `PatientProfileDto`, `UpdatePatientProfileRequest` y `CreatePreRegistrationRequest` sobrescriben `toString()` con `***` en nombre, documento, fecha de nacimiento y motivo. El estado vive en el `ViewModel` (sobrevive a la rotación); si Android mata el proceso, el motivo se pierde. | Guardar el borrador del motivo en `SavedStateHandle` o DataStore. | El motivo es texto clínico libre (RNF-05); no debe llegar a disco ni al `Bundle` del sistema. |
| D14 | **Layout** (R29): se reutiliza `paneMode` de `core/ui/WindowLayout.kt`. En `Dual`, el formulario de pre-registro es un `Row` con la lista de sedes (peso 1) y el motivo con el botón de envío (peso 1); en `Single`, una columna con desplazamiento. "Mis datos" y el inicio usan una columna de 600 dp como máximo. | `ListDetailPaneScaffold` de `material3-adaptive`. | No hay lista y detalle reales (PA-M02-3: solo el último pre-registro); no justifica otra dependencia (D16 de F-01). |
| D15 | **Pendiente heredado de F-01**: el valor por defecto de `API_BASE_URL` en `app/build.gradle.kts` pasa de `http://10.0.2.2:3000` a `http://localhost:3000` (con `adb reverse tcp:3000 tcp:3000`), y se actualizan `docs/architecture.md`, `docs/verification.md` y `README.md`. | Mantener `10.0.2.2`. | Android 17 / targetSdk 37 bloquea la red local sin `ACCESS_LOCAL_NETWORK` (lección de F-01, progress/history.md). |

## Impacto en contrato
- **Sin cambios en el contrato.** La app **consume 0.4.0** (vigente en `../asclepio-api/openapi/`; los endpoints del paciente se publicaron en 0.3.0 y 0.4.0 no los cambia). El leader debe fijar `"consumes_contract": "0.4.0"` en `feature_list.json`, y T1 cambia `BuildConfig.CONSUMED_CONTRACT` a `"0.4.0"`.
- Solo endpoints del paciente y públicos; ninguno de personal (`/patients`, `/pre-registrations`, `/encounters`…) (R26).
- DTOs escritos a mano según el snapshot, todos `@Serializable`:

| DTO de Kotlin | Esquema del contrato | Uso |
|---|---|---|
| `PatientProfileDto(id, documentType: String?, documentNumber: String?, fullName: String?, birthDate: String?, sex: Sex?, editable: Boolean)` | `PatientProfileDto` | `GET` y `PATCH /api/v1/patient/profile` → 200 |
| `UpdatePatientProfileRequest(fullName?, birthDate?, sex?)` | `UpdatePatientProfileDto` | `PATCH /api/v1/patient/profile` (solo campos cambiados, D9) |
| `CreatePreRegistrationRequest(siteId, chiefComplaintText, complaintSource: ComplaintSource)` | `CreatePreRegistrationDto` | `POST /api/v1/patient/pre-registrations` → 201 |
| `PatientPreRegistrationDto(id, siteId, siteName, status: String, preRegisteredAt, expiresAt, encounter: EncounterRefDto?)` | `PatientPreRegistrationDto` | respuesta 201 del `POST`; `GET /api/v1/patient/pre-registrations` → 200 `List<…>` |
| `EncounterRefDto(id, status: String)` | `EncounterRefDto` | dentro de `PatientPreRegistrationDto` |
| `PublicTenantDto`, `PublicSiteDto` (movidos a `core/tenants`) | `PublicTenantDto`, `PublicSiteDto` | `GET /api/v1/public/tenants` (sedes, R8) |

- `documentType` del perfil es `String?` (el contrato no declara `enum`; puede ser `TI` o `RC` si recepción creó al paciente). `birthDate` es `YYYY-MM-DD`; `preRegisteredAt` y `expiresAt`, ISO-8601 en UTC que solo se formatean (R17, R22).
- Errores esperados (contrato y F-02-api): `GET profile` → 401, 403; `PATCH profile` → 400, 401, 403, 409, 422, 429; `POST pre-registrations` → 400, 401, 403, 409, 422, 429; `GET pre-registrations` → 401, 403. Los 5xx y la falta de red se tratan en R24.
- Las peticiones no llevan `patientId`, `tenantId` ni horas: la API respondería 400 (R46 y R47 de F-02-api) (R10, R22, R26).

## Impacto en datos
- **Sin migración ni cambios en la base de datos** (la app no tiene base de datos).
- **Sin datos locales nuevos**: el perfil, la lista de sedes, el motivo y el estado del pre-registro viven solo en memoria (ViewModel). El Preferences DataStore de F-01 (`asclepio_session`) no cambia: la sede recordada no se modifica al elegir otra (PA-M02-5).
- La app no envía horas: `preRegisteredAt`, `expiresAt` y `arrival_at` los fija el servidor (RNF-10, F-02.AC3).

## Dependencias nuevas
Ninguna.

## Riesgos y pruebas
**Pruebas JVM** (`app/src/test`, MockWebServer + Retrofit real, `kotlinx-coroutines-test`, `Clock.fixed`, `Dispatchers.setMain(StandardTestDispatcher())`):
- `feature/patients`: perfil cargado, valores nulos y etiquetas de documento (R1); `editable = false` (R2); cuerpo exacto del `PATCH` con uno, dos y tres campos cambiados, verificado con `takeRequest()` (R3); sin cambios → botón deshabilitado y `requestCount == 0` (R4); validación sin petición, incluidos los casos frontera de edad de D5 de F-01 (R5); 409 → aviso y nuevo `GET` (R6); 422 y 400 con `errors[]` (R7).
- `feature/encounters`: sedes de la IPS recordada, preselección y sede recordada ausente (R8); IPS ausente, sin sedes y fallo (R9); cuerpo exacto del `POST` con `complaintSource = "TYPED"` y sin campos de hora (R10, R22); validación y contador (R11); 201 → estado actualizado (R12); 409, 422 y 400 (R13–R15); `statusText` e `isInProgress` con cada fila de la tabla de PA-M02-2 y un valor desconocido (R17, R19); lista vacía y fallo (R18, R20); respuesta con un campo `level` y `arrivalAt` añadidos que no aparece en el `UiState` (R21, R22).
- Comunes: 401 en cada endpoint del paciente → `SessionState.LoggedOut` con la IPS conservada (R23); 429 con tiempo virtual, 403, 404, 5xx y sin red (R24); doble envío (R25); reflexión sobre `PatientProfileApi` y `PreRegistrationsApi` (rutas bajo `api/v1/patient/`, sin `@Path`/`@Query` de paciente, cuerpos sin `patientId`/`tenantId`) (R26); `toString` enmascarado (R27); cada error y estado → `R.string` y ausencia del recurso `home_preregister_soon` (R16, R28); `paneMode` para el formulario (R29).
- Las pruebas de F-01 (121 JVM) siguen en verde tras mover código a `core/` (D2).

**Prueba manual** (humano, teléfono y tablet): T15 y T16 de tasks.md. Cubren lo visual de R1, R2, R16, R17 y R29 y el recorrido completo con la confirmación hecha en la web o con la API.

**Riesgos**
- La confirmación por recepción (F-02-web) puede no existir aún al probar: el estado `CONFIRMED` se provoca llamando a `POST /api/v1/pre-registrations/{id}/confirm` de la API local con un token de `RECEPTION` de desarrollo (solo en la prueba manual; la app nunca llama a ese endpoint).
- Sin sondeo (PA-M02-4), el paciente no ve el cambio de estado hasta volver a la app o pulsar "Actualizar".
- El límite de 10 peticiones por minuto por IP (R14 de F-02-api) puede saltar en una red Wi-Fi compartida; se informa con R24.
