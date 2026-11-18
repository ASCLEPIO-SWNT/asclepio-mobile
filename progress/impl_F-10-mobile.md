# F-10-mobile — Implementación

Rama `feature/F-10-mobile`. Contrato consumido: **0.10.0** (`CONSUMED_CONTRACT = "0.10.0"`; `consumes_contract` sigue en 0.10.0). Dependencia nueva única: `com.squareup.okhttp3:okhttp-sse:5.5.1` (misma versión que OkHttp, D1). Sin cambios en `../asclepio-api/`.

## Tareas
- [x] T1 okhttp-sse + `CONSUMED_CONTRACT` 0.10.0; `./gradlew lint` en verde.
- [x] T2 `QueuePositionDto` + `QueuePositionStream` contra MockWebServer.
- [x] T3 `QueuePositionViewModel` (backoff, RECONNECTING con `last`, 401 → `ended` + login, DONE sin reconexión).
- [x] T4 `QueuePositionCard` (función pura `queueCardContent` en JVM + prueba Compose instrumentada), catálogo en 5 idiomas, `liveRegion` educado.
- [x] T5 Integración en la entrada `Home` de `AppNavigation` con `repeatOnLifecycle(STARTED)` + prueba de fuente.
- [ ] **T6 QA manual en dispositivo — pendiente para el humano** (no se intentó): `adb reverse tcp:3000 tcp:3000`, prerregistro → confirmar en recepción → `triage.sh` de otro paciente más grave → la posición cambia sin refrescar; modo avión → "Sin conexión · actualizado hace X min" y reconexión; volver a primer plano reabre el stream; TalkBack anuncia los cambios. Ejecutar también `connectedDebugAndroidTest` para `QueuePositionCardInstrumentedTest` (solo se compiló con `assembleDebugAndroidTest`).

## Archivos tocados
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — okhttp-sse, contrato 0.10.0.
- `app/src/main/java/co/asclepio/mobile/core/auth/PatientCall.kt` — `patientStream`: Bearer/generación capturados al abrir; en el evento terminal `finish(handle, 401)` (un 401 vigente cierra solo su sesión; uno de otra generación lanza `SessionInvalidated`).
- `app/src/main/java/co/asclepio/mobile/feature/encounters/queue/QueuePositionDto.kt` — DTO + enums con respaldo `UNKNOWN`.
- `app/src/main/java/co/asclepio/mobile/feature/encounters/queue/QueuePositionStream.kt` — `callbackFlow` sobre `EventSources.createFactory`, `readTimeout` 45 s (latido 15 s), `Accept: text/event-stream`, sin logs.
- `app/src/main/java/co/asclepio/mobile/feature/encounters/queue/QueuePositionViewModel.kt` — `QueueUiState`, `follow()` (bucle en la corrutina del llamador), backoff 1→30 s.
- `app/src/main/java/co/asclepio/mobile/feature/encounters/ui/QueuePositionCard.kt` — `queueCardContent` + Composable.
- `app/src/main/java/co/asclepio/mobile/feature/encounters/ui/PreRegistrationStatusViewModel.kt` — `followsQueue()`.
- `app/src/main/java/co/asclepio/mobile/AppContainer.kt`, `AppNavigation.kt` — `queuePositionStream`; VM y tarjeta en la entrada Home.
- `app/src/main/res/values{,-en,-pt,-fr,-de}/queue_position.xml` — 17 textos `queue_*`.
- Pruebas: `QueuePositionStreamTest`, `QueuePositionViewModelTest`, `QueuePositionCardTest`, `QueuePositionLifecycleTest`, `androidTest/QueuePositionCardInstrumentedTest`; `PatientTestEnv.client` pasa a público.
- Docs: `docs/architecture.md` (paquete `encounters/queue`), `docs/verification.md` (sección F-10).

## Decisiones de interpretación (para el reviewer)
1. **"Entrada `Status`" del design = zona de estado del prerregistro en `Home`.** `Route.Status` es la pantalla de salud de F-00 (solo debug, sin sesión), donde no hay token; el glosario de la spec ("`PreRegistrationStatus`") y el caso de uso apuntan a `PreRegistrationStatusPane`, que se compone en la entrada `Home`. Ahí se integró.
2. **Cuándo se abre el stream (R1).** Mientras el último prerregistro está en curso (`!canCreate`: `PENDING` o atención sin terminar). Es un superconjunto de "tenga una atención": incluye `PENDING` para que R7/PA-F10-2 ("Acércate a recepción…") se vea antes de la confirmación y la confirmación llegue en vivo. En `Loading`/`Error` del estado se conserva la decisión anterior para no cortar el stream en cada "Actualizar".
3. **Antigüedad "hace X min" (R8).** La prueba existente de F-02 R22 prohíbe el reloj del dispositivo en `feature/encounters`. La antigüedad es una duración, no una hora legal: se mide con un reloj **monótono** (`SystemClock.elapsedRealtime`) inyectado desde `AppNavigation`; `QueueUiState.receivedAt` es ese valor en ms. No se tocó la prueba R22.
4. **Posición 1 y estimación.** Con posición 1 se muestra la fase, "Eres el siguiente" y, si existe, el rango (o el aviso de R6). R6 se aplica siempre que haya posición.
5. **403/5xx/cortes** reconectan con backoff (D2 solo distingue 401). Un `Closed` sin DONE (p. ej. el cierre del servidor al vencer el token) también reconecta; si la sesión venció, `finish` lanza `SessionInvalidated` y el bucle termina.

## Observaciones fuera de alcance (no implementadas)
- El texto del estado del prerregistro (p. ej. "Pendiente") no se refresca cuando el stream pasa de `NO_ACTIVE` a `WAITING_TRIAGE`; solo al volver a primer plano o con "Actualizar". Conviene decidir si la tarjeta debe disparar un `refresh()`.
- `PatientOnlyScopeTest` solo cuenta operaciones Retrofit; el stream usa OkHttp directo y su ruta (`api/v1/patient/queue-position/stream`) se verifica en `QueuePositionStreamTest` y `QueuePositionLifecycleTest`. No se amplió la lista de "diez operaciones".

## Trazabilidad R<n> → prueba
| R | Prueba |
|---|---|
| R1 | `QueuePositionStreamTest::R1 abre el stream del paciente con su Bearer y emite cada evento position como instantanea`; `::R1 ignora el latido ping y los eventos de otro tipo`; `QueuePositionViewModelTest::R1 el estado inicial no tiene dato y esta conectando en vivo`; `QueuePositionLifecycleTest::R1 el stream se sigue con un pre-registro en curso…`; `::R1 R2 follow solo se llama desde AppNavigation y siempre dentro de repeatOnLifecycle STARTED`; `::R1 R2 el stream solo se abre desde el ViewModel y la tarjeta solo se compone en la entrada Home` |
| R2 | `QueuePositionStreamTest::R2 cancelar la recoleccion cancela la llamada y no deja conexiones abiertas`; `::R2 sin sesion autenticada no se abre ninguna conexion`; `QueuePositionViewModelTest::R2 cancelar follow detiene el bucle y no programa mas reintentos`; `QueuePositionLifecycleTest::R2 no hay trabajo en segundo plano para la fila` (+ las dos `R1 R2`) |
| R3 | `QueuePositionCardTest::R3 en espera de triage con posicion mayor que 1…`; `::R3 en espera del medico con una persona delante usa el singular`; `QueuePositionCardInstrumentedTest::R3 R11 waiting card…` |
| R4 | `QueuePositionCardTest::R4 con posicion 1 muestra Eres el siguiente…`; `QueuePositionCardInstrumentedTest::R4 R6 next patient without enough data` |
| R5 | `QueuePositionCardTest::R5 en CALLED muestra Te estan llamando y en DONE Atencion finalizada`; `QueuePositionViewModelTest::R5 DONE termina sin reconectar…`; `QueuePositionStreamTest::R5 el cierre del servidor tras DONE…`; `QueuePositionCardInstrumentedTest::R5 called phase…` |
| R6 | `QueuePositionCardTest::R6 con INSUFFICIENT_DATA muestra el aviso en lugar del rango`; instrumentada `R4 R6` |
| R7 | `QueuePositionCardTest::R7 en NO_ACTIVE muestra Acercate a recepcion sin posicion`; instrumentada `R7 no active shows the reception hint` |
| R8 | `QueuePositionViewModelTest::R8` ×4 (backoff 1…30 s, reinicio tras evento, RECONNECTING con `last`, vuelta a LIVE); `QueuePositionStreamTest::R8` ×3 (cortes, 403/5xx); `QueuePositionCardTest::R8` ×2; instrumentada `R8 reconnecting keeps the last data with its age` |
| R9 | `QueuePositionStreamTest::R9 un 401 termina con Failed 401 y cierra la sesion conservando la IPS`; `::R9 un 401 de un stream de una sesion anterior no cierra la sesion nueva`; `QueuePositionViewModelTest::R9` ×3 (incluye stream real → `rootRoute` = `Login`); `QueuePositionCardTest::R9 terminado sin dato…` |
| R10 | `QueuePositionStreamTest::R10` ×3 (solo campos del contrato, enums desconocidos, sin logs); `QueuePositionCardTest::R10 la tarjeta solo muestra los campos de la instantanea` |
| R11 | `QueuePositionCardTest::R11` ×2 (cinco catálogos y textos en español; `LiveRegionMode.Polite` sin literales); instrumentada `R3 R11` |

## Resultados
- `./gradlew testDebugUnitTest`: **393 pruebas, 0 fallos** (39 nuevas en JVM: Stream 13, ViewModel 10, Card 12, Lifecycle 4; el resto, existentes).
- `./gradlew assembleDebugAndroidTest`: compila (`QueuePositionCardInstrumentedTest`, 5 pruebas, **no ejecutadas**: sin dispositivo).
- `./init.sh` (completo): `FAIL: 0 · WARN: 0 · Estado: VERDE`, exit 0.
