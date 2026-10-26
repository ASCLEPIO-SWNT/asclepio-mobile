# Arquitectura — asclepio-mobile

Kotlin + Jetpack Compose, **MVVM**, Retrofit + kotlinx.serialization y layouts adaptativos con `WindowSizeClass` (teléfono y tablet). Alcance: **admisión**.

## Paquetes
```
app/src/main/java/co/asclepio/mobile/
├── MainActivity.kt
├── core/
│   ├── network/     # Retrofit, kotlinx.serialization, ProblemDetails, API_BASE_URL (BuildConfig)
│   ├── auth/        # sesión y token (almacenamiento seguro), interceptor de auth
│   └── ui/          # tema, componentes comunes, utilidades de WindowSizeClass
└── feature/
    ├── patients/    # buscar o crear paciente (F-02)
    ├── encounters/  # crear atención (F-02)
    └── voice/       # dictado en el dispositivo (F-08)
```
Cada `feature/<f>/` contiene `data/` (API y repositorio), `ui/` (Composables y `<X>ViewModel`) y, si aplica, `model/`.

## Dependencias

**Permitidas**
- `feature/*` → `core/*`.
- `ui` (Composable) → `ViewModel` → repositorio → servicio Retrofit.

**Prohibidas**
- `feature/A` → `feature/B`. Lo compartido va a `core/`.
- `core/` → `feature/`.
- Un Composable que llama al repositorio o a Retrofit.
- Enviar audio a cualquier servidor, o usar ASR en la nube (ADR 0006 de la API).
- Enviar horas legales a la API.

## Plataforma
- El `minSdk` y su justificación (disponibilidad de `SpeechRecognizer` local) se documentan en `docs/adr/` con F-00.
- `API_BASE_URL` se lee de `local.properties` hacia `BuildConfig` (por defecto `http://10.0.2.2:3000`).
- El tráfico en claro solo se permite en `debug`.
- El permiso `RECORD_AUDIO` se declara en el manifiesto y se solicita en el momento de uso.

## Qué es "buen trabajo" aquí
- El código está en el paquete correcto y sigue MVVM con `StateFlow<UiState>` (`Loading`, `Success` y `Error`).
- Cada `R<n>` tiene su prueba `R<n>: …`.
- El layout funciona en teléfono y en tablet.
- El audio no sale del dispositivo.
