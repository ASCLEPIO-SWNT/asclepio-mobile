# UI-01-mobile — Diseño

## Resumen

Se aplica la identidad UI-01/UI-02 a la app nativa y se añaden idiomas, preferencias y guía TTS local, conservando sesiones y negocio F-01/F-02. Se incorpora Mis solicitudes mediante la lista propia ya publicada. La documentación materializa el bloque autorizado por el usuario para su activación el 2026-10-29 (America/Bogota), con F-01-staff-mobile cerrado, aprobado y publicado en main `548d17c`; el líder registra el estado y la línea base.

## Decisiones

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | Componentes Compose Material3 con tokens, tipografía, recursos e iconos de la web de referencia; una marca privada. | WebView, React o paleta dinámica que sustituya la marca. | Mantiene apariencia y arquitectura Android sin duplicar encabezados. |
| D2 | Recursos Android completos es/en/pt/fr/de y AppCompat 1.8.0 con AppCompatActivity como fuente única de locale. | Cambiar solo textos Compose con un mapa o duplicar idioma en otro DataStore. | Cubre diálogos/errores/semántica y compatibilidad API31–32/33+. |
| D3 | Preferences DataStore `asclepio_ui` separado, singleton; borradores en VM por entrada y generación. | Guardar UI junto al token o persistir formularios para sobrevivir locale. | Logout conserva preferencias sin guardar datos privados. |
| D4 | Fuentes TTF/OTF nativas locales, PNG/vector nativos y avisos de procedencia/licencia. | WOFF2 como fuente Android, SVG web complejo o descargas de fuente en runtime. | Recursos decodificables y cambios accesibles verificables. |
| D5 | TTS offline con instrucciones estáticas separadas y pausa/continuación por instrucción. | Lectura de pantalla/valores privados o una supuesta pausa nativa equivalente a navegador. | La API Android ofrece stop; se reanuda la instrucción interrumpida sin almacenar audio. |
| D6 | Mis solicitudes usa lista existente, conserva latest/canCreate y refresco paciente entrada/foreground/manual. | Timeline de eventos, polling paciente o reescribir F-02. | El contrato solo aporta solicitudes y estado actual; 30 s son del futuro dashboard STAFF. |
| D7 | Layout según ancho útil y texto efectivo, con columna/scroll como fallback y estados por icono/texto/color. | Dos paneles fijos por ser tablet o alturas rígidas de campos. | Funciona a 320 dp y texto grande en los cinco idiomas. |

## Presentación, recursos e idiomas

- `core/ui` contiene tokens/paletas/tipografía, Brand, PageHeader, Panel, botones/campos y estados; la raíz compone marco y preferencias y las features conservan VM/callbacks. No hay dependencias entre features. El marco privado muestra una sola marca; los títulos de pantalla no repiten el logotipo. Los destinos aún pendientes de F-01-staff siguen claramente pendientes, sin números ni contenido simulado.
- Referencia de lectura: UI-02-web main `dc280a9`/árbol `c812c63`. Igual identidad de turquesa/fondos/espaciado/radios/iconos y composición equivalente en dp/sp; los cuatro dashboards y la supresión del aside de enfermería se implementan en UI-02 posterior, no se adelantan aquí.
- Axiforma Regular/SemiBold/Bold TTF existen en legacy y se copian solo como recursos usados. OpenDyslexic requiere TTF/OTF nativo de la fuente oficial con SIL-OFL; no asumir que el WOFF2 web se decodifica en Android. Documentar origen/revisión y conservar avisos aplicables en `assets`/notas de recursos; no inventar una licencia abierta para Axiforma si el asset original no la contiene. Comprobar carga real de ambas FontFamily en dispositivo, no solo existencia de una preferencia.
- Marca PNG web y fondos pertinentes se empaquetan nativamente con tamaño adecuado. Los SVG web de login/registro llevan imágenes y filtros: extraer/rasterizar de forma reproducible solo los fondos usados, sin añadir motor SVG/WebView ni todos los assets legacy. El launcher usa el símbolo aprobado en recursos adaptativos nativos.
- Todos los textos actuales y añadidos, incluidos F-01-staff, campos, errores UiMessage, diálogos, preferencias, estado, semántica y etiquetas de iconos, se resuelven desde `values`/`values-en`/`values-pt`/`values-fr`/`values-de`. Comprobar mismas claves y placeholders tipados; `UiMessage` mantiene ids/argumentos hasta renderizar, no strings traducidos guardados en VM. Español usa «preregistro» sin guion; actualizar expectativas literales correspondientes sin alterar semántica.
- `MainActivity` pasa a `AppCompatActivity`, con tema compatible DayNight sin ActionBar, conservando edge-to-edge/safeDrawing. `AppCompatDelegate.setApplicationLocales` aplica el selector; `getApplicationLocales`/configuración efectiva alimenta selector y TTS. Declarar cinco idiomas mediante localeConfig y `autoStoreLocales` para versiones antiguas; sin preferencia al primer uso se aplica es una sola vez. Android/AppCompat conserva el idioma, no un duplicado en `asclepio_ui`.
- El cambio de locale puede recrear Activity. Navigation3 y su ViewModelStore por entrada conservan VM en configuración; el gestor F-01-staff no cambia generación por esa recreación. No usar locale/tema como key del NavDisplay, root o ViewModelStore. La muerte de proceso descarta borradores privados. Probar cambio desde selector y ajustes del sistema; no persistir contraseñas/datos para simular retención.

## Preferencias e interfaces comunes

- `UiPreferences` tipada: tema light/dark/system, modo none/highContrast/sepia/grayscale/alternative, escala 0.875/1/1.25/1.5/2 y `dyslexiaFont`. Defaults light/none/1/false. `UiPreferencesRepository.preferences: StateFlow<UiPreferences>` y `update(...)` usan un único DataStore `asclepio_ui`; validan lectura y capturan fallo de almacenamiento conservando estado en RAM. No token, principal, ids, listas ni borradores en ese archivo; logout no lo borra.
- Una pantalla/panel de preferencias accesible desde entrada y marco privado actualiza solo presentación. El tema system observa la configuración; las paletas cubren ColorScheme y estados/foco/selección, sin depender solo del color. Filtros de grises/alternativo deben mantener operable el panel para revertir el modo.
- `AsclepioTheme(preferences, content)` aplica FontFamily y escalas de Typography respetando además fontScale del sistema. No fijar escala sistema a 1 ni alturas que recorten texto. `paneMode` se amplía con ancho disponible/escala efectiva; cuando no caben ambos paneles legibles, una columna con scroll. Targets de al menos 48 dp, etiquetas/semántica y foco, controles visibles con IME y reducción de movimiento del sistema.
- AppCompat es la única dependencia de producción nueva prevista; versión 1.8.0 en catálogo, compatible con minSdk31. Añadir únicamente utilidades Compose instrumentadas compatibles con BOM para verificación. No framework UI, DI o red nuevo.

## Guía TTS local

- `core/ui` o subpaquete común de guía contiene `SpeechGuideController` y adaptador Android; no reutiliza el recognizer de F-08. La raíz entrega destino lógico, locale y **ids de instrucciones estáticas** de recursos; nunca DTOs, campos, consentimiento servidor ni texto de composables. El controlador expone Idle/Speaking/Paused/Unavailable, `start`, `pause`, `resume`, `repeat`, `stop` y `release` detrás de una interfaz testeable JVM.
- Seleccionar únicamente una `Voice` con idioma compatible e `isNetworkConnectionRequired == false`; no fallback a red ni cambio automático a otro idioma. Inicialización/voz ausente/speak fallido muestran indisponibilidad localizada. La guía no pide RECORD_AUDIO. Declarar visibilidad TTS_SERVICE si lo requiere la consulta del motor; no sintetizar a archivo ni registrar texto hablado.
- Cada instrucción es un utterance; pausa llama stop y conserva índice, continuar vuelve a pronunciar **esa instrucción desde el inicio**. Repetir empieza por la primera. No se promete reanudar audio desde milisegundo exacto. Solo una activación del usuario inicia reproducción; después de volver a pantalla/app no hay autoplay.
- Ruta/locale/texto/sesión/background cancela y aumenta generación de reproducción, reinicia índice/active/paused/failed y descarta callbacks antiguos; A→B→A no recupera controles/error previos. `shutdown` al liberar. No encadenar instrucciones desde callbacks de generación anterior. F-08 coordinará detener guía antes de dictado cuando se implemente, sin adelantarlo.

## Integración de solicitudes propias

- `PreRegistrationsApi.list()` ya devuelve todos los `PatientPreRegistrationDto`; añadir `PreRegistrationRepository.list()` con el wrapper paciente del F-01-staff terminado. Conservar `latest()` y la semántica de creación del inicio basadas en primer elemento. Nuevo `MyRequestsViewModel` y pantalla de lista se componen desde la raíz, con enlace «Mis solicitudes» en inicio paciente, sin imports account→encounters.
- La lista usa orden contractual descendente y presenta sede, texto estado de las funciones existentes, hora «Enviado» Bogotá y expiración solo PENDING. No eventos inventados, nivel, arrivalAt, encounter.id, motivo de rechazo ni controles de retiro. Campos desconocidos adicionales se ignoran. Estados desconocidos mantienen su texto seguro y no cambian la regla de una solicitud en curso.
- Refresca al entrar/volver a primer plano y manualmente; no polling, WorkManager ni temporizadores paciente. Entrada y foreground simultáneos no duplican solicitud activa. El Home conserva actualización tras 201 y su estado más reciente. Error de lista muestra error/reintento, sin filas antiguas presentadas como actuales; perfil/logout y borradores ajenos permanecen disponibles. Se conserva 429/401/403 y toda validación de F-02.
- F-02-mobile entregado sigue terminado. Se añade capacidad de listado y se actualizan presentación/textos por esta decisión autorizada; no se reescribe perfil/consentimiento/preregistro ni se cambia PA-M02-4. Los 30 s del plan pertenecen exclusivamente a los cuatro dashboards STAFF de UI-02.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.4.0 → 0.4.0**. Mis solicitudes reutiliza GET `/api/v1/patient/pre-registrations`; no hay rutas, parámetros, DTOs o errores nuevos. Auth y escrituras F-02 conservan cuerpos/headers y sus guardas del bloque anterior. No endpoints de preferencias, traducción o voz ni cambio de API/web/legacy.

## Impacto en datos

**Sin impacto en servidor ni migraciones.** Solo aparece DataStore local de preferencias visuales sin PHI y almacenamiento de locale administrado por Android/AppCompat. SessionStore/token permanecen separados. Listas, perfiles y borradores son RAM de su generación; no SavedStateHandle, disco, logs, navegación serializada ni TTS. La guía no produce archivos/audio persistido. Recursos/fuentes/licencias se empaquetan en APK.

## Fuentes técnicas verificadas y pruebas

- Android recomienda AppCompatActivity para locales Compose anteriores a API33 y advierte la recreación por cambio de idioma; la spec retiene VM en lugar de serializar datos. [Idiomas por aplicación](https://developer.android.com/guide/topics/resources/app-languages), [AppCompat 1.8.0](https://developer.android.com/jetpack/androidx/releases/appcompat).
- `TextToSpeech.stop` interrumpe/vacía la cola y `Voice.isNetworkConnectionRequired` permite excluir voces de red: pausa por instrucción es una decisión de esta interfaz sobre esas capacidades. [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice](https://developer.android.com/reference/android/speech/tts/Voice).
- JUnit con `R<n> `: catálogos/placeholders, defaults/valores inválidos/fallo storage, paletas/escala y voces offline, generación/callbacks/reset A→B→A, lista/orden/estado/horas/errores sin datos viejos y preservación latest/canCreate. MockWebServer para la frontera lista, sin mocks de repositorios internos.
- Instrumentación Compose API31/37: formulario escrito → es/en/pt/fr/de → rotación/tema/escala → mismos valores/sesión; idioma desde ajustes y reinicio, logout conserva preferencias pero limpia borrador; muerte de proceso no lo recupera. Comprobar carga de fuentes y roles, no solo screenshots vacíos.
- QA visual teléfono 320 dp/tablet, cinco idiomas, claro/oscuro/modos, tamaño 200 %, IME, TalkBack/teclado/foco y movimiento reducido; capturas equivalentes con UI02. Ejecutar lint/testDebugUnitTest/assembleDebug/connectedDebugAndroidTest/init y reviewer independiente al activar el bloque.
- TTS motor falso valida secuencia/voz/callbacks; capacidad ausente en emulador debe dejar app utilizable. Reproducción audible real exige voz local instalada; registrar por separado disponibilidad, reproducción, fixtures y API real. No presumir sonido por haber llamado a speak.

Se consultaron las notas de exploración del implementer UI en `local-analysis/mobile-verification/ui-notes.md`; sus hallazgos de recursos nativos, AppCompat/VM y lista actual están reflejados. La configuración API real permanece condicionada a cuentas/entorno del equipo y no cambia la prueba contractual autorizada.
