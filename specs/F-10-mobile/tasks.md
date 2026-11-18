# F-10-mobile — Tareas

> Primero la prueba `R<n> …` (con espacio, nunca `:`) y luego el código.

- [ ] T1 Dependencia `okhttp-sse` + `CONSUMED_CONTRACT = 0.10.0`; `./gradlew lint` en verde.
- [ ] T2 Prueba + `QueuePositionDto` y `QueuePositionStream` contra `MockWebServer`: evento `position`, latido ignorado, corte → `Failed`, 401 → `Failed(401)`. (R1, R9, R10)
- [ ] T3 Prueba + `QueuePositionViewModel`: backoff 1, 2, 4… hasta 30 s y reinicio tras un evento; `RECONNECTING` conservando `last`; 401 → `ended` y navegación a login; `DONE` → sin reconexión. (R5, R8, R9)
- [ ] T4 Prueba de UI (Compose JVM o `androidTest`) + `QueuePositionCard`: estados R3–R7, rango y textos de insuficiencia, catálogo de localización y `liveRegion`. (R3, R4, R5, R6, R7, R11)
- [ ] T5 Integración en `AppNavigation` (`Status`) con `repeatOnLifecycle(STARTED)` + prueba de fuente de que no hay recolección fuera de ella. (R1, R2)
- [ ] T6 QA manual con `adb reverse tcp:3000 tcp:3000`: prerregistro → confirmar en recepción → `triage.sh` de otro paciente más grave → la posición cambia sin refrescar. Con el modo avión → reconexión.
