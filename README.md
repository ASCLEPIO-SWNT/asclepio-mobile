# asclepio-mobile

App Android de **Asclepio** para admisión en urgencias: login, buscar o crear paciente, crear la atención y dictar el motivo de consulta **en el dispositivo** (el audio nunca sale del teléfono). Usa Kotlin + Jetpack Compose, MVVM, Retrofit + kotlinx.serialization y layouts adaptativos (`WindowSizeClass`).

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
  API_BASE_URL=http://10.0.2.2:3000
  ```
  Valores de `API_BASE_URL` según dónde pruebes:
  | Dónde | `API_BASE_URL` |
  |---|---|
  | Emulador | `http://10.0.2.2:3000` |
  | Teléfono por USB o Wi-Fi con `adb reverse tcp:3000 tcp:3000` | `http://localhost:3000` |
  | Teléfono en la misma Wi-Fi (sin adb reverse) | `http://<IP-de-tu-PC>:3000` |

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
