# F-01-mobile — Cuenta e inicio de sesión del paciente (sdd: true)

- **Inicio:** 2026-10-26 · Dependencias: F-00-mobile done y F-01-api done (contrato **0.2.0**).
- **Fuentes:** `../asclepio-api/docs/product/features/F-01.md` (F-01.AC7, AC8), ADR 0008 (app del paciente), contrato 0.2.0: `GET /public/tenants`, `GET /public/consent`, `POST /auth/patient/accounts`, `POST /auth/patient/login`, `GET /me`.
- **Decisiones humanas ya tomadas:** documento (CC/CE/PA) + contraseña (≥ 10); 18 años o más con fecha de nacimiento; IPS y sede elegidas de una lista; consentimiento obligatorio con versión; JWT de 8 h sin refresh; 401 genérico; bloqueo tras 5 intentos; 429 con Retry-After.
- **Pendientes heredados de F-00-mobile:** parseo de ProblemDetails en `core/network`; subpaquetes `data/` y `ui/` por feature; mensajes en `strings.xml`.

## Plan
1. spec_author → `specs/F-01-mobile/`.
2. ⏸ Aprobación humana.
3. implementer → reviewer → done.

## Estado
- 2026-10-26: spec escrita (R1–R35, T1–T20) → **spec_ready**. ⏸ 9 preguntas abiertas presentadas al humano.
- 2026-10-26: el contrato pasó a **0.2.1** (corrección: `consentVersion` es string). La app debe declarar `consumes_contract: 0.2.1`.
- **2026-10-26: APROBADA por el humano en el chat** ("acepto las propuestas, aprobado"): PA-1…PA-9 con las propuestas del spec_author. El spec_author las registra y actualiza el contrato a 0.2.1 antes de implementar.
- 2026-10-26: sin preguntas abiertas → **in_progress** (consume 0.2.1). Se lanza el implementer.
