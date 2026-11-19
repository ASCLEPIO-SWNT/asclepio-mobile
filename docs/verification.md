# Verificación — asclepio-mobile

## Cómo se demuestra que algo funciona
1. `./init.sh`: entorno (JDK 17+ y wrapper), harness, `local.properties`, estado y calidad (`./gradlew lint testDebugUnitTest`).
2. Prueba manual en el emulador de **teléfono** y de **tablet** contra la API local (`adb reverse tcp:3000 tcp:3000` + `http://localhost:3000`, el valor por defecto de `API_BASE_URL`; Android 17 bloquea `10.0.2.2` sin `ACCESS_LOCAL_NETWORK`). Se registra en `progress/impl_<id>.md`.

## Trazabilidad R<n> → prueba
```kotlin
@Test fun `R2 crea la atención con complaint_source VOICE_ON_DEVICE si el texto viene del dictado`() { … }
```
- Para comprobarlo: `grep -rnE '`R[0-9]+ ' app/src/test app/src/androidTest`.
- `implementer` registra la tabla `R<n>` → `archivo::prueba` en `progress/impl_<id>.md`.

## Qué se prueba siempre (cuando aplica)
- Las transiciones de `UiState` del ViewModel (Loading → Success o Error).
- La traducción de `problem+json`.
- En F-08: la detección de reconocimiento local, sin disponibilidad (solo texto) y con permiso denegado (solo texto). La lógica de decisión se prueba en la JVM, detrás de una interfaz.

## Demo extremo a extremo
La lista de comprobación de la demo completa está en `../asclepio-api/docs/verification.md`.


## App solo del paciente (DT-01-mobile)

- La app es solo del paciente (ADR 0008 de la API, "Ratificación"). `PatientOnlyScopeTest` fija las diez operaciones Retrofit permitidas y falla si una ruta de personal aparece en `src/main`; `PatientOnlyNavigationTest`, las rutas y los destinos de guía; `PatientOnlyResourcesTest`, los paquetes y recursos retirados; `PatientSessionLifecycleTest`, el rechazo de un `/me` no paciente al restaurar y las generaciones de sesión.
- Ejecutar `./gradlew lint testDebugUnitTest assembleDebugAndroidTest` y `./init.sh` completo. La regresión incluye F-01/F-02/UI-01 existentes, HTTP real con MockWebServer, privacidad de disco/toString, restauración y generaciones intercaladas y formularios VM.
- La fecha privada usa `rememberPrivateDatePickerState` y `PrivateDatePicker` dentro del Dialog; no usar `rememberDatePickerState` ni el DatePicker real fuera de esa frontera. La prueba estática verifica ese límite; la inspección de Bundle instrumentada se registra como evidencia aparte cuando se ejecute.
- Mantener las pruebas Keystore instrumentadas (AES-256-GCM) separadas de los fixtures JVM. Una pasada del APK base no prueba por sí sola la UI de una APK posterior.
- En teléfono/tablet comprobar la entrada directa (selección de IPS sin IPS recordada, inicio de sesión con IPS), el aviso "Esta cuenta no es de paciente…" al iniciar sesión o restaurar con una cuenta de personal, restauración bloqueada ante red/5xx, confirmación de cierre de sesión y Atrás sin contenido anterior. Verificar rotación/teclado y destrucción de borradores al cambiar de generación.
- Registrar para cada ejecución emulador/API, APK/commit, fixture contractual o servidor real y acciones efectivamente realizadas. No llamar integración real a un fixture; no afirmar TalkBack, rotación o persistencia del proceso sin haberlos ejercitado.

## Instrumentación Compose en Android 37 (UI-01-mobile)

Las dependencias de instrumentación fijan AndroidX Test runner 1.7.0, ext.junit 1.3.0 y Espresso core **3.7.0 estable**. Compose UI test 1.12.1 solicita Espresso 3.5.0 transitivamente; se declara la versión corregida de Espresso de forma explícita en `androidTestImplementation`, sin cambiar el runtime de producción. La [nota oficial de Espresso 3.7.0](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0) incluye el acceso a InputManager mediante `getSystemService`, que sustituye la reflexión sobre `InputManager.getInstance`.

En la pasada inicial de UI-01 sobre Android 37 esa reflexión provocó `NoSuchMethodException` antes de las acciones Compose. Es un fallo del harness de instrumentación; esa pasada no valida las aserciones de las pantallas. Conservar sus registros y volver a ejecutar las pruebas después de reconstruir el APK de test con la versión corregida, registrando los resultados por separado.

Para comprobar la dependencia efectiva y compilar el APK de test:

```sh
./gradlew :app:dependencyInsight --configuration debugAndroidTestRuntimeClasspath --dependency androidx.test.espresso:espresso-core --single-path --console=plain
./gradlew assembleDebugAndroidTest --console=plain
```

`dependencyInsight` debe resolver `espresso-core:3.7.0`; compilar el APK no sustituye ejecutar `connectedDebugAndroidTest` o el runner instalado en los dispositivos coordinados. No aplicar excepciones de reflexión ni modificar pruebas de producto para ocultar este error del harness.


## Dictado en el dispositivo (F-08-mobile)

Dos niveles de evidencia que **no se mezclan**:

1. **JVM con un reconocedor falso** (`./gradlew testDebugUnitTest`): `FakeOnDeviceRecognizer` (`src/test/.../core/voice/`) sustituye al puerto `OnDeviceRecognizer`. Cubre la lógica del ViewModel (disponibilidad, permiso, una sola escucha, bloqueo de envío, resultado aceptado y vacío, errores, detener, cancelar, generación de escucha, `onCleared`), la exclusión guía/dictado con la guía recreada (`DictationGuideExclusionTest`: `startDictationStoppingGuide` detiene la guía vigente antes de escuchar, D8), las funciones puras de `feature/voice/SpanishOnDeviceSupport.kt`, el formulario (`withDictation`/`withTypedText`), el POST real con MockWebServer y las pruebas estáticas de `OnDeviceOnlyTest` (solo `createOnDeviceSpeechRecognizer`, sin grabación, actividades de reconocimiento ni descarga de modelos; `RECORD_AUDIO` solo en `DictationControls`). Estas pruebas **no** demuestran reconocimiento real.
2. **Dispositivo o emulador** (QA manual, `progress/impl_F-08-mobile.md`): fallback sin reconocimiento local, permiso denegado, cinco idiomas con la nota "solo español", TalkBack, teléfono y tablet; y, solo en un dispositivo con el español local instalado, la transcripción real en modo avión (prueba de que es local), Detener/Cancelar, rotación, segundo plano y guía durante la escucha, "rotar → iniciar guía → cerrar panel → Dictar" (la guía se detiene), y el POST con `VOICE_ON_DEVICE` contra la API local (`adb reverse tcp:3000 tcp:3000`). Sin modelo local no se declara transcripción real; la guía TTS no es evidencia de reconocimiento.


## Posición en la fila en vivo (F-10-mobile)

- **JVM** (`./gradlew testDebugUnitTest`): `QueuePositionStreamTest` usa okhttp-sse real contra MockWebServer (`text/event-stream` con `event: position`, latido `: ping`, cortes, 401/403/5xx, cancelación y 401 de otra generación); `QueuePositionViewModelTest` prueba el backoff con tiempo virtual; `QueuePositionCardTest`, la función pura de la tarjeta y el catálogo; `QueuePositionLifecycleTest`, que `follow()` solo se llama dentro de `repeatOnLifecycle(STARTED)` en la entrada Home.
- **Instrumentada** (`QueuePositionCardInstrumentedTest`): render en español y `liveRegion` educado. Compilar el APK de test no equivale a ejecutarlo.
- **Manual** (T6): con `adb reverse tcp:3000 tcp:3000`, prerregistro → confirmar en recepción → `triage.sh` de otro paciente más grave → la posición cambia sin refrescar; con modo avión, "Sin conexión" y reconexión.
