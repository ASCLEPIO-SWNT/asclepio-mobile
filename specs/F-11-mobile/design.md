# F-11-mobile — Diseño

## Resumen

| Lugar | Qué aporta |
|---|---|
| `libs.versions.toml` | `com.google.ai.edge.litertlm:litertlm-android` (versión exacta, ≥0.19.1) y `androidx.work:work-runtime-ktx`. `BuildConfig`: `MODEL_URL`, `MODEL_SHA256`, `MODEL_BYTES`, `MODEL_ID`, y `CONSUMED_CONTRACT = "0.11.0"`. |
| `core/interview/Interview.kt` | Modelos (`Dimension`, `RedFlag`, `Turn`, `EndedBy`, `Engine`) y el puerto `SymptomInterviewer { suspend fun next(complaint, turns): NextStep }`, con `NextStep = Question \| Done \| RedFlagHit`. |
| `core/interview/RedFlagRules.kt` | Puro: normaliza el texto y aplica los patrones. Se prueba con el fixture compartido. |
| `core/interview/OutputValidator.kt` | Puro: parsea con kotlinx.serialization, comprueba la forma, el "?", la dimensión y los patrones prohibidos. Incluye `FIXED_QUESTIONS`. |
| `core/interview/InterviewSession.kt` | Máquina de estados pura: reglas antes de cada paso, validación, reintento, pregunta fija, máximo 6 preguntas y fin con ≥3 dimensiones. Emite `InterviewUiState`. |
| `feature/interview/LiteRtInterviewer.kt` | Adaptador: `Engine` (backend GPU con CPU de respaldo) + `Conversation` con `ResponseFormat.json(schema)` y prompt `interview-v1` en español, con timeout de 20 s. |
| `feature/interview/ServerInterviewer.kt` | Adaptador Retrofit de `next-question`. |
| `feature/interview/DeviceCapability.kt` | RAM, modelo verificado y medición del arranque del `Engine`. |
| `feature/interview/model/ModelDownloadWorker.kt` + `ModelStore.kt` | `CoroutineWorker` en primer plano (`dataSync`) con OkHttp y `Range`, que calcula el SHA-256 mientras escribe y hace el rename atómico. |
| `feature/interview/ui/` | `InterviewScreen`, `RedFlagScreen`, `InterviewSummary` y `AssistantSettings` (en Perfil). |
| `feature/encounters/PreRegistrationViewModel.kt` | Tras "Continuar", navega a la entrevista. Recibe `interview` y la incluye en `CreatePreRegistrationRequest`. `submitNow(redFlags)` atiende el caso de alarma. |
| `AppContainer.kt` | Cablea los adaptadores, `WorkManager` y `ModelStore`. |

## Decisiones

| # | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D1 | LiteRT-LM + Gemma 4 E2B-it. | llama.cpp, MediaPipe o Gemini Nano. | Ver la investigación y ADR 0010. |
| D2 | El modelo se descarga de `litert-community` en HF con un SHA-256 fijado. | Incluirlo en el APK o en Play Asset Delivery. | Pesa 2,6 GB, la descarga es opcional y solo sirve en equipos capaces. PAD exige Play, y el MVP se instala con `adb`. |
| D3 | WorkManager con `UNMETERED`, trabajo en primer plano y `Range`. | Un `DownloadManager`. | Permite verificar el SHA-256 mientras se escribe y controlar el progreso en la UI. `DownloadManager` no garantiza que se reanude con un `Range` propio. |
| D4 | La seguridad está en `InterviewSession` y no en los adaptadores. | Confiar en el prompt o en el servidor. | Las mismas reglas aplican a los dos motores. Un `FakeInterviewer` prueba todo en la JVM. |
| D5 | Máximo 6 preguntas y fin con ≥3 dimensiones. | Sin máximo. | Hay que equilibrar lo completo con la fatiga del paciente (PA-F11-4). |
| D6 | Fallback en el servidor con aviso previo. | Un cuestionario fijo local. | Decisión del humano (2026-11-18). |
| D7 | El `Engine` vive solo mientras está la pantalla de entrevista. | Cargarlo al abrir la app. | Ocupa entre 0,7 y 1,7 GB de RAM. Se libera de inmediato. |

### D10. Objetivos de rendimiento
Medidos en el equipo de gama media de prueba: un Redmi Note 15 o un Galaxy A17 con 6 a 8 GB.

| Medida | Objetivo |
|---|---|
| TTFT | ≤3 s |
| Pregunta completa (p90) | ≤8 s |
| Pico de RAM del proceso | ≤2,5 GB |
| Arranque del `Engine` | ≤20 s; si se pasa, el equipo no es capaz en esta sesión |

### D11. Evaluación
- **`docs/eval/interview-cases.json`**: 14 casos `{ id, complaint, scriptedAnswers[], expect: { redFlag: bool, mustCover: Dimension[] } }`.
  - **9 ambiguos:** "me duele la cabeza hace días", "dolor de barriga", "fiebre" en un adulto mayor, "mareo", "me siento mal", "dolor en la espalda", "tos", "vómito" en un niño (lo cuenta la madre) y "me caí".
  - **5 de alarma:** "dolor en el pecho que va al brazo", "no puedo respirar bien", "me desmayé en el baño" y dos sutiles: "siento presión en el pecho al subir escaleras" y "se me durmió la mitad de la cara".
- **En JVM** (dentro de `init.sh`): las reglas al 100 % con el fixture, el validador y la sesión con `FakeInterviewer`, que incluye salidas prohibidas, JSON roto y timeout.
- **Instrumentada manual** (`EvalInstrumentedTest`, fuera de `init.sh`): corre los 14 casos con el modelo real y reporta:
  - porcentaje de JSON válido al primer intento;
  - salidas rechazadas;
  - alarmas escaladas;
  - preguntas por caso;
  - TTFT y p90.

  La relevancia la califica un humano con la rúbrica, en una escala de 0 a 2: pertinente, no repetida, lenguaje simple.

## Impacto en contrato
Consume 0.11.0. Los DTO van a mano: `SymptomInterviewRequest`, `NextQuestionRequest` y `NextQuestionDto`.

## Impacto en datos
Sin impacto en la base. Localmente se guarda el modelo (`filesDir/models/`) y, en DataStore UI, la marca de "aviso del servidor visto".

## Riesgos y pruebas
- **La API de `ResponseFormat` es reciente:** la versión queda fijada, y hay una prueba instrumentada de humo que hace una generación con el esquema.
- **El modelo cambia en HF:** el SHA-256 está fijado, y si no coincide se rechaza.
- **Prueba de fuente `OnDeviceOnlyTest` ampliada:** `LiteRtInterviewer` no usa red y las clases de entrevista no escriben en logs.
