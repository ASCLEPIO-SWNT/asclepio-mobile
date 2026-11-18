# F-10-mobile — en curso

- **Aprobación:** humano, 2026-11-18, «aprobado» en el chat (specs de F-10/F-11).
- **Dependencia:** F-10-api `done` (contrato 0.10.0; PR asclepio-api#5 pendiente de fusionar en `develop`).
- **Rama:** `feature/F-10-mobile` desde `develop`.
- **Implementación (2026-11-18):** T1–T5 hechas con pruebas `R<n> `; `./init.sh` en verde. Detalle, decisiones de interpretación y trazabilidad en `progress/impl_F-10-mobile.md`.
- **Pendiente:** T6 (QA manual en dispositivo, humano) y la revisión del `reviewer`.
- **Revisión:** RECHAZADA una vez (tras `DONE` el ViewModel no reabría el stream con un prerregistro nuevo); corregida con la clave por id del prerregistro y APROBADA (2026-11-18, 396 pruebas, `init.sh` VERDE).
- **Pendiente para `done`:** T6, la QA manual en el dispositivo (humano). Igual que en F-08-mobile, se cierra tras la QA.
