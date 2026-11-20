# F-11-mobile — Implementación

- **Rama:** `feature/F-11-mobile` (commits `7597f3f` … ver `git log db401c9..HEAD`). Sin push.
- **Contrato consumido:** 0.11.0 (`BuildConfig.CONSUMED_CONTRACT`). Sin cambios en `../asclepio-api/`.
- **Tareas:** T1–T11 `[x]`. T12 `[ ]`: `docs/eval/interview-cases.json`, `EvalInstrumentedTest` y `EvalCasesTest` hechos; la corrida con el modelo real queda para el humano (nota en `tasks.md`).
- **Decisiones y desviaciones:** en `progress/current/F-11-mobile.md`. R17: el humano decidió conservar los turnos solo en la memoria del ViewModel (spec enmendada en `317edfa`, con `interview-v2` en design/tasks). Desviación 9 cerrada: C3 de `CHECKPOINTS.md` y `PatientOnlyDocsTest` listan `feature/{account,patients,encounters,status,voice,interview}`.

## Dependencias y build
- `com.google.ai.edge.litertlm:litertlm-android:0.19.1` (Google Maven; API verificada en el AAR: `EngineConfig`, `Backend.GPU/CPU`, `ConversationConfig(enableResponseFormat)`, `Conversation.sendMessageAsync(..., responseFormat = ResponseFormat.json(schema))`, `cancelProcess()`).
- `androidx.work:work-runtime-ktx:2.12.0`.
- `BuildConfig`: `MODEL_URL` (HF `litert-community/gemma-4-E2B-it-litert-lm`, revisión `2d74ac4…`, `gemma-4-E2B-it.litertlm`), `MODEL_BYTES = 2588147712`, `MODEL_SHA256 = 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, `MODEL_ID = gemma-4-E2B-it.litertlm@2d74ac4`, `CONSUMED_CONTRACT = 0.11.0`. El modelo no va en el APK.
- Manifiesto: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` y `SystemForegroundService` de WorkManager con `foregroundServiceType="dataSync"`.
- `androidTest` lee `docs/eval/` como assets (solo el APK de prueba).

## Archivos tocados
**Nuevos (main)**
- `core/interview/`: `Interview.kt`, `RedFlagRules.kt`, `OutputValidator.kt`, `InterviewSession.kt`, `UrgentSubmission.kt`.
- `feature/interview/`: `DeviceCapability.kt` (+ `EngineChoice`, `selectEngine`, `PreparableInterviewer`), `LiteRtInterviewer.kt` (+ `DebugGenerationMetrics`).
- `feature/interview/data/`: `SymptomInterviewApi.kt`, `ServerInterviewer.kt`, `ServerNoticeStore.kt`.
- `feature/interview/model/`: `ModelStore.kt`, `ModelDownloader.kt`, `ModelDownloadWorker.kt`, `AssistantModelRepository.kt`.
- `feature/interview/ui/`: `InterviewViewModel.kt`, `InterviewScreen.kt` (pregunta, aviso, `InterviewSummary`, `RedFlagScreen`), `AssistantSettings.kt`.
- `assets/prompts/interview-v2.md` (copia literal), `res/values{,-en,-pt,-fr,-de}/interview.xml` (41 textos).

**Modificados (main)**
- `AppContainer.kt`, `AppNavigation.kt` (secciones `InterviewSection` y `AssistantSection`), `AndroidManifest.xml`.
- `feature/encounters/data/PreRegistrationsApi.kt` (`interview` opcional), `feature/encounters/ui/PreRegistrationViewModel.kt` (`continueToAssistant`, `submitWithInterview`, `skipAssistant`, `sendUrgent`), `PreRegistrationScreen.kt` (slot `assistant`, etiqueta "Continuar").
- `feature/patients/ui/ProfileScreen.kt` (slot `assistant`).
- `gradle/libs.versions.toml`, `app/build.gradle.kts`.

**Pruebas**
- Nuevas: `core/interview/{RedFlagRulesTest,OutputValidatorTest,InterviewSessionTest,FakeInterviewer}.kt`, `feature/interview/{DeviceCapabilityTest,LiteRtInterviewerTest,InterviewPrivacyTest,EvalCasesTest}.kt`, `feature/interview/data/ServerInterviewerTest.kt`, `feature/interview/model/ModelDownloadTest.kt`, `feature/interview/ui/{InterviewViewModelTest,InterviewUiTest}.kt`, `feature/encounters/ui/PreRegistrationInterviewTest.kt`, `test/resources/red-flag-cases.json` (SHA-256 `2d75bdbd…e6b7`).
- Instrumentadas (compilan; no ejecutadas): `feature/interview/LiteRtSmokeInstrumentedTest.kt`, `feature/interview/EvalInstrumentedTest.kt`.
- Ampliadas: `PatientOnlyDocsTest` (C3 con `interview`), `PatientOnlyScopeTest` (+next-question), `OnDeviceOnlyTest` R3, `QueuePositionLifecycleTest` R2, `PatientTestEnv` (`retrofit` público).
- Docs: `CHECKPOINTS.md` (C3), `docs/architecture.md`, `docs/verification.md`, `docs/eval/interview-cases.json`.

## Trazabilidad R<n> → prueba (`app/src/test` salvo indicación)
| R | Pruebas |
|---|---|
| R1 | `ModelDownloadTest::R1 el trabajo exige red no medida`; `InterviewViewModelTest::R6 Omitir asistente … y R1 el aviso ofrece descargar con RAM suficiente`; `QueuePositionLifecycleTest::R2 no hay trabajo en segundo plano para la fila` (servicio dataSync) |
| R2 | `ModelDownloadTest::R2 con menos de 2 veces el tamano libre no se inicia y se informa cuanto falta` |
| R3 | `ModelDownloadTest::R3 una descarga cortada conserva el part y se reanuda con Range…`, `::R3 si el servidor ignora Range…` |
| R4 | `ModelDownloadTest::R4 una descarga completa con el SHA-256 correcto…`, `::R4 un SHA-256 incorrecto borra el part…` |
| R5 | `ModelDownloadTest::R5 cancelar…`, `::R5 el APK no incluye el modelo…`, `::R5 el estado de la UI…`; `InterviewUiTest::R5 el estado de la descarga muestra porcentaje y MB…` |
| R6 | `DeviceCapabilityTest` (4); `InterviewViewModelTest::R6 …` (4); `PreRegistrationInterviewTest::R6 …` (2); `ServerInterviewerTest::R6 …` (2); `InterviewUiTest::R6 el aviso del servidor es el texto de PA-F11-3…` |
| R7 | `ServerInterviewerTest::R7 429, 503, 500, un corte…`, `::R7 con 503 a mitad…`; `InterviewSessionTest::R7 …`; `InterviewViewModelTest::R7 …`; `PreRegistrationInterviewTest::R7 omitir el asistente envia como en 0_10_0…` |
| R8 | `InterviewSessionTest::R8 No se guarda…`; `InterviewViewModelTest::R8 Siguiente exige texto…`; `InterviewUiTest::R8 …` |
| R9 | `OutputValidatorTest::R9 …` (5); `InterviewSessionTest::R9 …` (3); `LiteRtInterviewerTest::R9 …` (3); `androidTest LiteRtSmokeInstrumentedTest::R9 …` (manual) |
| R10 | `OutputValidatorTest::R10 …` (3); `InterviewSessionTest::R10 …` (2); `InterviewViewModelTest::R8 …` (pregunta prohibida del servidor → fija) |
| R11 | `RedFlagRulesTest::R11 …` (7, fixture al 100 % y SHA-256); `InterviewSessionTest::R11 …` (2) |
| R12 | `InterviewSessionTest::R12 …` (3); `OutputValidatorTest::R12 …` (2); `InterviewViewModelTest::R12 …` (3); `PreRegistrationInterviewTest::R12 la alarma envia de inmediato…`; `InterviewUiTest::R12 la pantalla de alarma…` |
| R13 | `PreRegistrationInterviewTest::R13 …` (3); `InterviewUiTest::R13 la raiz lanza el envio de alarma ligado a la pantalla y R17…` |
| R14 | `InterviewSessionTest::R14 …` (4); `OutputValidatorTest::R14 …`; `InterviewViewModelTest::R14 …`; `PreRegistrationInterviewTest::R14 confirmar el resumen envia … interview segun el contrato`; `InterviewUiTest::R14 …` |
| R15 | `InterviewPrivacyTest::R15 …` (2); `InterviewViewModelTest::R6 con equipo capaz usa el motor local y no llama al servidor` |
| R16 | `InterviewPrivacyTest::R16 …` (2); `PrivacyTest::R33` (sin `Log` en todo `src/main`) |
| R17 | `InterviewSessionTest::R17 …`; `InterviewViewModelTest::R17 ON_STOP cancela…`, `::R17 al salir de la entrevista se libera el Engine`; `InterviewUiTest::R13 … R17 …` |
| R18 | `LiteRtInterviewerTest::R18 las metricas de generacion solo guardan numeros y solo en depuracion` |
| R19 | `EvalCasesTest::R19 …` (2); `androidTest EvalInstrumentedTest::R19 evaluacion manual de los 14 casos con el modelo real` (manual, pendiente) |

## Resultados
- `./gradlew testDebugUnitTest`: **484 pruebas, 0 fallos**; además se ampliaron 3 pruebas existentes.
- `./gradlew compileDebugAndroidTestKotlin`: OK (instrumentadas solo compiladas).
- `./init.sh` (completo, tras cerrar la desviación 9): `[OK] gradlew lint testDebugUnitTest` — `FAIL: 0 · WARN: 0 · Estado: VERDE`, 484 pruebas.
- Lint: solo avisos previos (versiones nuevas, recursos sin usar); un error `ByteOrderMark` por caracteres Unicode literales en una regex se corrigió con escapes.

## Pendiente del humano
- QA en un teléfono con ≥6 GB: descarga por Wi-Fi, reanudación, cancelar/borrar; entrevista local en modo avión; aviso del servidor una vez; alarma con envío inmediato; rotación y segundo plano durante una generación.
- `LiteRtSmokeInstrumentedTest` y `EvalInstrumentedTest` con el modelo real; informe y rúbrica en `progress/` (T12).
