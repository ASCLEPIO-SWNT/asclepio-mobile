# F-08-mobile — Dictado del motivo en el dispositivo

- **Rama:** `feature/F-08-mobile` · **Contrato:** 0.9.0 (sin cambios que afecten a F-08).
- **Aprobación:** spec aprobada por el humano en el chat el 2026-11-17 ("aprobado, PA-F08-1 y PA-F08-2 confirmadas"). PA-F08-1 y PA-F08-2 confirmadas tal como las propone `requirements.md`.
- **Línea base:** `./init.sh` VERDE (FAIL 0 · WARN 0) el 2026-11-17 antes de empezar.
- **Estado:** `in_progress`. Siguiente: implementer, T1→T14 en orden (T13 es QA manual del humano).

## Registro

- **T1** (2026-11-17) `CONSUMED_CONTRACT` → `0.9.0`; comentarios de versión en `PreRegistrationsApi.kt`/`PreRegistrationForm.kt` (contrato verificado: `CreatePreRegistrationDto` igual). Prueba `R9 …VOICE_ON_DEVICE` en `PreRegistrationsApiTest`: pasa sin cambio de código porque el enum `VOICE_ON_DEVICE` ya existía desde F-02 (prueba de caracterización, no hubo rojo funcional). `PreRegistrationsApiTest` 6/6.
- **T2** (2026-11-17) `core/voice/OnDeviceRecognizer.kt` (puerto, `OnDeviceAvailability`, `DictationError`, `DictationUiState` sin texto, `NoDictation`) y `FakeOnDeviceRecognizer` en `src/test`. Rojo: tipos inexistentes (compilación). `DictationTypesTest` 2/2. `DictationUiState.Idle` es `data object`: el `languageTag` lo guarda el ViewModel en un campo privado.
- **T3** (2026-11-17) `withDictation`/`withTypedText` en `PreRegistrationForm.kt`. Rojo: funciones inexistentes. "Termina en espacio" se interpreta como cualquier blanco final (`isWhitespace`, incluye salto de línea). `PreRegistrationFormTest` 12/12.
- **T4** (2026-11-17) `PreRegistrationViewModel(…, recognizer = NoDictation, beforeListen = {})` y `PreRegistrationUiState.dictation` (`Checking` → `Idle`/`Unavailable`). Rojo: constructor sin parámetros nuevos y campo inexistente (compilación). Las pruebas de F-02 no cambian de firma. `PreRegistrationViewModelTest` 21/21. El idioma elegido se comprueba al iniciar (prueba `R3 …` de T5).
