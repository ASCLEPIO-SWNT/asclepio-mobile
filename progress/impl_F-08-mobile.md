# F-08-mobile — Informe del implementer

Rama `feature/F-08-mobile`, base `0c3e510`. Contrato consumido 0.9.0 (`BuildConfig.CONSUMED_CONTRACT`), sin cambios de contrato. Registro por tarea en `progress/current/F-08-mobile.md`. El hallazgo de D8 con la guía recreada se resolvió con la enmienda aprobada el 2026-11-17 (T15).

## Archivos tocados

- Producción: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `AppContainer.kt`, `AppNavigation.kt`, `core/voice/OnDeviceRecognizer.kt` (nuevo), `feature/encounters/data/PreRegistrationsApi.kt`, `feature/encounters/ui/{PreRegistrationForm,PreRegistrationScreen,PreRegistrationViewModel}.kt`, `feature/voice/{AndroidOnDeviceRecognizer,SpanishOnDeviceSupport,DictationGuideExclusion}.kt` (nuevos), `feature/voice/ui/DictationControls.kt` (nuevo), `feature/voice/README.md`, `res/values{,-en,-pt,-fr,-de}/dictation.xml` (nuevos, 12 claves `dictation_*`).
- Pruebas: `core/voice/{DictationTypesTest,FakeOnDeviceRecognizer}.kt` (nuevos), `feature/voice/{SpanishOnDeviceSupportTest,OnDeviceOnlyTest,DictationStringsTest,DictationGuideExclusionTest}.kt` (nuevos), `feature/encounters/data/PreRegistrationsApiTest.kt`, `feature/encounters/ui/{PreRegistrationFormTest,PreRegistrationViewModelTest}.kt`.
- Documentación: `docs/architecture.md`, `docs/verification.md`, `specs/F-08-mobile/tasks.md` (T1–T12, T14 y T15 `[x]`), `progress/current/F-08-mobile.md`.

## Trazabilidad

| R | Archivo (`app/src/test/java/co/asclepio/mobile/…`) :: prueba |
|---|---|
| R1 | `feature/encounters/ui/PreRegistrationViewModelTest.kt` :: `R1 al abrir comprueba la disponibilidad sin iniciar escucha`; `R1 con espanol local disponible el dictado queda listo con ese idioma` · `feature/voice/SpanishOnDeviceSupportTest.kt` :: `R1 API 31 y 32 usan solo isOnDeviceRecognitionAvailable`; `R1 API 33 o mas consulta checkRecognitionSupport`; `R1 prefiere es-CO luego es luego otra variante es`; `R1 sin variante es instalada no hay idioma` |
| R2 | `feature/voice/OnDeviceOnlyTest.kt` :: `R2 solo AndroidOnDeviceRecognizer crea el reconocedor local y usa RecognizerIntent`; `R2 ninguna fuente usa createSpeechRecognizer actividades de reconocimiento grabacion ni descarga de modelos` |
| R3 | `PreRegistrationViewModelTest.kt` :: `R3 dictar con permiso inicia una escucha con el idioma elegido` · `SpanishOnDeviceSupportTest.kt` :: `R3 sin permiso la accion es solicitarlo y con permiso es escuchar` · `OnDeviceOnlyTest.kt` :: `R3 RECORD_AUDIO solo se solicita desde DictationControls` |
| R4 | `PreRegistrationViewModelTest.kt` :: `R4 permiso denegado muestra aviso conserva el motivo y no escucha`; `R4 error de permisos del reconocedor muestra el aviso de permiso` · `SpanishOnDeviceSupportTest.kt` :: `R4 el error de permisos se traduce a Permission` |
| R5 | `PreRegistrationViewModelTest.kt` :: `R5 durante la escucha no se inicia otra y no se puede enviar`; `R5 detener pasa a procesando y espera el resultado` |
| R6 | `PreRegistrationFormTest.kt` :: `R6 el dictado se anade al final separado por un espacio`; `R6 sin espacio extra si el motivo esta vacio o termina en espacio`; `R6 el dictado no trunca y un motivo de mas de 2000 da el error de longitud` · `PreRegistrationViewModelTest.kt` :: `R6 el resultado final se anade una sola vez y no se envia` |
| R7 | `PreRegistrationViewModelTest.kt` :: `R7 resultado vacio o error conserva motivo y origen con aviso`; `R7 error de idioma deja el dictado no disponible` · `SpanishOnDeviceSupportTest.kt` :: `R7 los errores de idioma se traducen a LanguageUnavailable` |
| R8 | `PreRegistrationFormTest.kt` :: `R8 aceptar dictado fija VOICE_ON_DEVICE`; `R8 dictado con edicion se envia como VOICE_ON_DEVICE`; `R8 borrar todo el motivo vuelve a TYPED y lo escrito despues sigue TYPED`; `R8 sin dictado el origen es TYPED` · `PreRegistrationViewModelTest.kt` :: `R8 tras el 201 el formulario nuevo vuelve a TYPED` |
| R9 | `feature/encounters/data/PreRegistrationsApiTest.kt` :: `R9 el POST con dictado lleva exactamente siteId chiefComplaintText y complaintSource VOICE_ON_DEVICE` |
| R10 | `PreRegistrationViewModelTest.kt` :: `R10 tras cancelar se ignoran resultados y errores tardios`; `R10 un resultado de una escucha anterior no se aplica a la nueva`; `R10 al limpiar el ViewModel se libera el reconocedor` |
| R11 | `PreRegistrationViewModelTest.kt` :: `R11 cancelar por rotacion conserva motivo y origen y exige pulsar Dictar de nuevo` |
| R12 | `core/voice/DictationTypesTest.kt` :: `R12 los estados y disponibilidades del dictado no contienen texto reconocido en toString`; `R12 NoDictation informa no disponible y no inicia escuchas` · `PreRegistrationViewModelTest.kt` :: `R12 el estado de la pantalla enmascara el motivo dictado en toString` · `OnDeviceOnlyTest.kt` :: `R12 los archivos del dictado no usan SavedStateHandle DataStore ni Log` |
| R13 | `feature/voice/DictationGuideExclusionTest.kt` :: `R13 tras recrear la guia Dictar detiene la guia actual` (D8 enmendado, T15; la cancelación cuando la guía habla está en la raíz: QA T13) |
| R14 | `feature/voice/DictationStringsTest.kt` :: `R14 los textos del dictado existen en los cinco idiomas`; `R14 DictationControls no tiene textos literales de UI` |
| R16 | `PreRegistrationViewModelTest.kt` :: `R16 sin reconocimiento local el dictado no esta disponible y se puede enviar por texto` |

Nota: en `PreRegistrationViewModelTest`, `PreRegistrationFormTest` y `PreRegistrationsApiTest` conviven pruebas `R<n>` de F-02-mobile (p. ej. `R8 muestra las sedes…`, `R10 el POST lleva… TYPED`); la tabla solo lista las de F-08.

## Resultados

- `./init.sh` (completo): `FAIL: 0 · WARN: 0` · `Estado: VERDE` (incluye `gradlew lint testDebugUnitTest`).
- `./gradlew lint testDebugUnitTest assembleDebugAndroidTest`: OK. 354 pruebas JVM, 48 clases, 0 fallos. Lint: 0 errores, 15 avisos preexistentes.
- T15 (enmienda D8, 2026-11-17): `./gradlew testDebugUnitTest --tests 'DictationGuideExclusionTest*' --tests 'PreRegistrationViewModelTest*'` OK (1 + 35). `./init.sh` completo: `FAIL: 0 · WARN: 0` · `Estado: VERDE`; 354 pruebas JVM en 49 clases, 0 fallos (+1 `R13 tras recrear la guia Dictar detiene la guia actual`, −1 `R13 iniciar el dictado detiene la guia antes de escuchar`).

## QA manual (T13): pendiente del humano

No ejecutada. Registrar aquí: emulador o dispositivo, versión de Android, APK/commit, API real o fixture y qué se ejercitó de verdad. Incluir el caso de D8 enmendado: rotar o cambiar idioma → iniciar la guía → cerrar el panel → "Dictar" (la guía se detiene).
