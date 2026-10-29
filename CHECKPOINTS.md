# CHECKPOINTS — Evaluación del estado final (asclepio-mobile)

> El `reviewer` recorre cada casilla, marca `[x]` o `[ ]` con evidencia y rechaza el cierre si queda alguna vacía en los checkpoints que aplican.

## C1 — El harness está completo
- [ ] Existen `AGENTS.md`, `CLAUDE.md`, `init.sh` (ejecutable), `feature_list.json`, `progress/current/` y `progress/history.md`.
- [ ] Existen `docs/architecture.md`, `docs/conventions.md`, `docs/specs.md` y `docs/verification.md`.
- [ ] `./init.sh` termina con exit code 0.

## C2 — El estado es coherente
- [ ] Como mucho una feature `in_progress`.
- [ ] Toda feature `done` tiene pruebas asociadas que pasan.
- [ ] `progress/current/` contiene solo el archivo de la feature activa (o nada).

## C3 — El código respeta la arquitectura
- [ ] Los paquetes siguen `docs/architecture.md`: `core/{network,auth,ui,model,tenants}` y `feature/{account,staffaccount,patients,encounters,status,voice}`.
- [ ] Hay MVVM: los `@Composable` no llaman a Retrofit; los ViewModel exponen `StateFlow<UiState>`.
- [ ] Una `feature/*` no importa de otra `feature/*`.
- [ ] No hay dependencias nuevas sin justificación en el `design.md`.
- [ ] No hay `Log.d` ni `println` de depuración, ni TODOs sin contexto.

## C4 — La verificación es real
- [ ] Hay pruebas unitarias (JUnit) por ViewModel o repositorio con lógica, y el HTTP se prueba contra un servidor falso que sigue el contrato (MockWebServer o similar), sin mockear Retrofit.
- [ ] `./gradlew testDebugUnitTest` muestra más de 0 pruebas, todas en verde.
- [ ] `./gradlew lint` no tiene errores.

## C5 — La sesión se cerró bien
- [ ] No hay archivos sin trackear sospechosos (`build/`, `.gradle/` y `local.properties` en `.gitignore`).
- [ ] `progress/history.md` tiene una entrada por la última sesión.
- [ ] La última feature trabajada tiene el estado correcto.

## C6 — Spec-Driven Development
- [ ] Toda feature `sdd: true` en `spec_ready`, `in_progress` o `done` tiene `specs/<id>/{requirements,design,tasks}.md`.
- [ ] `requirements.md` usa EARS estricto y cada `R<n>` está trazado a un `F-0X.ACn`.
- [ ] Toda feature `done` con `sdd: true` tiene todas sus tareas `[x]`.
- [ ] Cada `R<n>` está cubierto por al menos una prueba cuyo nombre empieza por `R<n> ` (en Kotlin el `:` no es válido en nombres de método JVM, así que se usa un espacio: ``fun `R1 crea paciente NN sin documento`()``). Se comprueba con `grep -rnE '`R[0-9]+ ' app/src/test app/src/androidTest`.

## C7 — Seguridad clínica y privacidad (UI)
- [ ] El audio no sale del dispositivo: solo se usa `SpeechRecognizer` en modo local y no hay código que suba audio.
- [ ] Si no hay reconocimiento local, la app funciona solo con texto.
- [ ] `complaint_source` es `VOICE_ON_DEVICE` solo cuando el texto viene del dictado.
- [ ] No hay datos personales en `Logcat`.
- [ ] El tráfico HTTP en claro está permitido solo en `debug` (`network_security_config` en el sourceSet de debug).
- [ ] La app no envía horas legales (la hora de llegada la fija el servidor cuando recepción confirma).
- [ ] Cada principal solo monta sus features autorizadas: PATIENT usa sus propios datos/APIs paciente/públicas; STAFF usa su rol verificado y guard previo al destino/VM (ADR local 0002).
- [ ] El token no basta para abrir contenido: `/me` valida restauración/login; un STAFF no necesita IPS paciente y un PATIENT exige tenant recordado coincidente.
- [ ] Logout/cambio/expiración/401 vigente invalidan generación y ámbito privado; Bearer es inmutable por operación y respuestas/401 antiguos no afectan otra sesión.
- [ ] Persistencia mantiene únicamente token cifrado/expiración e IPS/sede públicas; identidad/rol y borradores privados no se guardan ni se serializan en navegación.
- [ ] El alta de la cuenta muestra y registra el consentimiento de tratamiento de datos (cuando se defina PA-PAC-5).

## C8 — Contrato
- [ ] La feature declara `consumes_contract` y esa versión coincide (misma mayor) con `../asclepio-api/openapi/asclepio-api.v1.json`.
- [ ] Los DTOs de Kotlin corresponden a los esquemas del contrato declarado.
- [ ] Los errores `problem+json` se traducen a mensajes comprensibles.
