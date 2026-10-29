# F-02-mobile — Pre-registro del paciente

Brief global: ../asclepio-api/docs/product/features/F-02.md (F-02.AC8 y F-02.AC10; F-02.AC3 en lo que toca al cliente; ADR 0008)
Contrato: consume **0.4.0** (`../asclepio-api/openapi/asclepio-api.v1.json`; los endpoints del paciente llegaron en 0.3.0 y no cambian en 0.4.0): `GET /api/v1/patient/profile`, `PATCH /api/v1/patient/profile`, `POST /api/v1/patient/pre-registrations`, `GET /api/v1/patient/pre-registrations` y, ya consumido en F-01, `GET /api/v1/public/tenants`.
RNF aplicables: RNF-05, RNF-06, RNF-10, RNF-11, RNF-13, RNF-14, RNF-15
Decisiones humanas de partida: ADR 0008 (PA-PAC-2: sede de una lista; PA-PAC-3: expira a las 6 h; PA-PAC-4: solo el estado, nunca el nivel), PA-F02-5 (edición solo de `fullName`, `birthDate` y `sex` mientras `editable`), PA-F02-6 (un pre-registro vigente y una atención activa como mucho), PA-F02-7 (el paciente no ve el motivo de rechazo ni retira su pre-registro), PA-F02-9 (la app traduce el estado efectivo y el `status` de la atención a textos; `TRIAGED` = "en espera de atención"), PA-7 de F-01-mobile (la sede recordada aparece preseleccionada y editable) y el comportamiento de la API en ../asclepio-api/specs/F-02-api/requirements.md (R1–R14, R45–R47).

Glosario de la spec:
- **IPS y sede recordadas**, **sesión activa**, **mayor de edad**, **mensaje de error de la app** y **prueba JVM**: como en specs/F-01-mobile/requirements.md.
- **Último pre-registro**: el primer elemento de la respuesta 200 de `GET /api/v1/patient/pre-registrations` (la API los ordena por `preRegisteredAt` descendente, R12 de F-02-api).
- **Pre-registro en curso**: un último pre-registro con `status = PENDING`, o con `status = CONFIRMED` y `encounter` nulo o con `encounter.status` distinto de `CLOSED` y `CANCELLED`, o con un `status` o `encounter.status` que la app no conoce.
- **Motivo válido**: `chiefComplaintText` que, sin espacios al inicio ni al final, no está vacío y tiene como mucho 2 000 caracteres en unidades UTF-16 (`String.length`), según `CreatePreRegistrationDto` del contrato.
- **Hora mostrada**: un valor ISO-8601 de la API formateado en `America/Bogota` con `core/ui/DateTime.formatBogota`; la app no calcula horas a partir del reloj del dispositivo.

### Textos de la UI (PA-M02-1, aceptada)
| Clave | Texto |
|---|---|
| Inicio: acción de datos | "Mis datos" |
| Inicio: acción de pre-registro | "Nuevo pre-registro" |
| Inicio: actualizar | "Actualizar" |
| Inicio: sin pre-registros | "Aún no has enviado ningún pre-registro." |
| Inicio: sede | "Sede: %s" |
| Inicio: enviado | "Enviado: %s" |
| Inicio: vencimiento (solo `PENDING`) | "Si recepción no lo confirma, vence: %s" |
| Mis datos: título | "Mis datos" |
| Mis datos: bloqueados | "Tus datos ya fueron verificados en recepción y no se pueden cambiar desde la app." |
| Mis datos: guardar | "Guardar" |
| Mis datos: guardado | "Datos guardados" |
| Mis datos: valor nulo | "—" |
| Tipos de documento | CC "Cédula de ciudadanía", CE "Cédula de extranjería", PA "Pasaporte", TI "Tarjeta de identidad", RC "Registro civil"; otro código: el código tal cual |
| Pre-registro: título | "Nuevo pre-registro" |
| Pre-registro: sede | "Sede a la que llegas" |
| Pre-registro: motivo | "Motivo de consulta" |
| Pre-registro: contador | "%1$d / %2$d" |
| Pre-registro: enviar | "Enviar pre-registro" |
| Pre-registro: sin sedes | "No hay sedes disponibles para tu IPS. Intenta más tarde." |
| Pre-registro: sede obligatoria | "Elige una sede" |
| 409 en pre-registro | "Ya tienes un pre-registro pendiente o una atención en curso." con la acción "Ver mi estado" |
| 422 en pre-registro | "La sede elegida ya no está disponible. Elige otra sede." |
| 422 en Mis datos | "Debes ser mayor de 18 años." (texto propio de Mis datos, cadena `error_underage_profile`; el alta de F-01 conserva el suyo) |
| Campo obligatorio, longitud máxima, sin conexión, error general, 429 | los de F-01-mobile |

### Textos de estado (PA-M02-2, aceptada; desarrolla PA-F02-9)
| Estado del último pre-registro | Texto |
|---|---|
| `PENDING` | "Pendiente de confirmación. Acércate a recepción para confirmar tu llegada." |
| `EXPIRED` | "Venció sin confirmarse. Puedes enviar uno nuevo." |
| `REJECTED` | "Recepción no aceptó este pre-registro. Puedes enviar uno nuevo o acercarte a recepción." |
| `CONFIRMED` con `encounter` nulo | "Llegada confirmada" |
| `CONFIRMED` con `encounter.status` `REGISTERED` o `WAITING_TRIAGE` | "En espera de triage" |
| `CONFIRMED` con `encounter.status` `TRIAGED` | "En espera de atención" |
| `CONFIRMED` con `encounter.status` `IN_CARE` | "En atención" |
| `CONFIRMED` con `encounter.status` `CLOSED` | "Atención cerrada" |
| `CONFIRMED` con `encounter.status` `CANCELLED` | "Atención cancelada" |
| Cualquier otro valor de `status` o `encounter.status` | "Estado no disponible. Intenta actualizar más tarde." |

## Requisitos

### Mis datos
- **R1** (Por evento) Cuando el paciente abra "Mis datos", el sistema deberá pedir `GET /api/v1/patient/profile` y mostrar el tipo de documento (con su etiqueta) y el número de documento como texto no editable, y el nombre, la fecha de nacimiento (`dd/MM/yyyy`) y el sexo (con su etiqueta de F-01), mostrando "—" en los campos nulos. (→ F-02.AC8, F-02.AC10)
- **R2** (Por estado) Mientras el perfil cargado tenga `editable = false`, el sistema deberá mostrar los datos sin campos editables ni botón "Guardar", con el aviso "Tus datos ya fueron verificados en recepción y no se pueden cambiar desde la app.". (→ F-02.AC10)
- **R3** (Por evento) Cuando el paciente pulse "Guardar" con `editable = true` y la validación local superada, el sistema deberá enviar `PATCH /api/v1/patient/profile` solo con los campos que difieren del perfil cargado, de entre `fullName` (sin espacios en los extremos), `birthDate` (`YYYY-MM-DD`) y `sex`, sin `documentType`, `documentNumber` ni ningún otro campo, y, al recibir 200, mostrar el perfil de la respuesta y "Datos guardados". (→ F-02.AC8, F-02.AC10)
- **R4** (Por estado) Mientras ningún campo editable difiera del perfil cargado, el sistema deberá mantener deshabilitado el botón "Guardar". (→ F-02.AC8)
- **R5** (No deseado) Si al pulsar "Guardar" el nombre queda vacío o supera 200 caracteres tras quitar los espacios de los extremos ("Campo obligatorio" o "Máximo %d caracteres"), o la fecha de nacimiento no corresponde a un mayor de edad ("Debes ser mayor de 18 años"), entonces el sistema deberá marcar cada campo inválido con ese mensaje y no deberá enviar la petición. (→ F-02.AC8)
- **R6** (No deseado) Si `PATCH /api/v1/patient/profile` responde 409, entonces el sistema deberá mostrar el aviso de R2, volver a pedir `GET /api/v1/patient/profile` y mostrar los datos recibidos según R1 y R2. (→ F-02.AC10)
- **R7** (No deseado) Si `PATCH /api/v1/patient/profile` responde 422, entonces el sistema deberá marcar la fecha de nacimiento con "Debes ser mayor de 18 años"; y si responde 400, deberá marcar con su mensaje de la app cada campo cuyo nombre (`fullName`, `birthDate` o `sex`) aparezca en `errors[].field` y, si ninguno corresponde o `errors` falta, mostrar el error general; en ambos casos, conservando lo escrito. (→ F-02.AC8)

### Formulario de pre-registro
- **R8** (Por evento) Cuando el paciente abra "Nuevo pre-registro", el sistema deberá pedir `GET /api/v1/public/tenants`, mostrar las `sites` de la IPS recordada en el orden recibido y dejar preseleccionada la sede recordada si su `id` está en esa lista; si no está, ninguna sede quedará seleccionada. (→ F-02.AC8)
- **R9** (No deseado) Si `GET /api/v1/public/tenants` falla, o la respuesta no contiene la IPS recordada o esta no tiene sedes, entonces el sistema deberá mostrar el mensaje de error de la app que corresponda ("No hay sedes disponibles para tu IPS. Intenta más tarde." en los dos últimos casos) con la acción de reintentar, y no deberá permitir enviar el pre-registro. (→ F-02.AC8)
- **R10** (Por evento) Cuando el paciente envíe el pre-registro con una sede seleccionada y un motivo válido, el sistema deberá enviar `POST /api/v1/patient/pre-registrations` con exactamente los campos `siteId` (el `id` de la sede seleccionada), `chiefComplaintText` (sin espacios en los extremos) y `complaintSource` igual a `"TYPED"`, sin ningún otro campo. (→ F-02.AC8, F-02.AC4)
- **R11** (No deseado) Si al enviar no hay sede seleccionada ("Elige una sede") o el motivo no es válido ("Campo obligatorio" si queda vacío; "Máximo %d caracteres" si supera 2 000), entonces el sistema deberá marcar cada campo inválido con ese mensaje y no deberá enviar la petición; el campo del motivo deberá mostrar en todo momento el contador "%1$d / 2000" con la longitud actual. (→ F-02.AC8)
- **R12** (Por evento) Cuando `POST /api/v1/patient/pre-registrations` responda 201, el sistema deberá volver a la pantalla de inicio y mostrar como último pre-registro el de la respuesta (R17), y descartar el motivo escrito. (→ F-02.AC8)
- **R13** (No deseado) Si `POST /api/v1/patient/pre-registrations` responde 409, entonces el sistema deberá mostrar "Ya tienes un pre-registro pendiente o una atención en curso." con la acción "Ver mi estado", que vuelve a la pantalla de inicio y vuelve a pedir el estado (R17). (→ F-02.AC8)
- **R14** (No deseado) Si `POST /api/v1/patient/pre-registrations` responde 422, entonces el sistema deberá mostrar "La sede elegida ya no está disponible. Elige otra sede.", quitar la selección de sede, volver a pedir las sedes según R8 y conservar el motivo escrito. (→ F-02.AC8, F-02.AC10)
- **R15** (No deseado) Si `POST /api/v1/patient/pre-registrations` responde 400, entonces el sistema deberá marcar la sede si `errors[].field` contiene `siteId` y el motivo si contiene `chiefComplaintText`, y mostrar el error general si ninguno corresponde, `errors` falta o contiene `complaintSource`, conservando lo escrito. (→ F-02.AC8)

### Inicio y estado
- **R16** (Por estado) Mientras haya una sesión activa, el sistema deberá mostrar en la pantalla de inicio, además de lo de R31 de F-01-mobile, la acción "Mis datos" y el estado del último pre-registro (R17–R21) en lugar del texto "Pronto podrás pre-registrarte desde aquí". (→ F-02.AC8) — el aspecto se verifica en la prueba manual; la ausencia del texto anterior, con una prueba JVM sobre los recursos.
- **R17** (Por evento) Cuando se muestre la pantalla de inicio, cuando la app vuelva a primer plano en ella y cuando el paciente pulse "Actualizar", el sistema deberá pedir `GET /api/v1/patient/pre-registrations` y mostrar del último pre-registro el nombre de la sede (`siteName`), el texto de estado de la tabla de PA-M02-2, "Enviado: %s" con `preRegisteredAt` como hora mostrada y, solo si `status = PENDING`, "Si recepción no lo confirma, vence: %s" con `expiresAt` como hora mostrada. (→ F-02.AC8)
- **R18** (No deseado) Si `GET /api/v1/patient/pre-registrations` responde 200 con una lista vacía, entonces el sistema deberá mostrar "Aún no has enviado ningún pre-registro." y la acción "Nuevo pre-registro". (→ F-02.AC8)
- **R19** (Por estado) Mientras el último pre-registro esté en curso, el sistema no deberá mostrar la acción "Nuevo pre-registro"; en otro caso (`EXPIRED`, `REJECTED` o `CONFIRMED` con `encounter.status` `CLOSED` o `CANCELLED`), deberá mostrarla. (→ F-02.AC8)
- **R20** (No deseado) Si `GET /api/v1/patient/pre-registrations` falla, entonces el sistema deberá mostrar en la zona de estado el mensaje de error de la app que corresponda con la acción "Actualizar", sin mostrar "Nuevo pre-registro" y manteniendo disponibles "Mis datos" y "Cerrar sesión". (→ F-02.AC8)
- **R21** (Ubicuo) El sistema deberá mostrar del pre-registro y de su atención solo los datos de R17, sin nivel de triage, sin `encounter.id` y sin ningún campo que la respuesta traiga además de los del contrato (por ejemplo, un `level`, que se ignora al deserializar). (→ F-02.AC8, F-02.AC10)
- **R22** (Ubicuo) El sistema no deberá mostrar ni calcular una hora de llegada: no mostrará `arrivalAt` ni ninguna hora derivada del reloj del dispositivo o de `preRegisteredAt`, y ninguna petición de esta feature deberá incluir campos de hora (`arrivalAt`, `preRegisteredAt`, `expiresAt`, `resolvedAt`, `occurredAt`, `createdAt`, `updatedAt`). (→ F-02.AC3, F-02.AC8; RNF-10)

### Errores, sesión y privacidad
- **R23** (No deseado) Si cualquiera de los endpoints `/api/v1/patient/…` responde 401, entonces el sistema deberá borrar la sesión con `SessionManager.logout()` (conservando la IPS recordada) y mostrar la pantalla de inicio de sesión. (→ F-02.AC10)
- **R24** (No deseado) Si una petición de esta feature responde 429, falla sin respuesta HTTP, responde 403, 404, 5xx u otro código no tratado en R6, R7, R13–R15 o R23, entonces el sistema deberá mostrar el mensaje de error de la app que corresponda según R22 y R23 de F-01-mobile (con 403 y 404 como error general) y, en el 429, deshabilitar la acción que lo provocó durante los segundos de `Retry-After` (60 s si falta o no es un entero positivo); en ningún caso deberá mostrar `title`, `detail` ni `errors[].message` de la API. (→ F-02.AC8)
- **R25** (Por estado) Mientras un `PATCH /api/v1/patient/profile` o un `POST /api/v1/patient/pre-registrations` esté en curso, el sistema deberá mostrar el estado de carga y mantener deshabilitado su botón de envío, de modo que una segunda pulsación no envíe otra petición. (→ F-02.AC8)
- **R26** (Ubicuo) El sistema deberá usar en `feature/patients` y `feature/encounters` solo rutas bajo `api/v1/patient/` y `api/v1/public/`, sin parámetros de ruta ni de query con identificadores de paciente, y sin enviar `patientId` ni `tenantId` en ningún cuerpo. (→ F-02.AC10) — se verifica con una prueba JVM que inspecciona por reflexión las anotaciones de las interfaces Retrofit de esas features.
- **R27** (Ubicuo) El sistema deberá mantener el motivo de consulta y los datos del perfil solo en memoria (no en DataStore ni en `SavedStateHandle`), y el `toString()` de los estados de formulario y de los DTO que los contienen deberá mostrar enmascarados el motivo, el nombre, el documento y la fecha de nacimiento; la prueba de R33 de F-01-mobile (sin `Log`, `println`, `printStackTrace` ni `HttpLoggingInterceptor` en `app/src/main`) deberá seguir en verde. (→ F-02.AC10; RNF-05)
- **R28** (Ubicuo) El sistema deberá traducir cada texto y cada resultado de error de esta feature a un identificador de `strings.xml` (`@StringRes`) con los textos de las tablas de PA-M02-1 y PA-M02-2, sin textos literales de UI en Composables ni en ViewModels. (→ F-02.AC8)

### Layout
- **R29** (Opcional) Donde el ancho de ventana sea `Medium` o `Expanded`, el sistema deberá mostrar el formulario de pre-registro en dos paneles (sedes y motivo); donde sea `Compact`, en una sola columna; "Mis datos" y el inicio deberán mostrarse en una columna de 600 dp de ancho máximo en ambos casos. (→ F-02.AC8; RNF-13) — la elección se prueba en la JVM con `paneMode`; el aspecto, en la prueba manual de teléfono y tablet.

> Trazabilidad por criterio: AC3 → R22 · AC4 → R10 · AC8 → R1, R3–R5, R7–R25, R28, R29 · AC10 → R1–R3, R6, R14, R21, R23, R26, R27.

## Preguntas abiertas
- [x] **PA-M02-1** Textos de la UI (etiquetas, avisos y mensajes de error) → los de la tabla "Textos de la UI" del glosario. Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R1–R20, R24 y R28.
- [x] **PA-M02-2** Texto de cada estado visible para el paciente (PA-PAC-4 y PA-F02-9 solo fijan cuatro estados y `TRIAGED`; faltaban `EXPIRED`, `REJECTED`, `CANCELLED` y los valores desconocidos) → la tabla "Textos de estado" del glosario; `REGISTERED` se muestra igual que `WAITING_TRIAGE` porque la API pasa a `WAITING_TRIAGE` al confirmar (PA-F02-2), y un valor desconocido cuenta como "en curso" para no ofrecer un pre-registro que la API rechazaría. Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R17 y R28.
- [x] **PA-M02-3** ¿Qué se muestra del pre-registro? → solo el último (sin historial), con sede, estado, "Enviado" (`preRegisteredAt`) y, si está `PENDING`, la hora de vencimiento (`expiresAt`), ambas tal como las entrega la API. `preRegisteredAt` se rotula "Enviado" y nunca "Llegada" para no confundirla con la hora legal (ADR 0008). Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R17 y R21.
- [x] **PA-M02-4** ¿Cómo se actualiza el estado? → al mostrar el inicio, al volver la app a primer plano y con el botón "Actualizar"; sin sondeo periódico ni notificaciones push en el MVP. Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R17.
- [x] **PA-M02-5** Si el paciente elige en el pre-registro una sede distinta de la recordada, ¿se cambia la sede recordada? → no; la sede elegida solo se usa en ese pre-registro y la recordada sigue siendo la de F-01 (se cambia con "Cambiar IPS" en el login). Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R8 y R10.
- [x] **PA-M02-6** ¿El formulario de pre-registro muestra o pide confirmar los datos del paciente? → no; el pre-registro solo pide sede y motivo (el paciente sale del token) y los datos se revisan y editan en "Mis datos", accesible desde el inicio. Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R8–R11 y R16.
- [x] **PA-M02-7** ¿Qué ve el paciente cuando un endpoint del paciente responde 401 (token vencido o revocado)? → se borra la sesión y se vuelve al inicio de sesión de su IPS sin mensaje adicional, igual que con la expiración local de R27 de F-01-mobile. Resuelta el 2026-10-27: propuesta aceptada (humano). Cubierto por R23.
