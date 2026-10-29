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


## Acceso paciente/personal (F-01-staff-mobile)

- Ejecutar `./gradlew lint testDebugUnitTest assembleDebug` y `./init.sh` completo. La regresión incluye F-01/F-02 existentes, HTTP real con MockWebServer, privacidad de disco/toString, restauración y generaciones intercaladas, guards por rol y formularios VM.
- La fecha privada usa `rememberPrivateDatePickerState` y `PrivateDatePicker` dentro del Dialog; no usar `rememberDatePickerState` ni el DatePicker real fuera de esa frontera. La prueba estática verifica ese límite; la inspección de Bundle instrumentada se registra como evidencia aparte cuando se ejecute.
- Mantener las pruebas Keystore instrumentadas (AES-256-GCM) separadas de los fixtures JVM. Una pasada del APK base no prueba por sí sola la UI de una APK posterior.
- En teléfono/tablet comprobar bienvenida, paciente con/sin IPS, login staff sin selector de IPS ni registro público, cuatro hogares y destinos exactos, error local/429, restauración bloqueada ante red/5xx, confirmaciones de logout/cambio y Atrás sin contenido anterior. Verificar rotación/teclado y destrucción de borradores al cambiar generación.
- Las funciones staff posteriores aparecen como pendientes; no se simulan listas clínicas ni se ejecutan endpoints de esos bloques desde F-01-staff-mobile.
- Registrar para cada ejecución emulador/API, APK/commit, fixture contractual o servidor real y acciones efectivamente realizadas. No llamar integración real a un fixture; no afirmar TalkBack, rotación o persistencia del proceso sin haberlos ejercitado.
