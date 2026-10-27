# F-01-mobile — Cuenta e inicio de sesión del paciente

Brief global: ../asclepio-api/docs/product/features/F-01.md (F-01.AC7 y F-01.AC8; ADR 0008)
Contrato: consume **0.2.1** (`../asclepio-api/openapi/asclepio-api.v1.json`): `GET /api/v1/public/tenants`, `GET /api/v1/public/consent`, `POST /api/v1/auth/patient/accounts`, `POST /api/v1/auth/patient/login`, `GET /api/v1/me`.
RNF aplicables: RNF-05, RNF-10, RNF-11, RNF-13, RNF-14, RNF-15
Decisiones humanas de partida: progress/current/F-01-mobile.md, ADR 0008 (PA-PAC-1, PA-PAC-2, PA-PAC-5, PA-PAC-6), PA-F01-1 y PA-F01-2 del brief, el comportamiento de la API en ../asclepio-api/specs/F-01-api/requirements.md (R3, R4, R18, R20–R26, R37–R39, R44–R47) y las respuestas a PA-1..PA-9 de esta spec (humano, 2026-10-26).

Glosario de la spec:
- **IPS recordada**: el `tenantId`, el nombre de la IPS, el `siteId` y el nombre de la sede que el paciente eligió, guardados en el almacenamiento local de la app.
- **Sesión activa**: un `accessToken` y su `expiresAt` guardados por la app, con `expiresAt` posterior a la hora del dispositivo.
- **Versión vigente del consentimiento**: el campo `version` de la última respuesta 200 de `GET /api/v1/public/consent` obtenida en la pantalla de alta.
- **Mensaje de error de la app**: un texto de `strings.xml` elegido por la app según el caso; nunca el `title`, el `detail` ni `errors[].message` de la API. Los textos son los de la tabla siguiente (PA-1).
- **Mayor de edad**: `Period.between(birthDate, hoy).years >= 18`, donde `hoy` es la fecha civil actual en `America/Bogota` (misma regla que la API, D18 de F-01-api: quien nació un 29 de febrero cumple el 1 de marzo en años no bisiestos).
- **Prueba JVM**: JUnit en `app/src/test`, con el HTTP contra `MockWebServer` y Retrofit real (sin mockear Retrofit). Salvo que se indique otra cosa, cada `R<n>` se verifica con una prueba JVM cuyo nombre empieza por `R<n>:`.

### Textos de la UI (PA-1 y PA-6, aceptados)
| Clave | Texto |
|---|---|
| Lista de IPS vacía | "No hay IPS disponibles en este momento. Intenta más tarde." |
| IPS sin sedes | "Sin sedes disponibles" |
| Sin conexión | "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo." |
| Error general (5xx u otro) | "Algo salió mal. Intenta de nuevo en unos minutos." |
| 429 | "Demasiados intentos. Espera %d segundos e intenta de nuevo." |
| 401 en login | "Documento o contraseña incorrectos. Después de 5 intentos fallidos la cuenta se bloquea 15 minutos." |
| 409 en alta | "Ya existe una cuenta con este documento en esta IPS. Inicia sesión." |
| 422 en alta | "No pudimos crear la cuenta. Revisa tu fecha de nacimiento y vuelve a aceptar el consentimiento si cambió." |
| `/me` no es paciente | "Esta cuenta no es de paciente. Usa la aplicación web del personal." |
| Campo obligatorio | "Campo obligatorio" |
| Longitud máxima | "Máximo %d caracteres" |
| Longitud de contraseña | "La contraseña debe tener entre 10 y 128 caracteres" |
| Contraseñas distintas | "Las contraseñas no coinciden" |
| Menor de edad | "Debes ser mayor de 18 años para crear una cuenta" |
| Tipos de documento | CC "Cédula de ciudadanía", CE "Cédula de extranjería", PA "Pasaporte" |
| Sexo | M "Masculino", F "Femenino", X "Indeterminado / otro" |
| Inicio: identidad sin verificar | "Tu identidad se verificará en recepción" |
| Inicio: aviso de F-02 | "Pronto podrás pre-registrarte desde aquí" |
| Diálogo de cierre | Título "¿Cerrar sesión?", botones "Cancelar" y "Cerrar sesión" |

## Requisitos

### Elegir la IPS y la sede
- **R1** (Por evento) Cuando la app se abra sin IPS recordada, el sistema deberá pedir `GET /api/v1/public/tenants` y mostrar las IPS en el orden recibido, con las IPS sin `sites` deshabilitadas y el texto "Sin sedes disponibles"; al elegir una IPS, deberá mostrar sus `sites` en el orden recibido. (→ F-01.AC7)
- **R2** (Por evento) Cuando el paciente confirme una IPS y una de sus sedes (la sede es obligatoria para continuar), el sistema deberá guardar la IPS recordada, sin guardar el tipo ni el número de documento, y usar ese `tenantId` en todas las peticiones de alta e inicio de sesión posteriores. (→ F-01.AC7)
- **R3** (Por estado) Mientras exista una IPS recordada y no haya sesión activa, el sistema deberá abrir la app en la pantalla de inicio de sesión de esa IPS, mostrar su nombre y el de la sede, y ofrecer una acción para cambiar de IPS que vuelve al flujo de R1. (→ F-01.AC7)
- **R4** (No deseado) Si `GET /api/v1/public/tenants` responde 200 con una lista vacía, entonces el sistema deberá mostrar "No hay IPS disponibles en este momento. Intenta más tarde." con la acción de reintentar y no deberá permitir continuar. (→ F-01.AC7)
- **R5** (No deseado) Si `GET /api/v1/public/tenants` falla (sin conexión, 5xx o 429), entonces el sistema deberá mostrar el mensaje de error de la app que corresponda según R22 o R23 con la acción de reintentar, sin modificar la IPS recordada. (→ F-01.AC7)

### Consentimiento
- **R6** (Por evento) Cuando se abra la pantalla de alta, el sistema deberá pedir `GET /api/v1/public/consent` y mostrar el `text` completo en la misma pantalla (en el panel derecho en tablet, R35), con los títulos (`#`), la negrita (`**…**`) y las listas (`- `, `1. `) de Markdown convertidos a estilo de texto y sin mostrar esos símbolos. (→ F-01.AC8)
- **R7** (Ubicuo) El sistema deberá mostrar la casilla de aceptación del consentimiento desmarcada al abrir la pantalla de alta, permitir marcarla sin exigir desplazarse hasta el final del texto y mantener deshabilitado el botón de crear la cuenta mientras la casilla no esté marcada. (→ F-01.AC8)
- **R8** (Por evento) Cuando el paciente envíe el alta con la casilla marcada, el sistema deberá enviar `consentAccepted = true` y `consentVersion` igual a la versión vigente del consentimiento, como cadena. (→ F-01.AC8)
- **R9** (No deseado) Si `GET /api/v1/public/consent` falla, entonces el sistema deberá mostrar el mensaje de error de la app que corresponda según R22 o R23 con la acción de reintentar y no deberá permitir enviar el alta mientras no haya una versión vigente del consentimiento. (→ F-01.AC8)

### Formulario de alta
- **R10** (Por evento) Cuando el paciente envíe un alta que pasa la validación local, el sistema deberá enviar `POST /api/v1/auth/patient/accounts` con exactamente los campos `tenantId`, `documentType` (`CC`, `CE` o `PA`), `documentNumber` y `fullName` (ambos sin espacios al inicio ni al final), `birthDate` (`YYYY-MM-DD`), `sex` (`M`, `F` o `X`), `password`, `consentAccepted` y `consentVersion`, sin ningún otro campo (por ejemplo, sin la confirmación de contraseña, `consentAt` ni `identityVerified`). (→ F-01.AC7, F-01.AC8)
- **R11** (No deseado) Si al enviar el alta falta el tipo de documento, el sexo o la fecha de nacimiento ("Campo obligatorio"), el número de documento queda vacío o supera 64 caracteres tras quitar los espacios de los extremos, el nombre queda vacío o supera 200 caracteres tras quitar esos espacios ("Campo obligatorio" o "Máximo %d caracteres"), la contraseña tiene menos de 10 o más de 128 caracteres en unidades UTF-16, como `String.length` ("La contraseña debe tener entre 10 y 128 caracteres"), o la confirmación de contraseña no es idéntica a la contraseña ("Las contraseñas no coinciden"), entonces el sistema deberá marcar cada campo inválido con ese mensaje y no deberá enviar la petición. (→ F-01.AC7)
- **R12** (No deseado) Si la fecha de nacimiento no corresponde a un mayor de edad (incluida una fecha futura), entonces el sistema deberá marcar el campo de fecha de nacimiento con "Debes ser mayor de 18 años para crear una cuenta" y no deberá enviar la petición. (→ F-01.AC7)
- **R13** (No deseado) Si `POST /api/v1/auth/patient/accounts` responde 400, entonces el sistema deberá marcar con su mensaje de error de la app cada campo del formulario cuyo nombre aparezca en `errors[].field` y, si ningún `field` corresponde a un campo del formulario o `errors` falta, mostrar "Algo salió mal. Intenta de nuevo en unos minutos.". (→ F-01.AC7)
- **R14** (No deseado) Si `POST /api/v1/auth/patient/accounts` responde 409, entonces el sistema deberá mostrar "Ya existe una cuenta con este documento en esta IPS. Inicia sesión.", conservar los datos del formulario y ofrecer la acción de ir al inicio de sesión. (→ F-01.AC7)
- **R15** (No deseado) Si `POST /api/v1/auth/patient/accounts` responde 422, entonces el sistema deberá mostrar "No pudimos crear la cuenta. Revisa tu fecha de nacimiento y vuelve a aceptar el consentimiento si cambió.", volver a pedir `GET /api/v1/public/consent` y, si la `version` recibida es distinta de la versión vigente, reemplazar el texto mostrado, actualizar la versión vigente y desmarcar la casilla de aceptación. (→ F-01.AC7, F-01.AC8)
- **R16** (Por evento) Cuando `POST /api/v1/auth/patient/accounts` responda 201, el sistema deberá iniciar sesión automáticamente con el mismo `tenantId`, tipo y número de documento y contraseña siguiendo R17 a R21, sin pedirlos de nuevo; si ese inicio de sesión no termina en una sesión activa, deberá mostrar la pantalla de inicio de sesión con el tipo y el número de documento ya escritos y el mensaje de error que corresponda. (→ F-01.AC7)

### Inicio de sesión
- **R17** (Por evento) Cuando el paciente envíe el inicio de sesión con tipo de documento, número de documento y contraseña no vacíos, el sistema deberá enviar `POST /api/v1/auth/patient/login` con `tenantId` de la IPS recordada, `documentType`, `documentNumber` sin espacios en los extremos y `password`; si alguno de los tres está vacío, deberá marcarlo con "Campo obligatorio" sin enviar la petición. Los campos de documento empiezan vacíos al abrir la app (no se recuerdan), salvo en el caso de R16. (→ F-01.AC7)
- **R18** (No deseado) Si `POST /api/v1/auth/patient/login` responde 401, entonces el sistema deberá mostrar "Documento o contraseña incorrectos. Después de 5 intentos fallidos la cuenta se bloquea 15 minutos.", idéntico para cualquier causa (cuenta inexistente, contraseña incorrecta, cuenta bloqueada o IPS inactiva), y vaciar el campo de contraseña. (→ F-01.AC7)
- **R19** (Por evento) Cuando `POST /api/v1/auth/patient/login` responda 200, el sistema deberá pedir `GET /api/v1/me` con `Authorization: Bearer <accessToken>` y, solo si la respuesta tiene `kind = "PATIENT"` y `tenantId` igual al de la IPS recordada, guardar `accessToken` y `expiresAt` como sesión activa y mostrar la pantalla de inicio. (→ F-01.AC7)
- **R20** (No deseado) Si `GET /api/v1/me` tras el inicio de sesión responde con `kind` distinto de `"PATIENT"` (incluido `"STAFF"`) o con un `tenantId` distinto del de la IPS recordada, entonces el sistema deberá descartar el token sin guardarlo y mostrar "Esta cuenta no es de paciente. Usa la aplicación web del personal.". (→ F-01.AC7)
- **R21** (No deseado) Si `GET /api/v1/me` tras el inicio de sesión falla (401, 5xx, sin conexión o cuerpo no válido), entonces el sistema deberá descartar el token sin guardarlo y mostrar el mensaje de error de la app que corresponda según R22 o R23 (un 401 aquí se muestra como error general). (→ F-01.AC7)

### Errores comunes
- **R22** (No deseado) Si cualquiera de los cinco endpoints de esta feature responde 429, entonces el sistema deberá mostrar "Demasiados intentos. Espera %d segundos e intenta de nuevo." con los segundos de la cabecera `Retry-After` y deshabilitar la acción que lo provocó durante esos segundos; si la cabecera falta o no es un entero positivo, deberá usar 60 s. (→ F-01.AC7)
- **R23** (No deseado) Si una petición de esta feature falla sin respuesta HTTP (`IOException`), entonces el sistema deberá mostrar "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo."; si responde 5xx u otro código no tratado en R13–R22, deberá mostrar "Algo salió mal. Intenta de nuevo en unos minutos."; en ningún caso deberá mostrar el `title`, el `detail` ni `errors[].message` de la API. (→ F-01.AC7)
- **R24** (Ubicuo) El sistema deberá convertir en `core/network` toda respuesta HTTP de error a un `ProblemDetails` (`type`, `title`, `status`, `detail`, `instance`, `errors[]` con `field` y `message`, y el valor de `Retry-After`), aceptando la ausencia de los campos opcionales y, si el cuerpo no es `problem+json` válido, devolviendo un `ProblemDetails` con solo el código HTTP. (→ F-01.AC7)
- **R25** (Por estado) Mientras una petición de alta o de inicio de sesión esté en curso, el sistema deberá mostrar el estado de carga y mantener deshabilitado el botón de envío, de modo que una segunda pulsación no envíe otra petición. (→ F-01.AC7)

### Sesión y token
- **R26** (Ubicuo) El sistema deberá guardar en disco el `accessToken` solo cifrado con AES-256-GCM, con una clave no exportable del Android Keystore, de modo que el archivo persistido no contenga el token en claro; al leerlo, deberá devolver exactamente el token original. (→ F-01.AC7) — la parte de Keystore se verifica con **prueba instrumentada** (`app/src/androidTest`); la lógica de almacenamiento, con prueba JVM y un cifrador falso.
- **R27** (No deseado) Si al abrir la app o al volver a primer plano el `expiresAt` guardado no es posterior a la hora del dispositivo, o el token guardado no se puede descifrar, entonces el sistema deberá borrar la sesión y mostrar la pantalla de inicio de sesión. (→ F-01.AC7)
- **R28** (Por evento) Cuando la app se abra con una sesión activa, el sistema deberá mostrar la pantalla de inicio y pedir `GET /api/v1/me`; si responde 401, deberá borrar la sesión y mostrar la pantalla de inicio de sesión; si falla sin respuesta HTTP o con 5xx, deberá conservar la sesión. (→ F-01.AC7)
- **R29** (Ubicuo) El sistema deberá añadir la cabecera `Authorization: Bearer <accessToken>` solo a las peticiones a rutas fuera de `/api/v1/public/` y `/api/v1/auth/`, y nunca a `GET /api/v1/public/tenants`, `GET /api/v1/public/consent`, `POST /api/v1/auth/patient/accounts` ni `POST /api/v1/auth/patient/login`. (→ F-01.AC7)
- **R30** (Por evento) Cuando el paciente pulse "Cerrar sesión" en la pantalla de inicio, el sistema deberá mostrar el diálogo "¿Cerrar sesión?" con "Cancelar" y "Cerrar sesión"; si confirma, deberá borrar de la memoria y del disco el `accessToken` y su `expiresAt`, conservar la IPS recordada, no llamar a ningún endpoint (el contrato no tiene logout) y mostrar la pantalla de inicio de sesión; si cancela, deberá conservar la sesión. (→ F-01.AC7)
- **R31** (Por estado) Mientras haya una sesión activa, el sistema deberá mostrar la pantalla de inicio con el nombre de la IPS y de la sede recordadas, el aviso "Tu identidad se verificará en recepción" mientras `identityVerified` de `/me` sea `false`, el texto "Pronto podrás pre-registrarte desde aquí" y el botón "Cerrar sesión", sin mostrar documento ni nombre del paciente. (→ F-01.AC7)

### Privacidad y mensajes
- **R32** (Ubicuo) El sistema deberá mantener la contraseña y su confirmación solo en memoria: no deberá guardarlas en disco, en `SavedStateHandle` ni en la IPS recordada, y el `toString()` de los estados de formulario y de los DTO que las contienen deberá mostrarlas enmascaradas. (→ F-01.AC7)
- **R33** (Ubicuo) El sistema deberá no escribir en Logcat datos personales ni tokens: el código de `app/src/main` no deberá usar `android.util.Log`, `println`, `printStackTrace` ni `HttpLoggingInterceptor`. (→ F-01.AC7; RNF-05) — se verifica con una prueba JVM que recorre las fuentes.
- **R34** (Ubicuo) El sistema deberá traducir cada resultado de error de esta feature a un identificador de `strings.xml` (`@StringRes`) con los textos de la tabla de PA-1, sin textos literales de UI en Composables ni en ViewModels. (→ F-01.AC7)

### Layout
- **R35** (Opcional) Donde el ancho de ventana sea `Medium` o `Expanded`, el sistema deberá mostrar el alta en dos paneles (formulario y texto del consentimiento) y la elección de IPS en dos paneles (IPS y sedes); donde sea `Compact`, deberá mostrar cada pantalla en una sola columna. (→ F-01.AC7, F-01.AC8; RNF-13) — la elección del layout se prueba en la JVM como función pura; el aspecto, en la prueba manual de teléfono y tablet.

## Preguntas abiertas
- [x] **PA-1** Textos de la UI (mensajes de error y etiquetas) → propuesta aceptada (humano, 2026-10-26). Textos en la tabla del glosario; cubierto por R4, R5, R9, R11–R15, R17, R18, R20–R23 y R34.
- [x] **PA-2** ¿Entrada automática tras el alta? → propuesta aceptada (humano, 2026-10-26): entra automáticamente; si ese login falla, va al inicio de sesión con el documento ya escrito. Cubierto por R16.
- [x] **PA-3** Pantalla tras iniciar sesión antes de F-02 → propuesta aceptada (humano, 2026-10-26): "Inicio" con IPS y sede, aviso de identidad sin verificar, aviso de F-02 y "Cerrar sesión", sin documento ni nombre. Cubierto por R31.
- [x] **PA-4** ¿Se recuerda el documento? → propuesta aceptada (humano, 2026-10-26): no; solo se recuerdan la IPS y la sede. Cubierto por R2 y R17.
- [x] **PA-5** ¿Confirmar la contraseña en el alta? → propuesta aceptada (humano, 2026-10-26): sí; la confirmación no se envía a la API. Cubierto por R10, R11 y R32.
- [x] **PA-6** Etiquetas de `sex` → propuesta aceptada (humano, 2026-10-26): "Masculino", "Femenino" e "Indeterminado / otro". Cubierto por la tabla del glosario y R34.
- [x] **PA-7** Sede en F-01 → propuesta aceptada (humano, 2026-10-26): se elige junto con la IPS, es obligatoria, se recuerda y en F-02 aparecerá preseleccionada y editable; una IPS sin sedes se muestra deshabilitada con "Sin sedes disponibles". Cubierto por R1, R2 y R3.
- [x] **PA-8** ¿Confirmación al cerrar sesión? → propuesta aceptada (humano, 2026-10-26): sí, diálogo "¿Cerrar sesión?" con "Cancelar" y "Cerrar sesión". Cubierto por R30.
- [x] **PA-9** ¿Desplazarse hasta el final del consentimiento? → propuesta aceptada (humano, 2026-10-26): no; la casilla se puede marcar en cualquier momento, con el texto visible en la misma pantalla. Cubierto por R6 y R7.
