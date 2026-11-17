# Review F-08-mobile — Dictado del motivo en el dispositivo

Reviewer independiente, 2026-11-17. Rama `feature/F-08-mobile`, diff `0c3e510..HEAD` (16 commits, `89eb1bf`..`458c703`): T1–T12, T14 y T15. Árbol limpio, sin archivos sin trackear.

## Veredicto: APROBADO (el paso a `done` depende de la QA manual de T13, que sigue pendiente del humano)

El código, las pruebas, los recursos y la documentación cumplen R1–R14 y R16 con D8 enmendado. `./init.sh` completo sale en VERDE. T13 (QA manual en emulador/dispositivo) sigue `[ ]` y está fuera del alcance de esta revisión. Según C6, el leader no debe marcar `done` hasta que el humano la ejecute y la marque `[x]`.

## Ejecución

- `./init.sh` (completo, sin `--quick`): `FAIL: 0 · WARN: 0 · Estado: VERDE`, exit 0. Incluye `gradlew lint testDebugUnitTest`.
- `app/build/test-results/testDebugUnitTest/*.xml` de esta ejecución: 354 pruebas, 0 fallos, 0 errores, 0 omitidas. Pasan `DictationTypesTest` 2, `PreRegistrationFormTest` 12, `PreRegistrationViewModelTest` 35, `PreRegistrationsApiTest` 6, `DictationGuideExclusionTest` 1, `DictationStringsTest` 2, `OnDeviceOnlyTest` 4, `SpanishOnDeviceSupportTest` 7 y `PatientOnlyScopeTest`/`NavigationTest`/`ResourcesTest`/`DocsTest` (3/7/4/3).
- Lint (`app/build/reports/lint-results-debug.xml`): 0 errores y 15 avisos, todos preexistentes. Ninguno está en archivos de F-08.

## Comprobaciones específicas

1. **ADR 0006 / R2.**
   - `SpeechRecognizer.createOnDeviceSpeechRecognizer` solo aparece en `feature/voice/AndroidOnDeviceRecognizer.kt:117`, y siempre va detrás de `serviceAvailable()` (`:113`, `isOnDeviceRecognitionAvailable`). Si falla la creación, `runCatching` la trata como no disponible.
   - En `src/main` no aparecen `createSpeechRecognizer(`, `startActivity`, `MediaRecorder`, `AudioRecord`, `triggerModelDownload` ni `EXTRA_PREFER_OFFLINE`. Lo comprobé con grep del diff y lo fija `OnDeviceOnlyTest`.
   - El `Intent(ACTION_RECOGNIZE_SPEECH)` (`:124-129`) solo se pasa a `startListening`/`checkRecognitionSupport`. Lleva `EXTRA_PARTIAL_RESULTS=false` y `EXTRA_MAX_RESULTS=1`.
   - El listener se registra antes de cada comando (`:66` antes de `:67`, `:91` antes de `:92`).
   - Hay `destroy()` al terminar (`finish`, `:52`/`:78`), al cancelar (`:104`) y al liberar (`:109`).
   - Cada escucha usa un reconocedor nuevo. `start` cancela antes el anterior (`:72`).
   - Los callbacks llegan por `context.mainExecutor` (`:67`) o por el listener (hilo principal). El VM se construye en la composición.
2. **Ramas por SDK.**
   - `recognitionStrategy` (`SpanishOnDeviceSupport.kt:16-17`) devuelve `CheckSupport` solo con `SDK_INT >= TIRAMISU`.
   - `checkRecognitionSupport` vive en un método `@RequiresApi(TIRAMISU)` (`AndroidOnDeviceRecognizer.kt:44`).
   - API 31–32 devuelve `Available("es-CO")`. El error de idioma al escuchar pasa a `Unavailable` (VM `onDictationError`).
   - `@SuppressLint("NewApi")` (`:33-34`) lleva un comentario que justifica por qué lint no puede inferir el guard. Es aceptable.
   - Solo se usan los `installedOnDeviceLanguages` (`:59`). No hay `triggerModelDownload` (D2).
3. **PA-F08-1 / R8.**
   - `withDictation` fija `VOICE_ON_DEVICE` (`PreRegistrationForm.kt:32-36`).
   - `withTypedText` solo vuelve a `TYPED` si `text.isBlank()` (`:42-43`). `updateComplaint` lo usa.
   - El formulario nuevo y el que queda tras el 201 son `PreRegistrationForm()`, con `TYPED`. Lo prueban `R8 tras el 201 el formulario nuevo vuelve a TYPED` y `R8 borrar todo…`.
   - Cancelar, un error o un resultado vacío no cambian el origen (`R7 …`, `R10 …`, `R11 …`).
4. **PA-F08-2 / R16.**
   - Sin servicio, sin variante `es`, con un error de la comprobación o un error de idioma, el estado pasa a `Unavailable`.
   - `DictationControls.kt:59` oculta "Dictar" y muestra `dictation_unavailable`.
   - El envío por texto sigue disponible, como prueba `R16 sin reconocimiento local…` con un POST `TYPED` real contra MockWebServer.
   - Con el permiso denegado aparece el aviso y no se escucha (`R4 …`).
   - `NoDictation` es el valor por defecto.
5. **R3/R4.**
   - `RECORD_AUDIO` solo se pide desde `DictationControls.kt:51,65`, al pulsar el botón (`dictationAction`). No se pide al abrir la pantalla.
   - `OnDeviceOnlyTest` `R3 …` comprueba que nadie más llama a `RequestPermission`/`requestPermissions(`.
6. **R10/R11 (callbacks tardíos y liberación).**
   - La generación (`PreRegistrationViewModel.kt` `listenGeneration` + `isListening`) descarta los resultados y errores de escuchas canceladas o sustituidas.
   - `onCleared` incrementa la generación y llama a `release()`.
   - En la raíz: `LifecycleEventEffect(ON_STOP)` y `DisposableEffect(vm){ onDispose }` llaman a `cancelDictation()` (`AppNavigation.kt` en el bloque del pre-registro, junto a `:302`). Lo hacen al salir, al rotar, al cambiar idioma o tema y al cambiar de sesión.
   - El adaptador también ignora los callbacks de un reconocedor que ya no es el vigente (`listening === recognizer`).
7. **R12.**
   - El texto reconocido solo pasa a `form` y nunca a `DictationUiState` (son `data object`).
   - `PreRegistrationForm.toString` enmascara el motivo (`PreRegistrationForm.kt:24`).
   - No hay `SavedStateHandle`, DataStore, `SharedPreferences`, `Log.`, `println`, `rememberSaveable` ni escritura de archivos en `core/voice/`, `feature/voice/` ni en el VM (`OnDeviceOnlyTest` R12 y grep propio).
   - Ningún `Log`, `TODO` ni `println` nuevo en el diff.
8. **R13 con D8 enmendado.**
   - `onStart = { startDictationStoppingGuide(guide::stop, vm::startDictation) }` (`AppNavigation.kt:323`) se reconstruye en cada composición con la guía vigente.
   - `LaunchedEffect(vm, guide)` (`:302-303`) cancela la escucha cuando la guía pasa a `Speaking`.
   - Ya no queda `beforeListen` en `app/src`, `docs` ni `AGENTS.md` (grep vacío).
   - La guía solo narra recursos de `GuideDestination` y no recibe el motivo.
9. **Alcance solo paciente.** Ningún `PatientOnly*Test` cambia en el diff y todos pasan. No hay paquetes ni rutas de personal. `feature/encounters` solo importa `core/voice` y `feature/voice` no importa `feature/encounters`, como confirma el grep de imports. Solo la raíz conoce los dos.
10. **Contrato (C8).**
    - `feature_list.json` declara `consumes_contract: "0.9.0"` y el snapshot está en `info.version = 0.9.0`.
    - `CreatePreRegistrationDto`: `required [siteId, chiefComplaintText, complaintSource]` y `complaintSource` enum `TYPED | VOICE_ON_DEVICE`. Coincide con el DTO manual.
    - `BuildConfig.CONSUMED_CONTRACT = "0.9.0"` (`app/build.gradle.kts:29`).
    - `R9 el POST con dictado…` comprueba con MockWebServer exactamente tres claves.

## Checkpoints

### C1 — Harness
- [x] `AGENTS.md`, `CLAUDE.md`, `init.sh` (ejecutable), `feature_list.json`, `progress/current/` y `progress/history.md` existen (init: "Archivos base presentes", "init.sh ejecutable").
- [x] `docs/architecture.md`, `docs/conventions.md`, `docs/specs.md` y `docs/verification.md` existen (init sección 2). `architecture.md` y `verification.md` están actualizados para F-08.
- [x] `./init.sh` termina con exit 0 (VERDE).

### C2 — Estado
- [x] Una sola feature `in_progress` (F-08-mobile; init: "done: 5, in_progress: 1").
- [x] Las features `done` tienen pruebas en verde (354/354).
- [x] `progress/current/` contiene solo `F-08-mobile.md`.

### C3 — Arquitectura
- [x] Paquetes `core/voice` (nuevo, en `docs/architecture.md`) y `feature/voice`. Nada de personal, como confirman `PatientOnlyResourcesTest` y `PatientOnlyNavigationTest` en verde.
- [x] MVVM: `DictationControls` no llama a Retrofit y el VM expone `StateFlow<PreRegistrationUiState>`.
- [x] Ninguna `feature/*` importa otra `feature/*` (grep de imports vacío). Las conecta la raíz.
- [x] Sin dependencias nuevas (solo `CONSUMED_CONTRACT` en `build.gradle.kts`).
- [x] Sin `Log.d`, `println` ni TODOs nuevos (grep del diff).

### C4 — Verificación
- [x] Pruebas JVM del VM con `FakeOnDeviceRecognizer`. HTTP con MockWebServer y Retrofit real (`PreRegistrationsApiTest`, `PatientTestEnv`).
- [x] `testDebugUnitTest`: 354 pruebas, todas en verde.
- [x] `lint`: 0 errores.

### C5 — Cierre de sesión
- [x] Sin archivos sin trackear (`git ls-files --others --exclude-standard` vacío).
- [ ] `progress/history.md` sin entrada de F-08 todavía. La escribe el leader al cerrar (AGENTS.md §5). No bloquea la revisión.
- [x] F-08-mobile sigue `in_progress`, lo correcto con T13 pendiente.

### C6 — SDD
- [x] `specs/F-08-mobile/{requirements,design,tasks}.md` presentes.
- [x] EARS con trazas `→ F-08.ACn` en R1–R14 y R16 (R15 retirado y justificado).
- [ ] Todas las tareas `[x]`: falta **T13** (QA manual del humano). T1–T12, T14 y T15 están `[x]`.
- [x] Cada R vigente tiene al menos una prueba `R<n> ` en verde: R1 6, R2 2, R3 3, R4 3, R5 2, R6 4, R7 3, R8 5, R9 1, R10 3, R11 1, R12 4, R13 1, R14 2, R16 1. Ningún nombre lleva `:`.

### C7 — Seguridad clínica y privacidad
- [x] El audio no sale del dispositivo: solo `createOnDeviceSpeechRecognizer`, sin grabación ni subida (punto 1).
- [x] Sin reconocimiento local, la app funciona solo con texto (punto 4).
- [x] `VOICE_ON_DEVICE` solo tras un resultado aceptado del dictado (punto 3).
- [x] Sin datos personales en Logcat (sin `Log` en el dictado; `toString` enmascarado).
- [x] El HTTP en claro solo en debug no cambia con este diff.
- [x] Sin horas legales en el POST (`R9`/`R22` comprueban tres claves exactas).
- [x] Solo paciente: `PatientOnlyScopeTest` en verde y sin endpoints nuevos.
- [x] La validación del token y de `/me` no cambia con este diff.
- [x] La invalidación de sesión no cambia. Un cambio de sesión cancela la escucha (`key(navigationIdentity)` → `onDispose`).
- [x] Persistencia: el motivo y el texto reconocido solo se guardan en la RAM del VM (R12).
- [x] Consentimiento (PA-PAC-5): no aplica a F-08.

### C8 — Contrato
- [x] `consumes_contract` 0.9.0 = snapshot 0.9.0.
- [x] DTOs Kotlin = `CreatePreRegistrationDto` / `PatientPreRegistrationDto`.
- [x] Los `problem+json` siguen traducidos por las rutas de F-02, sin cambios (`PreRegistrationErrorMessagesTest` en verde).

## Observaciones (no bloquean)

1. `DictationGuideExclusionTest.kt:43-44`: `firstOnStart` se declara con `@Suppress("UNUSED_VARIABLE")` y nunca se invoca. Por eso "no toca la guía anterior" se cumple por construcción. La garantía real, que la raíz reconstruye el callback en cada composición, solo se puede comprobar en la QA de T13 con el caso "rotar → iniciar guía → cerrar panel → Dictar". Se podría quitar la variable muerta.
2. La cancelación cuando la guía pasa a `Speaking` (`AppNavigation.kt:302-303`) no tiene prueba JVM. Está anotada en la tabla de trazabilidad como QA de T13 y tiene que estar en esa QA.
3. Los números `R<n>` de F-08 coinciden con los de F-02 en las mismas clases (`R8`, `R10`, `R12`, `R13`, `R16`). Es la convención del repo y `impl_F-08-mobile.md` lo aclara.
4. `cancelDictation()` en `onDispose` muestra `dictation_cancelled` también al salir de la pantalla. Es inocuo, porque el aviso solo se ve si se vuelve a esa entrada.

## Pendiente para `done`

- T13 (humano): registrar en `progress/impl_F-08-mobile.md` el emulador o dispositivo, la versión de Android, el commit, la API local o fixture y qué se ejercitó. Incluir el fallback sin modelo local, el permiso denegado dos veces, los cinco idiomas, TalkBack, teléfono y tablet, rotación o segundo plano durante la escucha y el caso de D8 enmendado. Sin modelo local no se declara transcripción real.
- El leader mueve el resumen a `progress/history.md` y borra `current/`, `impl_` y `review_`.
