# F-10-mobile — Posición en la fila y espera, en vivo

Brief global: ../asclepio-api/docs/product/features/F-10.md (AC1, AC3–AC6)
Contrato: consume 0.10.0 (`GET /patient/queue-position`, `GET /patient/queue-position/stream`)
RNF aplicables: RNF-05, RNF-13
Depende de: F-10-api (`done`), F-02-mobile (pantalla de estado del prerregistro).

Glosario de la spec:
- **Tarjeta de fila**: componente de la pantalla de estado del prerregistro (`PreRegistrationStatus`) que muestra la instantánea más reciente.
- **Último dato**: la última instantánea recibida, con la hora local en que llegó.
- **Backoff**: espera antes de reconectar. Empieza en 1 s, se duplica en cada intento hasta un máximo de 30 s y vuelve a 1 s tras recibir un evento.

## Requisitos
- **R1** (Por estado) Mientras la pantalla de estado esté al menos en `STARTED` y el prerregistro tenga una atención, la app deberá mantener abierto el stream SSE `GET /patient/queue-position/stream` con el token del paciente. (→ F-10.AC4)
- **R2** (Por evento) Cuando la pantalla baje de `STARTED`, la app deberá cerrar el stream y no deberá mantener ninguna conexión ni trabajo en segundo plano. (→ F-10.AC4)
- **R3** (Por estado) Mientras la fase sea `WAITING_TRIAGE` o `WAITING_CARE` y `position > 1`, la tarjeta deberá mostrar la fase ("Esperando triage" o "Esperando al médico"), "Eres el N.º", "N personas delante" y el rango "min–max min" con el texto "Puede cambiar si llegan casos más urgentes". (→ F-10.AC1, F-10.AC3)
- **R4** (Por estado) Mientras `position = 1`, la tarjeta deberá mostrar "Eres el siguiente". (→ F-10.AC5)
- **R5** (Por estado) Mientras la fase sea `CALLED`, la tarjeta deberá mostrar "Te están llamando: acércate al consultorio". Con `DONE`, deberá mostrar "Atención finalizada" y no reconectar. (→ F-10.AC5)
- **R6** (Por estado) Mientras `estimateBasis = INSUFFICIENT_DATA`, la tarjeta deberá mostrar "Aún no hay datos suficientes para estimar el tiempo" en lugar del rango. (→ F-10.AC3)
- **R7** (Por estado) Mientras la fase sea `NO_ACTIVE`, la tarjeta deberá mostrar "Acércate a recepción para entrar en la fila" sin posición. (→ F-10.AC1)
- **R8** (No deseado) Si el stream se corta o falla la conexión, entonces la app deberá reconectar con backoff y, mientras tanto, mostrar el último dato con "Sin conexión · actualizado hace X min" y un indicador de reconexión. (→ F-10.AC5)
- **R9** (No deseado) Si el servidor responde 401, entonces la app deberá dejar de reconectar y llevar al login, como el resto de la app. (→ F-10.AC5)
- **R10** (Ubicuo) La app deberá excluir de los logs el contenido de los eventos y deberá mostrar solo los campos de la instantánea. (→ F-10.AC6)
- **R11** (Ubicuo) Los textos de la tarjeta deberán estar en el catálogo de localización y anunciarse con `liveRegion` educado (accesibilidad). (→ F-10.AC5)

## Preguntas abiertas
Ninguna propia (PA-F10-1 y PA-F10-2 viven en la API).
