# Historial de sesiones (append-only)

---

## 2026-10-26 — Harness SDD inicial
- **Resultado:** se crearon el harness SDD (agentes, hooks, `init.sh`, CHECKPOINTS) y la documentación del repo.
- **Estado:** todas las features en `pending`.

## 2026-10-26 — F-00-mobile: bootstrap técnico → done
- **Resultado:** Gradle 9.8.2 (wrapper) y AGP 9.4.1 con Kotlin 2.4.21 integrado. Compose (BOM 2026.09.00), minSdk 31 (ADR 0001) y compile/targetSdk 37. Retrofit 3 + kotlinx.serialization. `API_BASE_URL` desde `local.properties`, texto en claro solo en debug y `RECORD_AUDIO` declarado. Pantalla de estado `/health` con MVVM y `WindowSizeClass` (teléfono: una columna; tablet: dos paneles). 3 pruebas JVM contra MockWebServer; lint sin issues. Consume el contrato **0.1.0**.
- **Revisión:** APROBADA.
- **Pendientes para F-01-mobile:** (1) parseo de `ProblemDetails` en `core/network`; (2) usar los subpaquetes `data/` y `ui/` en cada feature; (3) mensajes de error en `strings.xml`.
- **Pendiente del humano:** probar en un dispositivo o emulador, teléfono y tablet (`adb reverse tcp:3000 tcp:3000` + `./gradlew installDebug`).

## 2026-10-27 — F-01-mobile: cuenta e inicio de sesión del paciente → done
- **Resultado:** elección de IPS y sede de la lista pública (recordadas en DataStore); consentimiento en Markdown con casilla obligatoria y versión vigente; alta (CC, CE o PA; 18 años o más con `Period.between` en America/Bogota; sexo M, F o X; contraseña de 10 a 128 caracteres con confirmación) con entrada automática tras el alta; login con 401 genérico, 429 con Retry-After y bloqueo del botón; `GET /me` debe dar kind PATIENT; token cifrado con AES-GCM del Android Keystore en DataStore; expiración a las 8 h; cierre de sesión con confirmación; pantalla de Inicio; layout de dos paneles en tablet (WindowSizeClass); sin logs, y la contraseña solo en memoria. Navigation 3, DI manual, ProblemDetails en `core/network`, mensajes en `strings.xml`. Consume el contrato **0.2.1**.
- **Pruebas:** 121 JVM (MockWebServer) y 5 instrumentadas (Keystore, `connectedDebugAndroidTest`). La prueba manual del humano en el emulador (T19 y T20) salió OK.
- **Revisión:** APROBADA en la 3.ª ronda (las dos anteriores se rechazaron por la convención de nombres de prueba).
- **Lecciones:** (1) en Kotlin, los nombres de prueba usan `R<n> ` con espacio (`:` no es válido en la JVM; nunca caracteres parecidos como U+A789); ya está en CHECKPOINTS, conventions y docs/specs.md. (2) **Android 17 / targetSdk 37 bloquea la red local** (`10.0.2.2`, LAN) sin `ACCESS_LOCAL_NETWORK`; en desarrollo se usa `adb reverse tcp:3000 tcp:3000` + `API_BASE_URL=http://localhost:3000` (README).
- **Pendientes para F-02-mobile:** cambiar el valor por defecto de `API_BASE_URL` en `app/build.gradle.kts` (hoy `10.0.2.2`) a `http://localhost:3000` y actualizar `docs/architecture.md`.

## 2026-10-27 — F-02-mobile: pre-registro del paciente → done
- **Resultado:** "Mis datos" (`GET`/`PATCH /patient/profile`: documento no editable, PATCH solo con los campos cambiados, mayoría de edad en America/Bogota, bloqueo tras la verificación en recepción con 409); nuevo pre-registro (sedes de la IPS recordada, la recordada preseleccionada sin cambiarla al elegir otra; motivo escrito de hasta 2000 caracteres; `complaintSource=TYPED`, listo para F-08); estado del último pre-registro en el inicio (sede, texto de estado sin nivel, "Enviado" y "vence" solo en `PENDING`, nunca hora de llegada), que se refresca al mostrarse, al volver a primer plano y con "Actualizar". 401 → logout; 429 con Retry-After; solo rutas `api/v1/patient/` y `api/v1/public/` (prueba por reflexión); motivo y perfil solo en memoria y enmascarados en `toString()`. Dos paneles en tablet para el formulario. `API_BASE_URL` por defecto pasa a `http://localhost:3000` (pendiente de F-01). Nuevos `core/tenants`, `core/model`, `core/auth/PatientCall.kt`. Consume el contrato **0.4.0**.
- **Spec:** R1–R29; PA-M02-1..7 resueltas el 2026-10-27 aceptando las propuestas (textos de UI y de estado, solo el último pre-registro, refresco sin sondeo, la sede recordada no cambia, el pre-registro solo pide sede y motivo, 401 sin mensaje).
- **Pruebas:** 220 JVM (99 nuevas) en verde, lint sin issues. La prueba manual del humano en teléfono y tablet (T15, T16) salió OK.
- **Revisión:** APROBADA en la 1.ª ronda.
- **Correcciones del 2026-10-28 (pedidas por el humano, revisión APROBADA):** (1) el 422 de "Mis datos" usa una cadena propia, `error_underage_profile` = "Debes ser mayor de 18 años."; el alta de F-01 conserva `error_underage`. (2) R6: tras un 409, el aviso de bloqueo se mantiene aunque falle la recarga y se pulse "Reintentar" (nueva prueba R6).
- **Pendiente no bloqueante:** el `created` del repositorio es un `SharedFlow` sin réplica (un login nuevo en el mismo proceso no ve el 201 anterior; el GET lo cubre).
- **Para el roadmap de la API (no se escribe desde aquí):** F-02-mobile done (2026-10-27), consume 0.4.0.


## 2026-10-29 — F-01-staff-mobile: acceso del personal y sesión por principal
- **Autorización:** plan móvil revisado aprobado por el usuario mediante PLEASE IMPLEMENT THIS PLAN. Specs y ADR local 0002 materializadas antes del código; sin cambios de contrato, API, web o legacy. Base del equipo ffea8e0 conservada, incluido F-02 del paciente.
- **Resultado de implementación:** bienvenida Paciente/Personal, login STAFF, cuatro inicios y guards; sesión única Patient/Staff verificada mediante /me, token cifrado, restauración STAFF sin IPS, expiración y operaciones aisladas por generación. Se descartan logins, respuestas y 401 antiguos; se conservan los reintentos legítimos. El selector de nacimiento mantiene su estado privado fuera de savers internos de Material3. Funciones operativas STAFF pendientes de los siguientes bloques.
- **Verificación de código:** 262 pruebas JVM/38 clases, lint, assembleDebug e init del implementer y del reviewer verdes, FAIL 0/WARN 0. Commits de código: 86624b7, e25b4dd, 67e8dc7 y 665702b.
- **Dispositivo:** Android37 Google APIs rev8, renderizador Lavapipe; cuatro roles con destinos exactos, confirmación/logout y limpieza de campos, restauración de enfermería sin IPS y restauración ADMIN con APK final. Formulario conserva correo/password teléfono→tablet→teléfono; cambio de acceso los elimina; el foco permanece en password al corregir errores. Marco usable a320dp/texto200% con acciones inferiores alcanzables por scroll y tablet1280x800. XML/PNG locales en ../../local-analysis/mobile-verification/f01-staff/.
- **Límites:** pruebas funcionales con servidor sintético aislado3131 y cuentas ficticias; API/PostgreSQL reales no validados. No se atribuyen pruebas reales de TalkBack, voz ni Bundle exhaustivo a este bloque. Cinco idiomas/identidad/voz y la verificación ampliada corresponden a UI-01-mobile. Keystore baseline:5 pruebas instrumentadas API31 y API37; instrumentación del APK final en curso.
- **Cierre:** código revisado sin hallazgos abiertos; firma final C1–C8, instrumentación y actualización de metadata pendientes al registrar esta entrada.

- **Resultado de instrumentación final:** connectedDebugAndroidTest sobre APK665702b en Android37 terminó verde (17s); XML real:5 pruebas,0fallos/errores/omitidas. Log local staff-final-instrumented.log. El APK de referencia de este bloque queda fuera de Git en ../../local-analysis/mobile-verification/f01-staff/asclepio-f01-staff-debug.apk.

- **Cierre final:** revisión independiente **APROBADA**, C1–C8 y R1–R21 sin hallazgos abiertos. T1–T5 completadas; F-01-staff-mobile pasa a **done**. Informes completos y progreso vivo archivados fuera de Git en ../../local-analysis/mobile-verification/f01-staff/reports/. Los siguientes bloques siguen pending. Publicación de este bloque autorizada en main mediante avance rápido, sin sobrescribir historial del equipo.
