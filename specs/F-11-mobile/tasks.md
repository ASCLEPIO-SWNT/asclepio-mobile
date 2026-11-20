# F-11-mobile — Tareas

> Primero la prueba `R<n> …` (con espacio) y luego el código. Las tareas de JVM van antes que las nativas.

- [x] T1 Dependencias (`litertlm-android` con la versión exacta, `work-runtime-ktx`), `BuildConfig` del modelo y `CONSUMED_CONTRACT = 0.11.0`. `./gradlew lint` en verde.
- [x] T2 Prueba + `RedFlagRules`: el fixture compartido al 100 % y el hash del fixture igual al de la API. (R11)
- [x] T3 Prueba + `OutputValidator` y `FIXED_QUESTIONS`: JSON roto, campos extra, más de 140 caracteres, ningún "?" o dos, dimensión repetida y cada patrón prohibido. (R9, R10)
- [x] T4 (incluye las enmiendas del 2026-11-18: `done` con `ONSET` y `LOCATION` si hay dolor, `redFlagSuspected` aunque se rechace la pregunta, e ignorado en el turno 0) Prueba + `InterviewSession` con `FakeInterviewer`:
  - las reglas se aplican antes de `next()`;
  - reintento → pregunta fija;
  - timeout de 20 s;
  - `redFlagSuspected` → `RedFlagHit`;
  - máximo 6 preguntas, fin con ≥3 dimensiones, "No sé" y "Terminar".

  (R9, R10, R11, R12, R14)
- [x] T5 Prueba + `ServerInterviewer` con `MockWebServer`: las tres respuestas, y 429/503/corte → fin con lo respondido. (R7)
- [x] T6 Prueba + `ModelStore` y `ModelDownloadWorker` (`MockWebServer` con `Range`): reanudación, SHA-256 correcto e incorrecto, espacio insuficiente y cancelación. (R1, R2, R3, R4, R5)
- [x] T7 Prueba + `DeviceCapability` (RAM, modelo y timeout de arranque inyectables) y selección del motor con el aviso del servidor visto una sola vez. (R6)
- [x] T8 `LiteRtInterviewer` con `ResponseFormat.json` y el prompt `interview-v1`. Prueba instrumentada de humo con el modelo real. Métricas de depuración. (R9, R18)
- [x] T9 Prueba + UI: `InterviewScreen`, `RedFlagScreen` (envío inmediato y reintento con backoff), `InterviewSummary` y `AssistantSettings` en Perfil, con el catálogo de localización. (R5, R8, R12, R13, R14)
- [x] T10 Prueba + integración en `PreRegistrationViewModel` (`interview` en la petición, `submitNow`). Ciclo de vida: `SavedStateHandle`, cancelación en `ON_STOP` y liberación del `Engine`. (R12, R14, R17)
- [x] T11 Pruebas de fuente: no hay red en el motor local y no hay logs con contenido (amplía `OnDeviceOnlyTest`/`PrivacyTest`). (R15, R16)
- [ ] T12 `docs/eval/interview-cases.json` + `EvalInstrumentedTest`, y la corrida manual con un informe en `progress/`. (R19)
