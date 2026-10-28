# asclepio-mobile

App Android de **Asclepio para el paciente** que llega a urgencias: crea su cuenta, inicia sesión y envía un **pre-registro** con sus datos y su motivo de consulta, que puede dictar **en el dispositivo** (el audio nunca sale del teléfono). Recepción confirma la llegada en la web (ADR 0008 de la API). Usa Kotlin + Jetpack Compose, MVVM, Retrofit + kotlinx.serialization y layouts adaptativos (`WindowSizeClass`).

## Espacio de trabajo
Necesita `asclepio-api` clonado al lado (de ahí salen el contrato y los briefs):
```
asclepio/
├── asclepio-api/
├── asclepio-web/
└── asclepio-mobile/   ← este repo
```

## Requisitos
- JDK 17+ y Android SDK (Android Studio o cmdline-tools) con `ANDROID_HOME` definido.
- `local.properties` (no se versiona):
  ```properties
  sdk.dir=/ruta/a/Android/Sdk
  # Opcional: por defecto es http://localhost:3000 (con adb reverse)
  API_BASE_URL=http://localhost:3000
  ```
  **Usa siempre `adb reverse` y `API_BASE_URL=http://localhost:3000`** (el valor por defecto; emulador y teléfono):
  ```bash
  adb reverse tcp:3000 tcp:3000   # repetirlo cada vez que se reinicia el emulador o se reconecta el teléfono
  ```
  > **Android 17 (API 37) bloquea la red local:** con `targetSdk 37`, una app no puede abrir conexiones a IPs locales (`10.0.2.2`, `192.168.x.x`) sin el permiso `ACCESS_LOCAL_NETWORK`, y el error aparece en la app como "sin conexión". `localhost` a través de `adb reverse` no es red local, así que funciona sin permisos. (Diagnosticado el 2026-10-27: `nc` desde `adb shell` conecta, pero `run-as co.asclepio.mobile nc …` da timeout.)

  > No es una app Expo: **Expo Go no puede abrirla** porque es nativa (Kotlin + Compose). Para verla en tu teléfono usa `adb`.

## Arranque
> El esqueleto técnico llega con **F-00-mobile**.

```bash
./init.sh            # o ./init.sh --quick
```
```bash
./gradlew testDebugUnitTest lint   # lo mismo que ejecuta init.sh
./gradlew assembleDebug            # APK en app/build/outputs/apk/debug/
adb devices                        # teléfono (depuración USB) o emulador
./gradlew installDebug             # instala en el dispositivo conectado
```
Antes, arranca la API (`cd ../asclepio-api && npm run start:dev`).

## Cómo trabajar con el harness (SDD)
1. Abre Claude Code en este directorio; actúa como **leader**.
2. El leader elige la siguiente feature `pending` cuyas dependencias estén en `done`, incluida la de la API.
3. La spec queda en `specs/<id>/` → **respondes "aprobado" en el chat**.
4. `implementer` implementa con las pruebas primero; `reviewer` verifica `CHECKPOINTS.md`.

Lectura recomendada: `AGENTS.md` → `../asclepio-api/docs/product/vision.md` → `docs/specs.md`.
