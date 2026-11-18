# Revisión de F-10-mobile: posición en la fila en vivo (segunda pasada)

- **Rama:** `feature/F-10-mobile`, revisada con `git diff develop...HEAD`. Hay 10 commits: `a7486ea..2e5c8d0`. Los dos nuevos son `1e957b8` (fix) y `2e5c8d0` (registro de la corrección).
- **Fecha:** 2026-11-18. Revisión hecha por un reviewer independiente.
- **Interpretaciones aceptadas por el leader.** Aquí se juzga si son correctas, no si la elección fue buena:
  - la tarjeta va en la entrada `Home`;
  - el stream sigue abierto mientras el último prerregistro esté en curso, incluido `PENDING`;
  - "hace X min" se mide con un reloj monótono.

## Veredicto: **APROBADO**

El bloqueante de la primera pasada (R1/R5: tras `DONE`, un prerregistro nuevo no volvía a abrir el stream) está corregido y tiene pruebas. No encontré regresiones. Solo queda T6, la QA manual, que es del humano.

## Verificación del arreglo (motivo 1 de la primera pasada)

| Punto | Resultado | Evidencia |
|---|---|---|
| Un prerregistro nuevo reinicia el estado | OK | `QueuePositionViewModel.track(key)`. Con un id distinto escribe `QueueUiState(key = key)`, de modo que `ended` vuelve a `false`. `R1 tras DONE un prerregistro nuevo vuelve a abrir el stream` comprueba que `opened == 2` y que el estado pasa a `LIVE` con `key = "pre-2"`. |
| El mismo prerregistro tras `DONE` no reconecta | OK | Con el mismo id, `track` no cambia nada y `follow()` termina en el acto. `R5 tras DONE el mismo prerregistro no vuelve a abrir el stream` comprueba que `opened == 1`. |
| La clave sale del prerregistro en curso | OK | `PreRegistrationStatusViewModel.show()` asigna `_queueKey = dto.id.takeIf { isInProgress(dto) }`, el mismo predicado que `canCreate`. Si la lista viene vacía, la clave queda en `null`. Durante `Loading` o `Error` conserva su valor. La clave también se actualiza por `repository.created`, así que un prerregistro recién creado se sigue de inmediato. `R1 el stream sigue al prerregistro en curso por su id…` cubre la secuencia en curso → 500 → cerrado → `PENDING` → vacío, con MockWebServer. |
| La snapshot conserva la clave | OK | `_state.update { QueueUiState(…, key = it.key) }`. |
| La tarjeta no mezcla atenciones | OK | `showsQueueCard(queueKey, state)`: con una clave, solo muestra el estado de esa clave; sin clave, solo el final (`ended`). `R5 la tarjeta no muestra Atencion finalizada para un prerregistro nuevo en curso`. |
| Ciclo de vida | OK | `LaunchedEffect(queueVm, queueKey)` → `track(key)` (solo memoria, sin E/S) → `repeatOnLifecycle(STARTED) { follow() }`. Cuando la clave cambia o pasa a `null`, el efecto se cancela y con él la conexión. La prueba de fuente `R1 R2 follow solo se llama … dentro de repeatOnLifecycle STARTED` sigue en verde. |
| 401 y `SessionInvalidated` siguen siendo terminales | OK | El código de la corrección no los toca. Las pruebas R9 siguen en verde. |

## Puntos de foco (revalidados)

| Foco | Resultado | Evidencia |
|---|---|---|
| Ninguna recolección ni conexión fuera de `repeatOnLifecycle(STARTED)` (R2) | OK | `AppNavigation.kt`, entrada `Home`. `queueKey` y `state` se recogen con `collectAsStateWithLifecycle`. El tick de la tarjeta corre en `repeatOnLifecycle(STARTED)`. El paquete `queue/` no usa `viewModelScope` ni tiene servicios (`QueuePositionLifecycleTest`). |
| Backoff de 1 a 30 s y reinicio | OK | `R8 … 1 2 4 8 16 y 30 s` y `R8 el backoff vuelve a 1 s tras recibir un evento`. |
| Un 401 detiene la reconexión y lleva al login | OK | `patientStream` → `finish(handle, 401)`. `R9 con el stream real un 401 cierra la sesion y la raiz pasa al inicio`. Un 401 de otra generación no cierra la sesión nueva. |
| `DONE` detiene la reconexión | OK | Ocurre dentro de la atención y con el mismo id. La atención siguiente vuelve a abrir el stream (ver arriba). |
| Se conserva el último dato durante `RECONNECTING` | OK | `copy(connection = RECONNECTING)`. `R8 al cortarse conserva el ultimo dato…`. |
| Sin contenido de eventos en los logs (R10) | OK | Ningún `Log.`, `println` ni `TODO` en el diff de `app/src/main` (grep: 0). El OkHttp no tiene `HttpLoggingInterceptor`. Prueba de fuente `R10`. |
| `EventSource` se cancela al cerrar el flow | OK | `awaitClose { source.cancel() }`. `R2 cancelar la recoleccion … no deja conexiones abiertas`. |
| DTO tolerante a enums desconocidos | OK | `FallbackEnumSerializer` con `UNKNOWN` y `ignoreUnknownKeys`. Pruebas `R10`. |
| Nombres de prueba `R<n> ` con espacio | OK | `grep -rnE 'fun \`R[0-9]+:' app/src` da 0 coincidencias. Las pruebas nuevas usan `R<n> `. |
| Catálogo de localización completo | OK | 17 textos `queue_*` en cada uno de `values`, `-en`, `-pt`, `-fr` y `-de`. Pruebas `R11`. |
| Única dependencia nueva: `okhttp-sse`, con la versión de OkHttp | OK | `okhttp-sse` usa `version.ref = "okhttp"` (5.5.1). La corrección no añade dependencias. |

## Checkpoints

### C1. Harness
- [x] Existen los archivos base e `init.sh` es ejecutable.
- [x] Existen los documentos de `docs/*`. `architecture.md` y `verification.md` incluyen F-10.
- [x] `./init.sh` completo: `FAIL: 0 · WARN: 0 · Estado: VERDE`, exit 0 (ejecutado en esta pasada).

### C2. Estado
- [x] Una sola feature `in_progress`: F-10-mobile.
- [x] 396 pruebas JVM, 0 fallos. 42 están en clases `*Queue*`. Las cifras coinciden con lo que declara el implementer.
- [x] `progress/current/` solo contiene `F-10-mobile.md`.

### C3. Arquitectura
- [x] Paquetes `feature/encounters/{queue,ui}`, documentados. No hay rutas de personal.
- [x] MVVM: `queueKey` y `state` son `StateFlow`, y el Composable no llama a la red.
- [x] Ningún `feature/*` importa de otro. `AppNavigation` hace de unión.
- [x] La dependencia nueva está justificada (D1).
- [x] No hay `Log.d`, `println` ni TODOs.

### C4. Verificación
- [x] Hay pruebas por ViewModel y por stream contra MockWebServer, y `PreRegistrationStatusViewModel.queueKey` también se prueba contra MockWebServer.
- [x] `testDebugUnitTest`: 396 pruebas, 0 fallos.
- [x] `lint`: 0 errores.

### C5. Cierre de sesión
- [x] No hay archivos sin trackear salvo este `progress/review_F-10-mobile.md`.
- [ ] `progress/history.md` todavía no tiene entrada de F-10. La escribe el leader al cerrar. No bloquea.
- [x] F-10-mobile sigue `in_progress`, que es lo correcto con T6 pendiente.

### C6. SDD
- [x] Están `requirements`, `design` y `tasks`.
- [x] Los requisitos usan EARS y cada `R<n>` apunta a un `F-10.ACn`.
- [ ] T1–T5 están marcadas `[x]`. **T6 (QA manual en dispositivo) sigue `[ ]` a propósito porque la hace el humano.** No es motivo de rechazo, pero tiene que estar hecha antes de `done`.
- [x] R1–R11 tienen cada uno al menos una prueba `R<n> ` en verde, incluidas las nuevas de R1 y R5.

### C7. Seguridad clínica y privacidad
- [x] Audio, texto sin reconocedor, `complaint_source` y cleartext: el diff no los toca.
- [x] No hay datos personales en Logcat. `queueKey` es el id del prerregistro, solo vive en memoria y no se registra en ningún log.
- [x] La app no envía horas legales. `receivedAt` es monótono y la prueba R22 de F-02 sigue en verde.
- [x] Alcance de paciente: Bearer del paciente y ruta `patient/…`. Sin sesión autenticada no se abre nada.
- [x] El Bearer y la generación no cambian durante la operación (`R9 … sesion anterior`).
- [x] Persistencia: el último dato y `queueKey` solo viven en memoria (D4).
- [x] Consentimiento: no aplica.

### C8. Contrato
- [x] `consumes_contract`, `CONSUMED_CONTRACT` e `info.version` coinciden: 0.10.0.
- [x] Los DTOs encajan con `QueuePositionDto` y `WaitEstimateDto`. El evento `position`, el `: ping` y el cierre tras `DONE` coinciden con el contrato.
- [x] Errores: un 401 lleva al login. Un 403, un 5xx o un corte reconectan mostrando "Sin conexión".

## Observaciones no bloqueantes (pendientes)
- `queue_ahead_one` y `queue_ahead_many` se usan en lugar de `<plurals>`, y lint avisa con `PluralsCandidate`. El implementer explica que `UiMessage` no admite plurales.
- El texto del estado del prerregistro no se refresca cuando el stream pasa de `NO_ACTIVE` a `WAITING_TRIAGE`. Solo cambia en `ON_START` o con "Actualizar".
- `PatientOnlyScopeTest` no cubre el stream de OkHttp.
- **T6, QA manual del humano:**
  1. Ejecutar `adb reverse tcp:3000 tcp:3000`.
  2. Hacer un prerregistro y confirmarlo en recepción.
  3. Ejecutar `triage.sh` con otro paciente más grave: la posición debe cambiar sin refrescar.
  4. Poner el modo avión: debe aparecer "Sin conexión · actualizado hace X min" y luego reconectar.
  5. Volver la app a primer plano: el stream debe reabrirse.
  6. Con TalkBack, comprobar que anuncia los cambios.
  7. Llevar una atención hasta `DONE` y crear otro prerregistro en la misma sesión: el stream debe reabrirse y no debe quedar "Atención finalizada".
  8. Ejecutar `connectedDebugAndroidTest` para `QueuePositionCardInstrumentedTest`, que hasta ahora solo está compilada.
