# F-08-mobile — Tareas

Sin migraciones. Orden estricto. En cada tarea el `implementer` escribe **primero la prueba** (nombre en backticks que empieza por `R<n> ` con espacio, nunca `:`), luego el código, y marca `[x]`. Las pruebas viven en `app/src/test/java/co/asclepio/mobile/...` y se ejecutan con `./gradlew testDebugUnitTest --tests '<Clase>*'`.

- [x] **T1 Contrato 0.9.0 y POST con dictado** (R9)
  - Archivos: `app/build.gradle.kts` (`CONSUMED_CONTRACT` → `"0.9.0"`), comentarios de versión en `feature/encounters/data/PreRegistrationsApi.kt` y `feature/encounters/ui/PreRegistrationForm.kt`.
  - Prueba primero en `feature/encounters/data/PreRegistrationsApiTest.kt`: `R9 el POST con dictado lleva exactamente siteId chiefComplaintText y complaintSource VOICE_ON_DEVICE` (MockWebServer: cuerpo JSON con tres claves exactas, sin audio, idioma ni horas).

- [x] **T2 Puerto core/voice y fake de pruebas** (R12)
  - Archivos: `core/voice/OnDeviceRecognizer.kt` (interfaz, `OnDeviceAvailability`, `DictationError`, `DictationUiState`, `NoDictation`); `src/test/.../core/voice/FakeOnDeviceRecognizer.kt`.
  - Prueba primero en `core/voice/DictationTypesTest.kt`: `R12 los estados y disponibilidades del dictado no contienen texto reconocido en toString`; `R12 NoDictation informa no disponible y no inicia escuchas`.

- [x] **T3 Funciones puras del formulario** (R6, R8)
  - Archivo: `feature/encounters/ui/PreRegistrationForm.kt` (`withDictation(text)`, `withTypedText(text)`).
  - Pruebas primero en `PreRegistrationFormTest.kt`: `R6 el dictado se anade al final separado por un espacio`, `R6 sin espacio extra si el motivo esta vacio o termina en espacio`, `R6 el dictado no trunca y un motivo de mas de 2000 da el error de longitud`, `R8 aceptar dictado fija VOICE_ON_DEVICE`, `R8 dictado con edicion se envia como VOICE_ON_DEVICE`, `R8 borrar todo el motivo vuelve a TYPED y lo escrito despues sigue TYPED`, `R8 sin dictado el origen es TYPED`.

- [x] **T4 ViewModel: disponibilidad y fallback** (R1, R16)
  - Archivo: `feature/encounters/ui/PreRegistrationViewModel.kt` (nuevo parámetro `recognizer: OnDeviceRecognizer = NoDictation` y `beforeListen: () -> Unit = {}`; campo `dictation` en `PreRegistrationUiState`).
  - Pruebas primero en `PreRegistrationViewModelTest.kt`: `R1 al abrir comprueba la disponibilidad sin iniciar escucha`, `R1 con espanol local disponible el dictado queda listo con ese idioma`, `R16 sin reconocimiento local el dictado no esta disponible y se puede enviar por texto`. Las pruebas existentes de F-02 siguen en verde sin cambios de firma.

- [x] **T5 ViewModel: iniciar, permiso y una sola escucha** (R3, R4, R5, R13)
  - Archivo: `PreRegistrationViewModel.kt` (`startDictation`, `onDictationPermissionDenied`, `canSubmit`).
  - Pruebas primero: `R3 dictar con permiso inicia una escucha con el idioma elegido`, `R4 permiso denegado muestra aviso conserva el motivo y no escucha`, `R5 durante la escucha no se inicia otra y no se puede enviar`, `R13 iniciar el dictado detiene la guia antes de escuchar`.

- [x] **T6 ViewModel: resultados, errores, detener y cancelar** (R6, R7, R8, R10, R16)
  - Archivo: `PreRegistrationViewModel.kt` (`stopDictation`, `cancelDictation`, generación de escucha, traducción a avisos).
  - Pruebas primero: `R6 el resultado final se anade una sola vez y no se envia`, `R7 resultado vacio o error conserva motivo y origen con aviso`, `R7 error de idioma deja el dictado no disponible`, `R4 error de permisos del reconocedor muestra el aviso de permiso`, `R5 detener pasa a procesando y espera el resultado`, `R10 tras cancelar se ignoran resultados y errores tardios`, `R10 un resultado de una escucha anterior no se aplica a la nueva`, `R8 tras el 201 el formulario nuevo vuelve a TYPED`.

- [ ] **T7 ViewModel: liberación y memoria** (R10, R11, R12)
  - Archivo: `PreRegistrationViewModel.kt` (`onCleared` → `release()`).
  - Pruebas primero: `R10 al limpiar el ViewModel se libera el reconocedor` (vía `ViewModelStore.clear()`), `R11 cancelar por rotacion conserva motivo y origen y exige pulsar Dictar de nuevo`, `R12 el estado de la pantalla enmascara el motivo dictado en toString`.

- [ ] **T8 Funciones puras de feature/voice** (R1, R2, R7, R3)
  - Archivo: `feature/voice/SpanishOnDeviceSupport.kt` (`recognitionStrategy(sdkInt)`, `pickSpanishTag(installed)`, `mapRecognizerError(code)`, `dictationAction(hasPermission)`).
  - Pruebas primero en `feature/voice/SpanishOnDeviceSupportTest.kt`: `R1 API 31 y 32 usan solo isOnDeviceRecognitionAvailable`, `R1 API 33 o mas consulta checkRecognitionSupport`, `R1 prefiere es-CO luego es luego otra variante es`, `R1 sin variante es instalada no hay idioma`, `R7 los errores de idioma se traducen a LanguageUnavailable`, `R4 el error de permisos se traduce a Permission`, `R3 sin permiso la accion es solicitarlo y con permiso es escuchar`.

- [ ] **T9 Adaptador Android y manifiesto** (R1, R2, R12)
  - Archivos: `feature/voice/AndroidOnDeviceRecognizer.kt` (comprueba disponibilidad antes de crear, hilo principal, listener antes de comandos, `destroy()` al terminar, un reconocedor por escucha, intent con `EXTRA_PARTIAL_RESULTS=false`, sin `triggerModelDownload`); `app/src/main/AndroidManifest.xml` (`<queries>` con `android.speech.RecognitionService`).
  - Prueba primero en `OnDeviceOnlyTest.kt` (estática sobre `src/main`): `R2 solo AndroidOnDeviceRecognizer crea el reconocedor local y usa RecognizerIntent`, `R2 ninguna fuente usa createSpeechRecognizer actividades de reconocimiento grabacion ni descarga de modelos`, `R12 los archivos del dictado no usan SavedStateHandle DataStore ni Log`.

- [ ] **T10 Controles de dictado y textos** (R3, R4, R5, R14, R16)
  - Archivos: `feature/voice/ui/DictationControls.kt` (botones, estado con `liveRegion`, nota "solo español", aviso de no disponible, solicitud de `RECORD_AUDIO` al pulsar con `dictationAction`); `res/values*/` claves `dictation_*` en los cinco idiomas.
  - Pruebas primero: `DictationStringsTest.kt` `R14 los textos del dictado existen en los cinco idiomas`; ampliar `OnDeviceOnlyTest` con `R3 RECORD_AUDIO solo se solicita desde DictationControls`. `PatientOnlyResourcesTest` sigue en verde (mismas claves en los cinco catálogos).

- [ ] **T11 Composición en la raíz y pantalla** (R5, R10, R11, R13)
  - Archivos: `AppContainer.kt` (fábrica `newOnDeviceRecognizer()` con el contexto de aplicación), `AppNavigation.kt` (VM con `recognizer` y `beforeListen = guide::stop`; slot `dictation`; `cancelDictation()` en `ON_STOP`, en `onDispose` de la entrada y cuando `guide.state` es `Speaking`), `feature/encounters/ui/PreRegistrationScreen.kt` (slot `dictation` en el panel del motivo).
  - Verificación: `PatientOnlyScopeTest`, `PatientOnlyNavigationTest`, `PatientOnlyResourcesTest` y `PreRegistrationViewModelTest` en verde; comportamiento de ciclo de vida y guía en T13.

- [ ] **T12 Documentación del repo** (R2, R12)
  - Archivos: `docs/architecture.md` (`core/voice/` y contenido de `feature/voice/`), `docs/verification.md` (sección F-08: JVM con fake frente a dispositivo), `feature/voice/README.md`.
  - Verificación: `PatientOnlyDocsTest` en verde.

- [ ] **T13 QA manual en emulador y dispositivo** (R1–R14, R16)
  - `adb reverse tcp:3000 tcp:3000` y `API_BASE_URL=http://localhost:3000` (Android 17 bloquea la red local), API local levantada.
  - Emulador de teléfono y de tablet (API 37): fallback sin reconocimiento local (aviso, sin "Dictar", envío por texto); permiso denegado y denegado de nuevo (aviso, sin bucle); cinco idiomas con la nota de "solo español"; TalkBack en los controles.
  - Dispositivo con español local (API 33+ y, si lo hay, 31–32): dictado en modo avión, edición, envío con `VOICE_ON_DEVICE` (comprobar en la web de recepción o en la base local); borrar todo y escribir → `TYPED`; Detener y Cancelar; rotación y segundo plano durante la escucha; abrir la guía durante la escucha.
  - Registrar en `progress/impl_F-08-mobile.md` emulador/dispositivo, versión de Android, APK/commit, API real o fixture y qué se ejercitó de verdad; sin modelo local no se declara transcripción real.

- [ ] **T14 Cierre: `./init.sh` en verde y trazabilidad** (R1–R14, R16)
  - `./gradlew lint testDebugUnitTest assembleDebugAndroidTest` y `./init.sh` completo en verde.
  - Tabla `R<n>` → `archivo::prueba` en `progress/impl_F-08-mobile.md` (`grep -rnE '`R[0-9]+ ' app/src/test app/src/androidTest`); todos los `R<n>` vigentes tienen al menos una prueba.
