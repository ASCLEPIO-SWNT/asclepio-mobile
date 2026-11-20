# F-11-mobile — en curso

- **Aprobación:** humano, 2026-11-18, «aprobado» en el chat. Las enmiendas son equivalentes a las de F-11-api (R13, R14 y R18), que el humano decidió el 2026-11-18 («Escalar igual» y «aplica las propuestas 1 y 2»).
- **Dependencia:** F-11-api `done` y fusionada (asclepio-api#6, contrato 0.11.0).
- **Rama:** `feature/F-11-mobile` desde `develop`.
- **Pendiente del humano al final:** QA en el dispositivo (≥6 GB de RAM) y `EvalInstrumentedTest` con el modelo real (T12).

## Implementación (implementer, 2026-11-19)
- T1–T11 `[x]`; T12 `[ ]` (artefactos hechos, la corrida con el modelo real es del humano). Detalle y trazabilidad en `progress/impl_F-11-mobile.md`. `./init.sh` completo en VERDE.
- **LiteRT-LM** `com.google.ai.edge.litertlm:litertlm-android:0.19.1` (última en Google Maven, 2026-11-05; `ConversationConfig(enableResponseFormat = true)` + `ResponseFormat.json(schema)` verificados en el AAR). WorkManager 2.12.0.
- **Modelo** (HF API, repo no gated): `gemma-4-E2B-it.litertlm`, revisión `2d74ac41a33d7da82f39f7d5720a04c68514a103`, 2 588 147 712 bytes, LFS SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` (confirmado con `X-Linked-Size`/`X-Linked-ETag` del HEAD; no se descargó).

## Decisiones y desviaciones para el humano/reviewer
1. **R17 y `SavedStateHandle` (contradicción con C7/PrivacyTest R32):** el spec pide conservar los turnos en `SavedStateHandle`, pero la regla vigente del repo (C7, `PrivacyTest` R32, `PatientDataPrivacyTest`) prohíbe serializar borradores privados en el estado de navegación. Se conservan en el `InterviewViewModel` (RAM: sobreviven a rotación y a `ON_STOP`, no a la muerte del proceso). No se debilitó la prueba de privacidad. **Requiere decisión del humano** si se quiere sobrevivir a la muerte del proceso.
2. **R7 `endedBy`:** el contrato no tiene un valor para "motor caído". Con turnos respondidos se envía `interview` con `endedBy = PATIENT` (el paciente confirma en el resumen); con 0 turnos se envía sin `interview`, como en 0.10.0 (descripción del 503 en el contrato).
3. **Prompt:** se usa `interview-v2` (enmienda de R9), copia literal en `app/src/main/assets/prompts/interview-v2.md` con SHA-256 fijado; el `design.md` y T8 aún dicen `interview-v1`.
4. **Aviso del servidor visto:** DataStore propio `asclepio_interview` (el de preferencias visuales hace `clear()` en cada cambio y lo borraría).
5. **R13:** el reintento vive mientras la pantalla de alarma está compuesta; un 4xx distinto de 408/429 (p. ej. 409) no se reintenta y se muestra el error. Un 429 respeta `Retry-After`.
6. **Resumen editable (R14):** al confirmar se vuelven a aplicar las reglas a las respuestas editadas; si hay señal se pasa a la pantalla de alarma (R11/R12).
7. **Notificación de la descarga:** se declara `POST_NOTIFICATIONS` y se pide solo al pulsar "Descargar" en Mis datos (Android 13+); sin permiso la descarga sigue y el progreso se ve en la app.
8. **Pruebas estáticas ampliadas por F-11:** `PatientOnlyScopeTest` (+`POST next-question`), `OnDeviceOnlyTest` R3 (AssistantSettings solo pide `POST_NOTIFICATIONS`), `QueuePositionLifecycleTest` R2 (permite el `SystemForegroundService` de WorkManager con `dataSync`, la fila sigue sin servicio).
9. **CHECKPOINTS C3** sigue listando `feature/{account,patients,encounters,status,voice}` (lo fija `PatientOnlyDocsTest`); falta añadir `interview` en C3 y en esa prueba (harness del leader). `docs/architecture.md` y `docs/verification.md` ya lo describen.
10. **Riesgos a verificar en el equipo:** que `onMessage` de LiteRT-LM entregue fragmentos (no texto acumulado) y que GPU→CPU funcione; lo cubren `LiteRtSmokeInstrumentedTest` y `EvalInstrumentedTest` (no ejecutados).
