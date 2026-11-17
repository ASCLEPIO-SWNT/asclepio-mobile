# F-08-mobile — Diseño

## Resumen

El paciente puede dictar el motivo de su pre-registro en español con el reconocedor **local** de Android; el texto se añade al motivo, se puede editar y se envía con `complaintSource = VOICE_ON_DEVICE`. Si no hay reconocimiento local en español o se deniega el micrófono, el formulario sigue siendo solo de texto, como en F-02-mobile.

Capas (según `docs/architecture.md`; la app es solo del paciente, ADR 0008):

| Paquete | Qué se añade o cambia |
|---|---|
| `core/voice/` (nuevo) | Puerto mínimo `OnDeviceRecognizer`, los tipos `OnDeviceAvailability`, `DictationError`, `DictationUiState` y la implementación nula `NoDictation`. Sin dependencias Android. |
| `feature/voice/` | `AndroidOnDeviceRecognizer` (adaptador de `SpeechRecognizer`), `SpanishOnDeviceSupport.kt` (funciones puras: rama por SDK, elección de idioma y traducción de códigos de error), `DictationGuideExclusion.kt` (función pura `startDictationStoppingGuide`, D8) y `ui/DictationControls.kt` (botones, estado y solicitud de `RECORD_AUDIO`). |
| `feature/encounters/` | `PreRegistrationViewModel` gestiona el estado del dictado y el origen del motivo; `PreRegistrationForm.kt` añade dos funciones puras (`withTypedText`, `withDictation`); `PreRegistrationScreen` recibe un slot `dictation` y bloquea "Enviar" durante la escucha. |
| Raíz | `AppContainer` crea el adaptador; `AppNavigation` compone `DictationControls` en el slot, cancela la escucha en `ON_STOP`, al salir de la entrada y cuando la guía habla, y en el callback de "Dictar" detiene la guía actual antes de llamar al ViewModel (D8). |

`feature/encounters` y `feature/voice` solo dependen de `core/voice`; ninguna importa a la otra. Solo la raíz conoce las dos (mismo patrón que el slot de estado de F-02-mobile, D3).

### Puerto (core/voice)

```kotlin
interface OnDeviceRecognizer {
    fun checkAvailability(onResult: (OnDeviceAvailability) -> Unit)
    fun start(languageTag: String, onFinal: (String) -> Unit, onError: (DictationError) -> Unit)
    fun stop()      // stopListening: espera el resultado
    fun cancel()    // cancel + destroy; no entrega más callbacks
    fun release()   // igual que cancel; idempotente
}
sealed interface OnDeviceAvailability { data class Available(val languageTag: String) : OnDeviceAvailability; data object Unavailable : OnDeviceAvailability }
enum class DictationError { LanguageUnavailable, Permission, NoMatch, Busy, Other }
sealed interface DictationUiState { Checking, Unavailable, Idle, Listening, Processing }  // sin texto reconocido
```

El ViewModel se prueba en `testDebugUnitTest` con un `FakeOnDeviceRecognizer` en `src/test` que registra las llamadas y permite disparar `onFinal`/`onError`. No se usa Robolectric (el repo no lo usa).

## Plataforma y detección (R1, R2, R16)

Línea base del repo: `compileSdk 37`, `targetSdk 37` (Android 17), `minSdk 31` (ADR 0001).

- **API 31–32:** solo existen `isOnDeviceRecognitionAvailable(context)` y `createOnDeviceSpeechRecognizer(context)`. Si el primero devuelve `true` se considera `Available("es-CO")`; el idioma no instalado se detecta al escuchar por `ERROR_LANGUAGE_NOT_SUPPORTED` o `ERROR_LANGUAGE_UNAVAILABLE` (API 31), que el ViewModel convierte en `Unavailable` (R7, R16).
- **API 33+:** si `isOnDeviceRecognitionAvailable` es `true`, se crea el reconocedor local, se registra un listener y se llama a `checkRecognitionSupport(intent, mainExecutor, callback)`. Con `RecognitionSupport.installedOnDeviceLanguages` se elige el idioma con `pickSpanishTag`: `es-CO` si está; si no, `es`; si no, la primera variante `es-*` en el orden recibido; si no hay ninguna, `Unavailable`. `onError` del callback también es `Unavailable`. Solo cuentan los idiomas **instalados** (no `pending`, `supported` ni `online`).
- La rama se decide con `recognitionStrategy(sdkInt)` (`ServiceOnly` para 31–32, `CheckSupport` para 33+), función pura probada en la JVM; el adaptador la llama con `Build.VERSION.SDK_INT`.
- `createOnDeviceSpeechRecognizer` lanza `UnsupportedOperationException` si `isOnDeviceRecognitionAvailable` es `false`: el adaptador nunca lo llama sin esa comprobación y, además, captura la excepción como `Unavailable`. Se llama siempre en el hilo principal (los métodos del ViewModel se invocan desde la UI y el adaptador usa `context.mainExecutor`). `setRecognitionListener` se llama antes de `checkRecognitionSupport`/`startListening`, y `destroy()` al cancelar, liberar o terminar.
- `startListening` recibe un `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)` con `EXTRA_LANGUAGE_MODEL = FREE_FORM`, `EXTRA_LANGUAGE = <tag>`, `EXTRA_PARTIAL_RESULTS = false` y `EXTRA_MAX_RESULTS = 1`. Ese intent nunca se lanza como actividad.
- Se crea un reconocedor nuevo por escucha y se destruye al terminar: así cancelar o salir siempre libera el micrófono y no quedan callbacks colgados.
- `AndroidManifest.xml` añade a `<queries>` el intent `android.speech.RecognitionService` (visibilidad de paquetes desde Android 11). `RECORD_AUDIO` ya está declarado.

## Decisiones

| # | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D1 | Solo `createOnDeviceSpeechRecognizer`, con ramas por `SDK_INT` (31–32 frente a 33+) y fallback a texto. | `createSpeechRecognizer` + `EXTRA_PREFER_OFFLINE`, o `RecognizerIntent` en una actividad externa. | "Prefer offline" es una preferencia y la actividad externa puede usar la nube: el audio podría salir del teléfono (ADR 0006, ADR 0001). |
| D2 | **No** llamar a `triggerModelDownload`; sin idioma instalado se cae a texto. | Pedir la descarga del modelo de español desde la app (API 33+). | El brief (F-08.AC1) pide ocultar o deshabilitar el dictado si no hay reconocimiento local, no instalarlo. Es más simple, no depende de la red ni de una UI del sistema y en API 31–32 no existe. El paciente puede instalar el idioma desde los ajustes del sistema y reabrir la pantalla. |
| D3 | El ViewModel del pre-registro es dueño del estado del dictado y del origen; el puerto solo entrega texto. | Un `DictationController` o coordinador de modalidades aparte. | Menos piezas; el formulario y su origen ya viven en ese ViewModel (F-02-mobile) y así se prueban juntos con un fake. |
| D4 | Un contador `listenGeneration` en el ViewModel: cada `start` lo incrementa y los callbacks solo se aplican si coinciden y el estado es `Listening`/`Processing`. | Confiar en que el adaptador nunca entregue callbacks tardíos. | Impide insertar un resultado viejo tras cancelar, rotar o reiniciar la escucha (R10, R11). |
| D5 | Origen pegajoso con una sola regla de vuelta: `VOICE_ON_DEVICE` al aceptar un resultado; `TYPED` solo si una edición deja el motivo en blanco. | Calcular un porcentaje de texto dictado o volver a `TYPED` con cualquier edición. | Propuesta de PA-F08-1: coincide con el supuesto del brief y se prueba con casos simples. |
| D6 | Sin resultados parciales: solo se pide y se usa el resultado final. | Mostrar parciales mientras se habla. | Menos estado en RAM y nada que limpiar (R12). |
| D7 | Sin idioma de reconocimiento disponible, se **oculta** "Dictar" y se muestra un aviso. | Botón deshabilitado. | F-08.AC1 admite ambas; ocultar evita un control inútil para TalkBack y el aviso explica por qué. |
| D8 | Guía y dictado se excluyen sin clase nueva y sin que el ViewModel conozca la guía: la raíz (la composición que posee la guía) detiene la guía **actual** en el callback de "Dictar" antes de llamar a `vm.startDictation()`, mediante la función pura `startDictationStoppingGuide(stopGuide, startDictation)` de `feature/voice/` construida en cada composición con la guía vigente; y la raíz cancela el dictado cuando `guide.state` pasa a `Speaking`. El ViewModel **no** recibe `beforeListen`. *(Enmienda 2026-11-17.)* | Un `AudioModeCoordinator` común; pasar `beforeListen = guide::stop` al ViewModel (versión anterior de D8); guía de ámbito de aplicación en `AppContainer`. | La guía se recrea al rotar o cambiar de idioma y el ViewModel sobrevive: una referencia a la guía dentro del ViewModel queda ligada a la primera composición y "Dictar" dejaría de detener la guía vigente (contradice R13). El callback se reconstruye con cada composición, así que siempre ve la guía actual. La guía solo narra recursos fijos de `GuideDestination` y no puede leer el motivo. Mover la guía a `AppContainer` sale del alcance de F-08. |
| D9 | La solicitud de `RECORD_AUDIO` vive en `DictationControls` (`rememberLauncherForActivityResult(RequestPermission)`) y la decisión es una función pura `dictationAction(hasPermission)`. | Pedir el permiso desde el ViewModel o al arrancar. | El permiso es de la UI; la función pura permite probar R3 en la JVM. Un resultado tardío tras salir no llega (el launcher se desregistra) y, si llega, el ViewModel solo arranca desde `Idle`. |

## Flujo en el ViewModel

1. `init`: estado `Checking`; `checkAvailability` → `Idle` (guardando el `languageTag`) o `Unavailable`.
2. "Dictar" (con permiso): la raíz detiene antes la guía actual (D8); `startDictation()` solo desde `Idle` y si no se está enviando ni se ha creado; incrementa la generación, pasa a `Listening` y llama a `start`. Sin permiso: `onDictationPermissionDenied()` muestra el aviso y no llama a `start`.
3. "Detener": `stop()` y estado `Processing`, a la espera de `onFinal`/`onError`. "Cancelar": `cancel()`, incrementa la generación y vuelve a `Idle`.
4. `onFinal(text)` vigente: si `text.trim()` no está vacío, `form.withDictation(text.trim())` (añade con la regla de R6 y fija `VOICE_ON_DEVICE`); si está vacío, aviso "no se entendió". Vuelve a `Idle`.
5. `onError(e)` vigente: `LanguageUnavailable` → `Unavailable` + aviso; `Permission` → aviso de R4; `NoMatch`, `Busy`, `Other` → aviso y `Idle`. El borrador no cambia.
6. `updateComplaint(text)`: `form.withTypedText(text)`; si `text.isBlank()`, origen `TYPED`.
7. `canSubmit` añade `dictation !is Listening && dictation !is Processing`. Tras el 201, el formulario nuevo vuelve a `TYPED` (ya ocurre en F-02).
8. `onCleared()`: `recognizer.release()`. La raíz llama a `cancelDictation()` en `ON_STOP`, en el `onDispose` de la entrada (salir de la pantalla, rotación o cambio de idioma/tema, cambio de sesión por `key(navigationIdentity)`) y cuando la guía pasa a `Speaking`.

Traducción de errores (`mapRecognizerError`, pura): `ERROR_LANGUAGE_NOT_SUPPORTED`, `ERROR_LANGUAGE_UNAVAILABLE` → `LanguageUnavailable`; `ERROR_INSUFFICIENT_PERMISSIONS` → `Permission`; `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT` → `NoMatch`; `ERROR_RECOGNIZER_BUSY` → `Busy`; cualquier otro → `Other`.

## UI y textos (R5, R14, R16)

`DictationControls` muestra "Dictar" en `Idle`; "Escuchando…"/"Procesando…" con "Detener" y "Cancelar" en `Listening`/`Processing`; nada en `Checking`; el aviso de no disponible en `Unavailable`. Una nota fija indica que el dictado reconoce solo español, también con la interfaz en inglés, portugués, francés o alemán. Cada botón tiene texto visible (descripción accesible) y el estado se anuncia con una región `liveRegion`. Claves nuevas con prefijo `dictation_` en los cinco catálogos (`values`, `values-en`, `values-pt`, `values-fr`, `values-de`); `PatientOnlyResourcesTest` exige el mismo conjunto de claves en los cinco.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.9.0** (antes la spec citaba 0.4.0; `BuildConfig.CONSUMED_CONTRACT` sigue en `"0.4.0"` y se actualiza a `"0.9.0"` en T1).

Verificación contra `../asclepio-api/openapi/asclepio-api.v1.json` (`info.version = 0.9.0`):
- `POST /api/v1/patient/pre-registrations` (`PatientPreRegistrationsController_create`, Bearer de paciente) sigue usando `CreatePreRegistrationDto` con `required: [siteId, chiefComplaintText, complaintSource]`; `siteId` string uuid; `chiefComplaintText` string `minLength 1`, `maxLength 2000`; `complaintSource` enum `TYPED | VOICE_ON_DEVICE`. Respuestas 201 `PatientPreRegistrationDto`, 429 con `Retry-After` y `default` `application/problem+json`, iguales a lo que ya consume F-02-mobile.
- `PatientPreRegistrationDto` y `EncounterRefDto` coinciden con los DTOs escritos a mano en `feature/encounters/data/PreRegistrationsApi.kt`.
- La API no tiene CHANGELOG del contrato. Según `../asclepio-api/docs/roadmap.md`, de 0.4.0 a 0.9.0 los cambios son de F-04-api (0.5.0), F-05-api (0.6.0), F-06-api (0.7.0), F-07-api (0.8.0) y DT-02-api (0.9.0): endpoints y esquemas del personal (sugerencia, decisión, cola, reportes). No tocan los endpoints del paciente ni el enum `complaintSource`. `GET /api/v1/patient/pre-registrations/{id}` existe desde F-02-api y la app no lo usa.
- No se añaden endpoints, campos ni tipos regenerados (los DTOs del móvil son manuales). `PatientOnlyScopeTest` sigue fijando las diez operaciones del paciente.

## Impacto en datos

**Sin impacto en datos.** Sin migraciones ni persistencia nueva en el dispositivo. El audio lo procesa el servicio local de Android y no se graba ni se sube. El texto reconocido y el origen viven solo en el `PreRegistrationUiState` del ViewModel; al enviar, la API recibe el texto y `complaintSource` en el POST existente y, al confirmar recepción, lo copia a la atención. La app no envía horas (RNF-10).

## Riesgos y pruebas

Riesgos:
- Muchos teléfonos de pacientes no tienen reconocimiento local en español: el fallback a texto será frecuente (PA-F08-2). Los emuladores suelen no tenerlo; la transcripción real se prueba en un dispositivo con el idioma español instalado.
- En API 31–32 no hay forma de saber el idioma antes de escuchar; se pide `es-CO` y un error de idioma desactiva el dictado para esa pantalla.
- Si `checkRecognitionSupport` nunca responde, el estado se queda en `Checking` (sin "Dictar"), y el texto sigue funcionando. No se inventa un tiempo de espera.

Pruebas JVM (`testDebugUnitTest`, nombres `R<n> ` con espacio, sin `:`):
- `PreRegistrationViewModelTest` con `FakeOnDeviceRecognizer`: disponibilidad, permiso denegado, una sola escucha, bloqueo de envío, resultado aceptado y vacío, errores, detener, cancelar, generación, `onCleared`.
- `DictationGuideExclusionTest`: `startDictationStoppingGuide` detiene la guía que recibe (la vigente tras recrearla) antes de iniciar la escucha, y no la de una composición anterior (R13, D8).
- `PreRegistrationFormTest`: `withDictation` y `withTypedText` (separador, sin truncar, reglas de origen).
- `PreRegistrationsApiTest` (Retrofit real con MockWebServer): JSON exacto con `VOICE_ON_DEVICE`.
- `SpanishOnDeviceSupportTest`: `recognitionStrategy`, `pickSpanishTag`, `mapRecognizerError`, `dictationAction`.
- `OnDeviceOnlyTest` (estático sobre `src/main`): `createOnDeviceSpeechRecognizer` y `RecognizerIntent` solo en `AndroidOnDeviceRecognizer.kt`; ninguna aparición de `createSpeechRecognizer(`, `startActivity`, `MediaRecorder`, `AudioRecord`, `synthesizeToFile` o `triggerModelDownload`; `RECORD_AUDIO` solo en `DictationControls.kt` y el manifiesto; sin `SavedStateHandle`, `Log.` ni DataStore en los archivos del dictado.
- `DictationStringsTest`: claves `dictation_*` no vacías en los cinco idiomas.
- Siguen en verde `PatientOnlyScopeTest`, `PatientOnlyNavigationTest`, `PatientOnlyResourcesTest`, `PatientOnlyDocsTest` y las pruebas de F-02.

QA manual (tarea final): emulador de teléfono y de tablet y, si se dispone, un dispositivo físico con español local. Las pruebas manuales contra la API local usan `adb reverse tcp:3000 tcp:3000` y `API_BASE_URL=http://localhost:3000` (Android 17 / targetSdk 37 bloquea la red local, como `10.0.2.2`). Se registra por separado: (a) pruebas JVM con el fake, (b) fallback y permisos en emulador, (c) transcripción real en dispositivo con modelo local (en modo avión, para mostrar que es local), (d) POST contra la API local. Sin modelo local no se declara transcripción real; la guía TTS no es evidencia de reconocimiento.
