# F-08-mobile — Diseño

## Resumen y límites

Spec futura de dictado del motivo PATIENT, dentro del plan aprobado y sin cambios de servidor. Se conserva `PreRegistrationForm`/VM y su provenance ya modelada. `feature/voice` implementa el adaptador on-device; la raíz compone controles/resultado mediante una interfaz en core, sin import entre feature/encounters y feature/voice. La guía TTS UI-01 permanece estática y separada.

## Decisiones

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | Factory on-device API31+ y español fijo es-CO/es con comprobación runtime/fallback texto. | Factory genérica+preferOffline, otro idioma o ASR remoto. | Preferencia offline no es garantía; ADR0006 exige audio local. |
| D2 | Solo resultado final vigente se añade al borrador; parciales no se guardan ni envían. | Sustituir automáticamente todo el motivo o enviar al terminar de hablar. | Conserva texto previo y revisión del usuario. |
| D3 | Flag de provenance sticky por borrador al aceptar dictado. | Recalcular TYPED por edición o estimar porcentaje dictado. | El plan resuelve PA-F08-1: sigue VOICE_ON_DEVICE tras editar. |
| D4 | Generación de escucha ligada a sesión/pantalla/draft, cancel/destroy en lifecycle y gate de permiso. | Reutilizar callbacks después de navegar o retomar micrófono automáticamente. | Impide insertar texto viejo o escuchar fuera de contexto. |
| D5 | Coordinación común de modalidades: iniciar ASR detiene guía y viceversa. | TTS y micrófono simultáneos o narrar transcripción con la guía. | Evita reconocer la propia narración y exponer datos clínicos. |

## Capacidad Android, permiso e idioma

- `minSdk31` se mantiene. Comprobar `SpeechRecognizer.isOnDeviceRecognitionAvailable`; crear **solo** con `createOnDeviceSpeechRecognizer`, registrar listener y operar en hilo principal. Declarar visibilidad `android.speech.RecognitionService` necesaria en `<queries>`, conservar RECORD_AUDIO ya declarado y pedirlo solo al pulsar Dictar.
- Request de reconocimiento con lenguaje libre y `EXTRA_LANGUAGE` **es-CO**, alternativa **es** local. La UI puede estar en en/pt/fr/de y seguirá reconociendo únicamente español, con esa indicación traducida. No autodetección ni cambio a otro idioma, Activity genérica de reconocimiento o proveedor cloud. EXTRA_PREFER_OFFLINE, si se incluye, no sustituye a la factory local.
- API33+ permite `checkRecognitionSupport` sobre el recognizer on-device; priorizar modelo español instalado/on-device, no lenguas solo online/pendientes. Sin soporte/modelo local se deja texto; no descarga automática. En API31–32 no existe esa consulta de soporte por intent: comprobar servicio local, solicitar español al reconocedor local y tratar errores de idioma/modelo como indisponibilidad runtime, sin afirmar que el booleano del servicio garantice idioma instalado. En ambos casos el canal sigue siendo exclusivamente on-device.
- Si es-CO no está soportado pero es sí, usar esa alternativa local en una nueva escucha explícita o seleccionarla antes mediante soporte33+. No replay de audio ni bucles de reintento; si no existe español local, deshabilitar/ocultar dictado con explicación y dejar texto. Error de comprobación soporta fallback seguro, no recognizer genérico.
- `RequestPermission` se solicita por acción. Al volver el resultado se comprueban pantalla/foreground/principal/generación; un grant tardío no inicia escucha después de navegar. Denegado/revocado/sin micro/servicio ocupado muestra error local y conserva el campo; no pedir permiso en bucle ni abrir ajustes automáticamente.

## Interfaces y composición

- Interfaz `DictationController` en core con estados Checking/Unavailable/Idle/Listening/Processing/Error y operaciones `check`, `start`, `stop`, `cancel`, `release`; callbacks finales con token de escucha. El adaptador Android vive en `feature/voice`. Una implementación falsa JVM expone los mismos eventos, sin modelar disponibilidad física como éxito.
- `AppContainer` inyecta factory/coordinador y la raíz agrega `voiceControls` como slot del formulario de preregistro y callback al VM de encounters. No se importa una feature desde otra. El formulario/VM es dueño del motivo/provenance; el controller solo entrega texto del resultado y no conoce APIs, ids de paciente ni crea preregistros.
- `AudioModeCoordinator` común proporciona exclusión Guide/Dictation. Comenzar ASR llama stop de guía antes de startListening. Comenzar guía cancela/desecha una escucha activa antes de narrar sus recursos estáticos. Las dependencias se inyectan como interfaces/callbacks para evitar ciclo core/ui↔feature/voice.

## Flujo de escucha y provenance

1. Solo PATIENT verificado y formulario actual puede activar. Guardar `listenGeneration` y snapshot de sesión/draft. Tras capacidad/permiso válidos detener guía e iniciar una escucha; botón Dictar no crea otra mientras esté Listening/Processing.
2. Detener usa `stopListening`, pasa a Processing y espera `onResults/onError` antes de una nueva escucha. Cancelar usa cancel/destroy e invalida generación inmediatamente; no acepta resultado posterior. No escuchar continuo ni autoreinicio. El envío del preregistro se bloquea durante Listening/Processing; se puede cancelar y seguir escribiendo.
3. `onResults` acepta solo primer candidato final no vacío de la escucha vigente, una sola vez. Se añade al motivo previo con separación de espacio si hace falta, sin recortar ni sustituir texto escrito. Parciales, si se muestran como preview, no se incorporan al formulario ni cambian provenance; no se persisten. Un resultado mayor al límite mantiene el texto y el error2000 para que se edite, sin truncar ni enviar solo.
4. Aceptar resultado pone `complaintSource=VOICE_ON_DEVICE`. Las ediciones posteriores, incluso reemplazar mucho o todo el texto, conservan ese valor durante **el mismo borrador**. Sin final aceptado se mantiene TYPED; error/cancelación/sin coincidencia no cambian el flag. Un formulario nuevo o reset tras201 vuelve a TYPED; ningún flag de un usuario anterior se filtra al siguiente.
5. `toRequest` conserva siteId+chiefComplaintText.trim()+complaintSource del contrato. No se añade audio, idioma, sessionId, hora o actor. La API copia la fuente al confirmar; el móvil no modifica directamente una atención para propagarla.

## Lifecycle, RAM y errores

Al salir del formulario, background, cambio de sesión/draft o configuración: cancelar y destruir recognizer, aumentar generación y limpiar parciales/estado de escucha. Rotación/locale/tema retiene motivo/provenance ya aceptados en el VM de la sesión; no reanuda micrófono, requiere nueva pulsación. Si muere proceso se pierde borrador, sin guardarlo para simular continuidad. Callbacks de permisos/support/results/errors viejos se ignoran, incluido A→B→A; no se mezclan resultados entre dos escuchas consecutivas ni se modifica otro formulario.

Errores de idioma/modelo/servicio, silencio/no coincidencia, busy y permiso son recursos locales de UI01; no detalle del motor con PHI en logs. Sin red recognizer remoto no existe como alternativa. Liberar recursos aunque falle init/start/stop y no ejecutar un comando antes de listener. No guardar audio, no MediaRecorder ni synthesizeToFile, no peticiones de bytes/multipart, no logs ni SavedStateHandle. Resultados y estados que los contienen enmascaran texto en toString. La guía recibe únicamente instrucciones fijas y jamás el motivo/transcripción.

Controles Dictar, Detener, Cancelar y alternativa escrita con semántica accesible, foco, estado visible y explicaciones5idiomas. No solicitar RECORD_AUDIO para TTS; la app puede usar guía sin micrófono y escribir sin ninguno. Se conservan contador/validación2000, sede/errores y permisos F-02, con identidad/preferencias320dp/tablet/texto200 UI01.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.4.0→0.4.0**. Solo cambia el valor de `complaintSource` ya publicado en `CreatePreRegistrationDto` cuando corresponde; POST `/api/v1/patient/pre-registrations` mantiene exactamente tres campos. No endpoint de audio/modelos/ASR ni cambio de backend, móvil STAFF, web o legacy. F-02 sin dictado sigue enviando TYPED y su regresión permanece.

## Impacto en datos

**Sin migraciones ni persistencia adicional.** Audio procesado por servicio on-device sin archivo/subida; texto/provenance RAM del borrador y texto final únicamente en el POST existente cuando usuario lo envía. No datos privados en preferencias, backups, logs, navegación serializada o TTS. La app no calcula horas legales ni edita atenciones.

## Fuentes y verificación

- Android publica la factory local desdeAPI31, `checkRecognitionSupport` desdeAPI33, lifecycle explícito y resultados; el código distingue ambas capacidades sin prometer disponibilidad de idioma a partir del servicio genérico. [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer). La preferencia offline puede ignorarse: [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent). El pipeline restringido aplica la decisión de ADR0006, no una garantía inferida de un recognizer de red.
- JUnit `R<n> ` sobre controlador/política: servicio ausente, español instalado/no instalado/support no disponible, permiso grant/denegado/revocado/tardío, states y un start por acción; es-CO/es sin otros idiomas/ASR genérico. Double callbacks final/error y A→B→A se descartan por generación; stop espera resultado, cancel no lo acepta.
- Provenance: solo escrito→TYPED; final aceptado→VOICE_ON_DEVICE; edición intensa/borrado/reemplazo sigue VOICE en ese draft; nuevo draft/201→TYPED; cancel/error/parcial sin aceptar no cambia; resultado mayor2000 valida sin truncar. MockWebServer en frontera verifica JSON exacto/enum y ausencia de audio, no repositorios simulados que oculten el POST.
- Instrumentación API31/37: fallback sin servicio/modelo y permiso denegado; UI sigue permitiendo escritura/envío, controles accesibles y rotación/background/session cleanup. En dispositivo con modelo local, realizar dictado español, revisión/editado y prueba sin conectividad ASR para verificar funcionalidad local; no asumir voz/modelo instalados en emulador.
- Separar evidencia: (a) lógica/JVM/adaptador falso, (b) permiso/fallback/lifecycle instrumentado, (c) transcripción real local, (d) POST fixture contractual, (e) API/DB real si hay configuración/cuentas. Si falta modelo local, registrar esa limitación y no declarar transcripción real. La guía audible TTS es otra prueba, no evidencia de ASR.
- Al activar: regresiones F-01/F-02 y paciente/STAFF, cinco idiomas,320dp/texto200/tablet/TalkBack/teclado; lint/testDebugUnitTest/assembleDebug/connectedDebugAndroidTest/init y reviewerC1–C8, trazabilidad. Sin librería de ASR, permiso o dependencia nueva ajena a las APIs Android existentes.
