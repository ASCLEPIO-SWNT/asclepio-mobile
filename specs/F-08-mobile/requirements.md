# F-08-mobile — Dictado del motivo en el dispositivo

Brief global: `../asclepio-api/docs/product/features/F-08.md` (F-08.AC1–AC5). Decisiones: ADR 0006 (voz en el dispositivo) y ADR 0008 (app solo del paciente, sección "Ratificación") de la API; ADR 0001 local (minSdk 31).
Contrato: consume **0.9.0** (`info.version` de `../asclepio-api/openapi/asclepio-api.v1.json`), sin cambios de contrato. Solo usa `POST /api/v1/patient/pre-registrations` (`CreatePreRegistrationDto`), ya consumido por F-02-mobile.
Dependencias: F-02-mobile (`done`).
RNF aplicables: RNF-05, RNF-06, RNF-10, RNF-13, RNF-14.

Glosario:
- **Reconocimiento local en español**: un `SpeechRecognizer` creado con `createOnDeviceSpeechRecognizer` (API 31+) cuyo idioma solicitado es español (`es-CO` preferido, o cualquier variante `es`).
- **Escucha vigente**: la escucha iniciada por la última pulsación de "Dictar" en el formulario abierto, que no se ha cancelado, liberado ni sustituido.
- **Resultado aceptado**: el primer candidato de `onResults` de la escucha vigente que no queda vacío tras quitar espacios. Los resultados parciales no se piden ni se usan.
- **Origen del motivo**: el valor de `complaintSource` del borrador (`TYPED` o `VOICE_ON_DEVICE`).

## Requisitos

- **R1** (Por evento) Cuando se abra la pantalla de pre-registro, el sistema deberá comprobar, sin abrir el micrófono ni pedir permisos, si hay reconocimiento local en español: en API 31–32 con `SpeechRecognizer.isOnDeviceRecognitionAvailable`, y en API 33+ con `isOnDeviceRecognitionAvailable` seguido de `checkRecognitionSupport` exigiendo una variante `es` en `installedOnDeviceLanguages`, sin llamar a `triggerModelDownload`. (→ F-08.AC1)
- **R2** (Ubicuo) El sistema deberá crear el reconocedor exclusivamente con `SpeechRecognizer.createOnDeviceSpeechRecognizer`, en el hilo principal y solo después de que `isOnDeviceRecognitionAvailable` devuelva `true`, registrar el listener antes de cualquier comando y llamar a `destroy()` al terminar, sin usar `createSpeechRecognizer`, `RecognizerIntent` en una actividad externa, grabación propia de audio ni ningún envío de audio. (→ F-08.AC2; RNF-06)
- **R3** (Por evento) Cuando el paciente pulse "Dictar" sin el permiso `RECORD_AUDIO` concedido, el sistema deberá solicitarlo en ese momento y empezar a escuchar solo si se concede; el sistema no deberá solicitarlo al abrir la app ni la pantalla. (→ F-08.AC5)
- **R4** (No deseado) Si el permiso `RECORD_AUDIO` se deniega o se revoca, o el reconocedor informa falta de permisos, entonces el sistema deberá mostrar un aviso local, conservar el motivo escrito y su origen y mantener la edición y el envío por texto, sin volver a solicitar el permiso hasta una nueva pulsación de "Dictar". (→ F-08.AC5)
- **R5** (Por estado) Mientras haya una escucha vigente o se espere su resultado, el sistema deberá mostrar el estado ("Escuchando…"/"Procesando…") con los controles "Detener" y "Cancelar", no iniciar otra escucha y deshabilitar "Enviar". (→ F-08.AC2, F-08.AC3)
- **R6** (Por evento) Cuando llegue un resultado aceptado de la escucha vigente, el sistema deberá añadirlo una sola vez al final del motivo (separado por un espacio si el texto previo no está vacío ni termina en espacio), dejarlo editable, no enviarlo y no truncarlo; si el motivo supera 2000 caracteres se mostrará la validación existente de F-02. (→ F-08.AC3)
- **R7** (No deseado) Si la escucha termina sin resultado aceptado, con error o cancelada, entonces el sistema deberá conservar el motivo y su origen sin cambios y mostrar el aviso local correspondiente; si el error es de idioma no disponible (`ERROR_LANGUAGE_NOT_SUPPORTED` o `ERROR_LANGUAGE_UNAVAILABLE`), el dictado pasará a no disponible según R16. (→ F-08.AC1, F-08.AC3)
- **R8** (Por evento) Cuando se acepte un resultado de dictado en el borrador, el sistema deberá fijar `complaintSource = VOICE_ON_DEVICE` y conservarlo ante cualquier edición posterior que deje el motivo con texto; el motivo volverá a `TYPED` solo cuando una edición lo deje vacío o en blanco (regla de PA-F08-1), y un borrador sin resultado aceptado, un formulario nuevo o el formulario tras un 201 tendrán `TYPED`. (→ F-08.AC4)
- **R9** (Por evento) Cuando el paciente envíe el pre-registro, el sistema deberá enviar el `POST /api/v1/patient/pre-registrations` existente con exactamente `siteId`, `chiefComplaintText` (sin espacios al inicio ni al final) y `complaintSource`, sin audio, idioma, horas ni otros campos, y con las validaciones y errores de F-02. (→ F-08.AC2, F-08.AC4; RNF-10, RNF-14)
- **R10** (Por evento) Cuando la app pase a segundo plano, se abandone la pantalla de pre-registro, cambie la sesión o se cancele la escucha, el sistema deberá cancelar la escucha, liberar el reconocedor e ignorar cualquier resultado o error posterior de esa escucha, sin modificar el borrador. (→ F-08.AC2, F-08.AC3)
- **R11** (Por evento) Cuando se rote la pantalla o cambie el idioma o el tema durante una escucha, el sistema deberá cancelarla, conservar el motivo y el origen ya aceptados en el ViewModel y exigir una nueva pulsación de "Dictar" para volver a escuchar. (→ F-08.AC3, F-08.AC4)
- **R12** (Ubicuo) El sistema deberá mantener el texto reconocido solo en la memoria del ViewModel del formulario, sin resultados parciales, archivos de audio, `SavedStateHandle`, DataStore ni logs, y con el texto enmascarado en `toString()`. (→ F-08.AC2; RNF-05, RNF-06)
- **R13** (Por evento) Cuando empiece una escucha, el sistema deberá detener la guía TTS; y cuando la guía empiece a hablar, el sistema deberá cancelar la escucha vigente, sin que la guía lea nunca el motivo ni el texto reconocido. (→ F-08.AC2)
- **R14** (Ubicuo) El sistema deberá mostrar los controles, estados y avisos del dictado con textos en los cinco idiomas de la app, indicando que el dictado reconoce solo español, con descripción accesible de cada control, en teléfono y en tablet. (→ F-08.AC1, F-08.AC3; RNF-13)
- ~~**R15**~~ Retirado (ver "Cambios respecto a la versión anterior").
- **R16** (No deseado) Si no hay reconocimiento local en español (sin servicio local, sin variante `es` instalada en API 33+, error de la comprobación o error de idioma de R7), entonces el sistema deberá ocultar "Dictar", mostrar un aviso de que el dictado no está disponible en este teléfono y mantener el motivo escrito y el envío. (→ F-08.AC1)

## Pendiente de aprobación (preguntas abiertas)

- [x] **PA-F08-1** (confirmada por el humano en el chat, 2026-11-17) (brief F-08) Texto dictado y luego editado. **Propuesta:** se envía como `VOICE_ON_DEVICE` en cuanto el borrador incorpora un resultado aceptado, aunque luego se edite mucho. **Regla para volver a `TYPED`:** solo si una edición deja el campo del motivo vacío o en blanco (el paciente borró todo); lo que escriba después es `TYPED` hasta un nuevo resultado aceptado. Cancelar, un error o un dictado sin resultado no cambian el origen. — bloquea R8.
- [x] **PA-F08-2** (confirmada por el humano en el chat, 2026-11-17) (brief F-08) Dispositivos objetivo. **Propuesta:** Android 12+ (minSdk 31, ADR 0001). El dictado se ofrece solo donde haya reconocimiento local en español detectado en tiempo de ejecución; en el resto (sin servicio o modelo local, idioma no instalado, permiso `RECORD_AUDIO` denegado) la app funciona solo con texto. No se descargan modelos desde la app. — bloquea R1, R3, R4, R16.

## Cambios respecto a la versión anterior

- Contrato: de "consume 0.4.0" a **consume 0.9.0** (sin cambios que afecten a F-08; ver `design.md`, "Impacto en contrato"). Se retiran la marca de "spec futura, sin activar", la referencia a la autorización del plan de migración y a sus bloques, y la dependencia de UI-01-mobile (no figura en `feature_list.json`).
- PA-F08-1 y PA-F08-2 vuelven a estar abiertas: la versión anterior las daba por resueltas por un plan que ya no está vigente; ahora requieren confirmación humana.
- R1: se precisan las ramas por versión (31–32 frente a 33+) y la decisión de no descargar modelos. La parte de fallback pasa a R16.
- R2: se quita la mención a `EXTRA_PREFER_OFFLINE`; se añaden `destroy()` y la prohibición de `RecognizerIntent` en actividad externa y de grabación propia.
- R6: se fija la regla de separación al añadir el resultado.
- R7: se añade el paso a no disponible ante errores de idioma (detección en API 31–32).
- R8: se sustituye "conserva `VOICE_ON_DEVICE` aunque se reescriba" por la regla simple y comprobable de PA-F08-1 (vuelve a `TYPED` solo si el motivo queda en blanco).
- R10/R11: se simplifican (sin "generación de permisos" ni "A→B→A"); el comportamiento se mantiene.
- R12: se concreta qué almacenamientos quedan excluidos.
- R14: se retira la mención a dictado de personal (la app es solo del paciente, ADR 0008) y las métricas de UI-01 no trazables a F-08.
- R15 (evidencia separada de pruebas simuladas y en dispositivo): **retirado** porque describe el proceso de verificación, no un comportamiento del sistema comprobable con una prueba; pasa a `design.md` ("Riesgos y pruebas") y a la tarea de QA manual.
- Enmienda de D8 (2026-11-17, aprobada por el humano en el chat): la raíz detiene la guía actual en el callback de "Dictar" antes de llamar al ViewModel y se elimina `beforeListen` del ViewModel, porque la guía se recrea al rotar o cambiar de idioma y el ViewModel sobrevive; se añade T15. R13 no cambia.
- R16: nuevo, separa el fallback a texto (antes dentro de R1).
- RNF: se retiran RNF-12 (roles de personal) y RNF-18 (secretos, sin relación) y se añade RNF-13 (teléfono y tablet).
