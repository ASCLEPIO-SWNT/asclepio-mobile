# Revisión F-11-mobile — Entrevista de síntomas con IA en el teléfono

- **Rama:** `feature/F-11-mobile` (HEAD `04d0596`), diff `git diff origin/develop...HEAD` (63 archivos).
- **Revisor:** reviewer, 2026-11-19. Primera revisión: RECHAZADO (motivo 1). Segunda: APROBADO tras `04d0596`.

## Veredicto: **APROBADO** (segunda revisión, tras `04d0596`)

La primera revisión rechazó por el motivo 1, la salida accidental de la pantalla de alarma. `04d0596` lo corrige y lo verifiqué. El motivo 2, la entrada en `history.md`, lo escribe el leader al cerrar y no bloquea (C5, nota abajo).

- **`./init.sh` completo (2.ª corrida):** `FAIL: 0 · WARN: 0 · Estado: VERDE`, exit 0.
- **`testDebugUnitTest`:** 486 pruebas, 0 fallos. Son las 484 anteriores más 2 nuevas, sin regresiones.

### Verificación de la corrección (`04d0596`)
**No se puede salir mientras el envío está pendiente o reintentando.** El bloqueo es `redFlagBlocksBack(state, urgent)`: `state is RedFlag && urgent != Sent && urgent !is Failed` (`InterviewScreen.kt`). Incluye `urgent == null`, el instante antes de que arranque `sendUrgent`. Cada vía de salida queda cerrada así:

| Vía | Bloqueo |
|---|---|
| Gesto o botón atrás del sistema | `BackHandler(enabled = locked) {}` en `InterviewSection`. Se compone dentro del contenido de `NavDisplay`, así que tiene prioridad sobre su manejador. Además, `NavDisplay.onBack` se guarda con `!backLocked`. |
| "Volver" superior | Se oculta con `backStack.size > 1 && !backLocked` (`AppNavigation.kt:202`). |
| Botones de la propia alarma | "Volver al inicio" solo aparece con `Sent` o `Failed`. El formulario (`onViewStatus`, etc.) no se compone mientras dura la entrevista. |
| Barra superior de `MainActivity` (guía y preferencias) | Son paneles superpuestos y no tocan la pila. Un cambio de idioma recrea la Activity, pero la pila (`rememberNavBackStack`) y los ViewModel se conservan, y `DisposableEffect(locked)` vuelve a fijar `backLocked`, que es un `remember` no guardable que arranca en `false`. |
| Logout o expiración | No son una salida accidental: R27 y C7 piden invalidar el ámbito. |
| Segundo plano | No sale de la pantalla y `LaunchedEffect` sigue vivo, así que el reintento continúa. |

**Tras un envío correcto, la salida vuelve a la normalidad.** Con `Sent`, `locked` pasa a `false`, `onBackLock(false)` vuelve a mostrar "Volver" y el atrás queda habilitado. "Volver al inicio" ejecuta `onCreatedHandled()` y `popTo(Home)`. Al salir de la composición, `onDispose { onBackLock(false) }` evita que el bloqueo quede pegado. Con `Failed` (un 4xx definitivo) también se puede salir, y el error está visible.

**El reintento sigue ligado a la pantalla visible:** `LaunchedEffect(s.interview) { sendUrgent }` no cambia, y las pruebas `PreRegistrationInterviewTest::R13` ×3 siguen en verde.

**Pruebas nuevas:**
- `InterviewUiTest::R13 en la alarma atras y Volver quedan bloqueados…` es una prueba de lógica sobre todos los estados.
- `InterviewUiTest::R12 la raiz intercepta el gesto atras y oculta Volver…` es una prueba de fuente.

Las dos pasan.

**Regresiones:** no hay. El bloqueo solo se activa con `InterviewUiState.RedFlag`, y las rutas sin entrevista no lo componen. No hay `import` entre features: `redFlagBlocksBack` vive en `feature/interview/ui` y lo usa `AppNavigation`, que es el punto de unión.

## Checkpoints

### C1 — Harness
- [x] Están `AGENTS.md`, `CLAUDE.md`, `init.sh` (ejecutable), `feature_list.json`, `progress/current/` y `progress/history.md`. Evidencia: sección 2 de `init.sh`, `[OK]`.
- [x] Están los documentos `docs/{architecture,conventions,specs,verification}.md`. Evidencia: `init.sh`.
- [x] `./init.sh` termina con exit 0 (VERDE).

### C2 — Estado
- [x] Hay una sola feature en `in_progress`, F-11-mobile (`feature_list.json:223`; la coincidencia de la línea 11 es el enum de estados).
- [x] Las features en `done` siguen en verde: 486/486.
- [x] `progress/current/` contiene solo `F-11-mobile.md`.

### C3 — Arquitectura
- [x] Paquetes: `core/interview` y `feature/interview/{data,model,ui}`. C3 y `PatientOnlyDocsTest` ya incluyen `interview` (`61e63c1`). `PatientOnlyScopeTest` añade solo `POST next-question` (API del paciente).
- [x] MVVM:
  - `InterviewViewModel` y `AssistantSettingsViewModel` exponen `StateFlow`;
  - Retrofit solo aparece en `ServerInterviewer` y en el repositorio.
- [x] No hay `import` de una `feature/*` a otra: grep vacío en `feature/interview` y `feature/encounters`. La unión se hace en `AppNavigation`. `UrgentSubmission` vive en `core/interview` y `SymptomInterviewDto` en `core`.
- [x] Las dependencias nuevas están justificadas en el design (D1, D3): solo `litertlm-android:0.19.1` y `work-runtime-ktx:2.12.0` (`libs.versions.toml`, `app/build.gradle.kts`).
- [x] No hay `Log`, `println` ni TODOs en los `.kt` del diff (grep vacío; `PrivacyTest::R33` y `InterviewPrivacyTest::R16` en verde).

### C4 — Verificación
- [x] Pruebas JVM por VM y por adaptador. El HTTP se prueba contra `MockWebServer`:
  - `ServerInterviewerTest`;
  - `ModelDownloadTest`, con `Range`/206/200/416;
  - `PreRegistrationInterviewTest`.
- [x] `testDebugUnitTest`: 486, todas en verde (484 en la 1.ª revisión). Pruebas de F-11:

  | Prueba | Casos |
  |---|---|
  | `InterviewSessionTest` | 17 |
  | `InterviewViewModelTest` | 12 |
  | `OutputValidatorTest` | 11 |
  | `ModelDownloadTest` | 9 |
  | `PreRegistrationInterviewTest` | 8 |
  | `RedFlagRulesTest` | 7 |
  | `InterviewUiTest` | 8 |
  | `ServerInterviewerTest` | 4 |
  | `DeviceCapabilityTest` | 4 |
  | `InterviewPrivacyTest` | 4 |
  | `LiteRtInterviewerTest` | 4 |
  | `EvalCasesTest` | 2 |

- [x] `./gradlew lint`: sin errores (forma parte de `init.sh`).

### C5 — Cierre
- [x] No hay archivos sin trackear: `git status` limpio.
- [x] `progress/history.md`: el leader añade la entrada de F-11-mobile al cerrar (convención del flujo; no bloquea esta revisión).
- [x] El estado `in_progress` es correcto mientras la revisión siga abierta.

### C6 — SDD
- [x] `specs/F-11-mobile/{requirements,design,tasks}.md` existen. Incluyen las enmiendas de R9/R12/glosario (2026-11-18) y de R17 (2026-11-19). `interview-v2` está en design y tasks.
- [x] Los requisitos están en EARS y cada `R<n>` está trazado a `F-11.ACn`.
- [x] Tareas: T1–T11 `[x]`. T12 `[ ]` se deja a propósito para el humano: los artefactos están hechos y falta la corrida con el modelo real (ver "Pendiente del humano").
- [x] Pruebas `R<n> ` con espacio en `core/interview`, `feature/interview` y `PreRegistrationInterviewTest`:

  | R | Pruebas | R | Pruebas | R | Pruebas | R | Pruebas |
  |---|---|---|---|---|---|---|---|
  | R1 | 1 | R6 | 13 | R11 | 9 | R16 | 2 |
  | R2 | 1 | R7 | 5 | R12 | 11 | R17 | 3 |
  | R3 | 2 | R8 | 3 | R13 | 5 | R18 | 1 |
  | R4 | 2 | R9 | 11 | R14 | 8 | R19 | 2 (JVM) + instrumentada manual |
  | R5 | 4 | R10 | 5 | R15 | 2 | | |

  No hay nombres con `R<n>:` (grep vacío). Todas pasan.

### C7 — Seguridad clínica y privacidad
- [x] El audio no sale del dispositivo. El dictado de la respuesta reutiliza el `OnDeviceRecognizer` de F-08 (`OnDeviceOnlyTest` ampliado).
- [x] La entrevista funciona solo con texto si no hay dictado (`DictationUiState.Unavailable`).
- [x] `complaint_source` no cambia: el formulario de F-08 sigue mandando.
- [x] No hay datos personales en Logcat:
  - no hay `Log` en `main`;
  - los `toString()` de `Turn`, `SymptomInterviewDto`, `NextQuestionRequest`, `NextStep.*`, `InterviewResult`, `InterviewUiState.*` y `CreatePreRegistrationRequest` enmascaran el contenido (`InterviewPrivacyTest::R16`);
  - no hay `HttpLoggingInterceptor`.
- [x] `network_security_config` no cambia.
- [x] La app no envía horas.
- [x] La app sigue siendo solo del paciente. `next-question` usa el Bearer del paciente (`patientCall`).
- [x] El token y `/me` no cambian.
- [x] Las respuestas de `ServerInterviewer` pasan por `patientCall`, que captura el Bearer por operación.
- [x] Persistencia:
  - turnos, motivo y respuestas solo en la RAM del `InterviewViewModel` (R17 enmendado; sin `SavedStateHandle` ni `rememberSaveable`);
  - el DataStore `asclepio_interview` guarda solo el booleano `server_notice_seen`;
  - `allowBackup="false"`.
- [N/A] El consentimiento PA-PAC-5 no es de esta feature.

### C8 — Contrato
- [x] `consumes_contract: 0.11.0` coincide con `openapi/asclepio-api.v1.json` `info.version = 0.11.0`. `BuildConfig.CONSUMED_CONTRACT = 0.11.0`.
- [x] Los DTO corresponden a `NextQuestionRequestDto`, `NextQuestionDto`, `InterviewTurnDto` (`answer` obligatorio y anulable; `explicitNulls` por defecto lo serializa como `null`) y `SymptomInterviewDto`. `interview` es opcional en `CreatePreRegistrationRequest` (`encodeDefaults = false`). `turns.take(5)` respeta `maxItems: 5`.
- [x] 429/503/corte de `next-question` terminan la entrevista con lo respondido (R7). Los errores del envío urgente usan el catálogo (`preRegistrationErrorMessage`).

## Verificación del foco pedido

### 1. Fidelidad del port de seguridad — OK
- `RedFlagRules.PATTERNS`: los 37 patrones son iguales, carácter a carácter, a `RED_FLAG_PATTERNS` de `interview.rules.ts`, en el mismo orden por señal. Lo comprobé con un script que compara las cadenas JS desescapadas con las raw strings de Kotlin. `PAIN` es igual.
- Fixture: `cmp` idéntico. SHA-256 `2d75bdbd…e6b7` en ambos lados, y `RedFlagRulesTest::R11` fija el hash y pasa el 100 %.
- Prompt: `diff` muestra que `assets/prompts/interview-v2.md` es idéntico a `ai/prompts/interview-v2.md`.
- `OutputValidator`: los 15 `FORBIDDEN_PATTERNS`, las 8 `FIXED_QUESTIONS`, las claves exactas, los tipos estrictos (un booleano en cadena se rechaza), 1–140 caracteres, un solo `?` al final, la dimensión repetida y `textFail` conservan `redFlagSuspected`, como `validateQuestion`.
- `resolve`, `missingMandatory` y `answeredDimensions` son el port de `resolveOutput`:
  - la sospecha escala aunque el texto falle, salvo con 0 respuestas no nulas;
  - `done` exige ≥3 respuestas, ONSET y, si hay dolor, LOCATION; si falta una, se hace su pregunta fija.
- `InterviewSession.advance()` aplica `RedFlagRules.detect(complaint, turns)` antes de cada `generate()`. `answer()` añade el turno y vuelve a `advance()` (R11). Además:
  - salida local inválida: un reintento y luego la pregunta fija;
  - timeout de 20 s: pregunta fija;
  - la pregunta del servidor se revalida;
  - un `DONE` del servidor pasa por `gateDone`.

### 2. R10 — OK
Solo `InterviewState.Asking` lleva texto, y viene de `Resolved.Ask` (validado) o de `FIXED_QUESTIONS`. `NextStep.ModelOutput` nunca llega a la UI. El resumen muestra las preguntas de los turnos, que ya estaban validadas.

### 3. Camino de alarma — OK (corregido en `04d0596`)
- Funciona:
  - `end(RED_FLAG)` → `InterviewUiState.RedFlag`;
  - la raíz lanza `sendUrgent` de inmediato con `endedBy = RED_FLAG` y las señales (`PreRegistrationInterviewTest::R12`);
  - backoff de 1→30 s o `Retry-After`;
  - se cancela al salir (R13 ×3);
  - un resumen editado con una señal escala.
- En la 1.ª revisión, la pantalla se podía abandonar por accidente con el gesto atrás o con "Volver", y se perdía el envío. `04d0596` lo bloquea; ver "Verificación de la corrección".

### 4. Privacidad — OK
- `LiteRtInterviewer` y `core/interview` no importan nada de red (`InterviewPrivacyTest::R15`). El modelo se lee de `filesDir/models/`.
- No hay texto clínico en logs, `SavedStateHandle` ni DataStore.
- El aviso del servidor (texto literal de PA-F11-3) se muestra antes de la primera llamada a `next-question`, cuando el equipo no es capaz y el aviso no se ha visto (`selectEngine`). "Omitir asistente" llama a `skip()`, que pasa a `Skipped` y luego a `skipAssistant()`, y envía sin `interview`.

### 5. Descarga — OK
- `UNMETERED` (`ModelDownloadTest::R1`).
- `setForeground` con `FOREGROUND_SERVICE_TYPE_DATA_SYNC` y `SystemForegroundService` con `foregroundServiceType="dataSync"`.
- Reanudación con `Range: bytes=n-`: 206 añade, 200 reinicia y 416 borra.
- El SHA-256 se calcula mientras se escribe, después de volver a leer el `.part`. El rename es atómico con `Files.move(ATOMIC_MOVE)`. Un hash incorrecto borra el `.part`.
- Espacio: se exigen libres 2 × `MODEL_BYTES` menos lo ya escrito.
- Cancelar llama a `cancelUniqueWork` y borra el `.part`; borrar llama a `deleteAll`.
- El modelo no está en el APK: `assets/` solo tiene licencias, `prompts/` y `ui-resource-sha256.txt`, no hay `.litertlm` en el repo y `docs/eval` es un asset solo de `androidTest`.
- Metadatos de la API de HF (sin descargar el archivo):
  - repositorio `litert-community/gemma-4-E2B-it-litert-lm`, revisión `2d74ac41a33d7da82f39f7d5720a04c68514a103`, no gated;
  - `gemma-4-E2B-it.litertlm`, size `2588147712`, LFS sha256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
  
  Coinciden exactamente con `MODEL_BYTES`, `MODEL_SHA256` y `MODEL_URL`.

### 6. Capacidad — OK
- `MIN_TOTAL_MEM_BYTES = 5_905_580_032`, que son 5,5 GiB. Se exige `modelVerified()`.
- `initialize` usa `withTimeoutOrNull(20 s)`; si falla o vence, `failedThisSession` queda en true.
- `DeviceCapability` es de ámbito de la app (`AppContainer`), y si el equipo no es capaz se usa el servidor con el aviso.

### 7. Ciclo de vida — OK
- `LifecycleEventEffect(ON_STOP)` → `onStop()` cancela el job si la sesión está generando. Al cancelarse, `invokeOnCancellation` llama a `conversation.cancelProcess()`.
- `ON_START` regenera la pregunta.
- `release()` → `close()` del `Engine` al terminar, al omitir y en `onCleared` (`InterviewViewModelTest::R17` ×2).

### 8. Dependencias y nombres — OK
- Solo se añaden las dos dependencias pedidas.
- Permisos nuevos: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` y `POST_NOTIFICATIONS`. Este último se pide solo al pulsar "Descargar" en Android 13+. Son los mínimos.
- Los nombres usan `R<n> ` con espacio.

## Juicio de las desviaciones del implementer
1. **R17 en memoria.** Resuelta: el humano enmendó la spec el 2026-11-19. Se acepta.
2. **R7 `endedBy = PATIENT` con turnos, sin `interview` con 0 turnos.** Se acepta: el contrato no tiene un valor para "motor caído" y el brief AC5 dice "como hoy".
3. **Prompt v2.** Resuelta en la spec y en el design.
4. **DataStore propio para el aviso.** Se acepta: guarda un booleano no privado.
5. **R13: un 4xx distinto de 408/429 no se reintenta.** Se acepta: un 409 no se arregla reintentando y la alarma sigue visible con el error.
6. **El resumen editado se revalida con las reglas.** Se acepta: es más seguro.
7. **`POST_NOTIFICATIONS` bajo demanda.** Se acepta.
8. **Pruebas estáticas ampliadas.** Se acepta: las ampliaciones son mínimas y no debilitan nada.
9. **C3.** Cerrada en `61e63c1`.
10. **Riesgos de `onMessage` y GPU→CPU.** Se aceptan como riesgo para el dispositivo. Si los fragmentos llegan acumulados, el JSON no valida y se usa la pregunta fija: falla de forma segura.

## Observaciones no bloqueantes (recomendadas)
- **Rotación en la alarma.** La Activity se recrea y `LaunchedEffect(s.interview)` vuelve a lanzar `sendUrgent`. Si una petición en vuelo llegó al servidor antes de cancelarse, el reenvío recibe 409 y muestra `Failed` aunque el prerregistro sí se creó. Conviene lanzar el envío en `viewModelScope`, ligado al estado de la alarma, o tratar un 409 tras `Sending` como enviado.
- **Equipo no capaz con el aviso sin ver.** Las reglas sobre el motivo solo se aplican después de aceptar el aviso. Si el paciente pulsa "Omitir asistente" con un motivo como "dolor en el pecho", nunca ve la alarma. Conviene ejecutar `RedFlagRules.detect(complaint)` al pulsar "Continuar", antes de elegir el motor.
- **Regex en el dispositivo.** El fixture se prueba en la JVM (`java.util.regex`), pero Android usa ICU, donde `\b` y `\w` reconocen Unicode. Con el texto normalizado el riesgo es bajo. Aun así, una prueba instrumentada barata que pase `red-flag-cases.json` en el dispositivo cerraría la duda.
- **Descarga con cancelación y borrado.** `cancel()` borra el `.part` mientras el worker puede seguir escribiendo. En Linux es inocuo, porque el inode queda desvinculado, pero conviene esperar a que el trabajo termine.

## Pendiente del humano (no bloquea esta revisión)
- **T12 / R19:** ejecutar `EvalInstrumentedTest` y `LiteRtSmokeInstrumentedTest` con el modelo real en el equipo de gama media, calificar con la rúbrica y dejar el informe en `progress/`.
- **QA en un dispositivo con ≥6 GB:**
  - descarga por Wi-Fi, reanudación, cancelar y borrar;
  - entrevista local en modo avión;
  - aviso del servidor una sola vez;
  - alarma con envío inmediato y reintento, incluido comprobar que el gesto atrás queda bloqueado durante `Sending`/`Retrying`;
  - rotación y segundo plano durante una generación;
  - GPU→CPU.
