# F-08-mobile — Dictado del motivo en el dispositivo

- **Rama:** `feature/F-08-mobile` · **Contrato:** 0.9.0 (sin cambios que afecten a F-08).
- **Aprobación:** spec aprobada por el humano en el chat el 2026-11-17 ("aprobado, PA-F08-1 y PA-F08-2 confirmadas"). PA-F08-1 y PA-F08-2 confirmadas tal como las propone `requirements.md`.
- **Línea base:** `./init.sh` VERDE (FAIL 0 · WARN 0) el 2026-11-17 antes de empezar.
- **Estado:** `in_progress`. Siguiente: implementer, T1→T14 en orden (T13 es QA manual del humano).

## Registro

- **T1** (2026-11-17) `CONSUMED_CONTRACT` → `0.9.0`; comentarios de versión en `PreRegistrationsApi.kt`/`PreRegistrationForm.kt` (contrato verificado: `CreatePreRegistrationDto` igual). Prueba `R9 …VOICE_ON_DEVICE` en `PreRegistrationsApiTest`: pasa sin cambio de código porque el enum `VOICE_ON_DEVICE` ya existía desde F-02 (prueba de caracterización, no hubo rojo funcional). `PreRegistrationsApiTest` 6/6.
