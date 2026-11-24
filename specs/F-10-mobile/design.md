# F-10-mobile — Diseño

## Resumen

| Lugar | Qué aporta |
|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `com.squareup.okhttp3:okhttp-sse`, con la misma versión que OkHttp (5.5.1). `CONSUMED_CONTRACT = "0.10.0"`. |
| `feature/encounters/queue/QueuePositionDto.kt` | DTO con kotlinx.serialization (enums con el valor `UNKNOWN` de respaldo). |
| `feature/encounters/queue/QueuePositionStream.kt` | `fun stream(): Flow<StreamEvent>` con `callbackFlow` sobre `EventSources.createFactory(client)`, usando el `OkHttpClient` autenticado del `AppContainer`. `StreamEvent` es `Snapshot \| Closed \| Failed(code)`. |
| `feature/encounters/queue/QueuePositionViewModel.kt` | Bucle de reconexión con backoff (pura, con un `delay` inyectable) → `StateFlow<QueueUiState>`. `QueueUiState` lleva `last: Snapshot?`, `receivedAt`, `connection: LIVE \| RECONNECTING`, `ended`. |
| `feature/encounters/ui/QueuePositionCard.kt` | Tarjeta de R3–R8. |
| `AppNavigation.kt` | En la entrada `Status`, `repeatOnLifecycle(STARTED)` para recoger el flow (R1, R2). |

## Decisiones

| # | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D1 | `okhttp-sse`. | Parsear SSE a mano o usar Ktor. | Es de Square, va sobre el OkHttp ya instalado y ocupa unos pocos KB. |
| D2 | Reconexión propia con backoff de 1→30 s. | El `retry:` de SSE. | Hay que distinguir 401 (R9) y mostrar `RECONNECTING`. |
| D3 | Solo en primer plano, con `repeatOnLifecycle`. | Un foreground service. | Batería. El push queda fuera del alcance. |
| D4 | Se conserva el último dato en memoria, no en disco. | DataStore. | Basta para la sesión de espera. Al volver a abrir llega una instantánea nueva enseguida. |

## Impacto en contrato
Consume 0.10.0 y no regenera tipos (el DTO va a mano, como el resto de la app).

## Impacto en datos
Sin impacto en datos.

## Riesgos y pruebas
- **JVM con `MockWebServer`:** respuesta `text/event-stream` con la instantánea, el corte de la conexión y el reintento, el 401 y el `DONE`.
- **ViewModel con el reloj de corrutinas:** la secuencia del backoff y el reinicio tras un evento.
- **Prueba de fuente:** el flow no se recoge fuera de `repeatOnLifecycle`.
- **Manual en dispositivo:** con `adb reverse` y `triage.sh`.
