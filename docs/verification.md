# Verificación — asclepio-mobile

## Cómo se demuestra que algo funciona
1. `./init.sh`: entorno (JDK 17+ y wrapper), harness, `local.properties`, estado y calidad (`./gradlew lint testDebugUnitTest`).
2. Prueba manual en el emulador de **teléfono** y de **tablet** contra la API local (`http://10.0.2.2:3000`). Se registra en `progress/impl_<id>.md`.

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
