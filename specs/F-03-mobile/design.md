# F-03-mobile — Diseño

## Resumen y dependencias

Spec futura del bloque de captura NURSE y lectura NURSE/PHYSICIAN autorizado en `docs/mobile-migration.md`. Se activa después de F-02-staff y UI-01 verificados; sus specs/interfaces no se modifican aquí. `core/encounters` incorpora los DTO/API/repositorio clínicos compartidos y las invalidaciones del bloque anterior; `feature/triage` contiene bandeja/captura/historial Compose/VM. La raíz compone el historial en el detalle general mediante slot, sin imports entre features.

## Decisiones

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | Reusar `EncountersRepository`, sesión/generación y `StaffDataChanges`; ampliar core/encounters con captura clínica. | Clonar la lista/detalle en triage o importar otra feature. | Mantiene un contrato común para F-02/F-03/UI-02. |
| D2 | Dos formularios independientes en RAM, resultados remotos separados y reset solo del POST exitoso. | Un único POST compuesto, borrador persistido o reset global tras refresco. | El backend guarda tomas/lotes por separado. |
| D3 | Lista en espera con sede/páginas50 y lectura de entrada/manual/invalidación. | Adelantar sondeo30s/KPIs/aside del dashboard. | UI-02 posterior contiene el sondeo y resúmenes STAFF. |
| D4 | Reglas exactas de snapshot0.4 y decisiones PA-F03, con una tabla cliente contrastada por pruebas. | Reglas nuevas, puntuación NEWS2 o importar runtime de web. | Son límites técnicos provisionales publicados, no clasificación clínica. |
| D5 | Gate de escritura por rol/id/generación y revisión de contexto+captura; tras conflicto solo lecturas posteriores habilitan. | Rehabilitar con cualquier respuesta WAITING_TRIAGE cacheada o iniciada antes del409. | Cierra la carrera de revalidación y respuestas tardías. |
| D6 | Campos de error del backend se interpretan por ruta, con recursos locales; estructuras de filas se fijan durante su POST. | Mostrar texto backend o aplicar un índice viejo a una fila reordenada. | Conserva localización y errores en la fila correcta. |

## Reglas publicadas del contrato0.4.0

Fuente: `../asclepio-api/openapi/asclepio-api.v1.json` y PA-F03-1–4. **PROVISIONAL — no validado clínicamente, revisar con asesor clínico.** No se calculan escalas ni se cambia la validez del backend.

| Campo | Tipo/unidad | Obligatorio | Rango inclusivo |
|---|---|---|---|
| respiratoryRate | entero/rpm | Sí |4–80|
| oxygenSaturation | entero/% | Sí |50–100|
| supplementalOxygen | booleano, sí/no explícito | Sí |dominio|
| systolicBp | entero/mmHg | Sí |40–300|
| heartRate | entero/lpm | Sí |20–300|
| consciousness | A/C/V/P/U, elección explícita | Sí |dominio|
| temperatureC | número/máximo1decimal/°C | Sí |25.0–45.0|
| diastolicBp | entero/mmHg | No |20–200|
| glucoseMgDl | entero/mg/dL | No |20–600|

Si hay diastólica, sistólica>diastólica. Campos numéricos de entrada empiezan como texto vacío; booleano/enum son nullable hasta elegir. No coaccionar vacío a0, no preseleccionar oxígeno/conciencia y no omitir false. Temperatura acepta separador decimal del idioma (coma/punto, sin separadores de miles), normaliza a valor numérico y verifica precisión sin errores de coma flotante; límites e integer se comprueban antes de construir el request. Opcionales vacíos quedan ausentes en JSON, no null. Un valor inválido impide la toma entera.

Hallazgos: lote1–20, `kind` SYMPTOM/FINDING/COMPLAINT_NOTE y `description.trim()`1–500. Filas editables antes del envío, con claves locales estables solo RAM; no se serializan estas claves. Original de motivo es inmutable; notas son nuevas filas COMPLAINT_NOTE. Kotlin DTOs/request no añaden horarios ni responsables; las propiedades booleanas obligatorias no llevan un default que haga desaparecer false al serializar con encodeDefaults=false.

## Interfaces compartidas y arquitectura

- `core/encounters`: `ClinicalCaptureApi`, `ClinicalCaptureRepository`, DTOs `CreateVitalSignsRequest/VitalSignsDto`, `CreateSymptomFindingsRequest/SymptomFindingDto/SymptomFindingListDto`, `ClinicalCaptureDto`, conciencia y tipo de hallazgo. `getCapture(id)` permite NURSE/PHYSICIAN; `recordVitalSigns(id,request)` y `recordFindings(id,request)` solo NURSE. Todo pasa por authenticatedCall STAFF con cabecera/token/generación capturados del bloque F-01, no por APIs paciente.
- `ClinicalRules` central conserva la tabla y validadores puros. Una prueba JVM compara tipo/required/min/max/enums/longitudes con el snapshot readonly0.4; precisión1decimal/coherencia están expresamente documentadas y testeadas, sin parsear prosa ni generar criterios nuevos. El snapshot completo no se empaqueta en el APK. No nuevas librerías clínicas/deformularios ni Zod/TanStack en Android.
- Ampliar `StaffDataChange` del bloque anterior con recurso ClinicalCapture(id); éxito emite ese recurso más Detail(id)/Encounters. Detail recupera eventos y Context de API, no los crea el cliente. Los resúmenes futuros de UI-02 observarán Encounters; no se implementan ahora. Los consumidores filtran generación y coalescen lecturas/invalidaciones sin replay de PHI ni framework nuevo.
- `feature/triage/ui`: `TriageTrayViewModel`, `ClinicalCaptureViewModel`, `ClinicalHistoryViewModel` y componentes puros que reciben estado/callbacks. El VM de captura conserva `VitalSignsFormState` y `FindingsFormState` independientes, con estados de envío/errores/éxito propios, y contexto/historial remoto separados.
- Para el detalle de F-02-staff, la raíz proporciona un slot `clinicalHistory` y callback Capturar a la pantalla existente. Solo si principal STAFF y rol NURSE/PHYSICIAN monta VM/pane de historial; RECEPTION/ADMIN no montan el slot ni ejecutan GET. NURSE con detalle vigente WAITING_TRIAGE puede abrir captura; PHYSICIAN solo lee. Ninguna feature importa `feature/triage` desde otra: la raíz compone las dos.

## Bandeja, contexto y revalidación

Bandeja NURSE reutiliza `EncountersRepository.list` con status fijo WAITING_TRIAGE, limit50, offset≥0 y sede opcional del tenant. Muestra paciente/documento, sede, llegada servidor y acción Capturar; filtros/página conservados al volver. Cambiar sede→offset0; anterior resta50 mínimo0 y siguiente solo si llegan50. Se carga al entrar y manualmente, con invalidación tras escrituras; no polling/WorkManager/counts/prioridades. Catálogo de sede fallido muestra error y deshabilita selector, permitiendo lista sin filtro. Resultado viejo de otro filtro se descarta.

Captura se compone para un id/generación: contexto de `EncountersRepository.detail(id)` y `ClinicalCaptureRepository.getCapture(id)`. Se muestran paciente, sede, estado, motivo original/fuente literal e historial. No habilitar POST hasta ambos resultados actuales de esa misma revisión y WAITING_TRIAGE; si discrepan, bloquear y revalidar explícitamente. Estado distinto mantiene historial autorizado de solo lectura.

Mantener `readRevision` local que aumenta al invalidar contexto o recibir409. Cada request captura id/generación/revisión; una respuesta anterior nunca satisface la nueva comprobación. En409: gate pasa a Revalidating antes de pedir contexto+captura; se descartan lecturas que estaban en curso desde antes, se lanzan/coalescen las posteriores y se habilita solo si ambas respuestas nuevas son válidas y de espera. Error/403/404 o estado no espera deja gate bloqueado. El servidor sigue resolviendo una carrera posterior mediante409; no se pretende sustituir esa transacción.

Tras201 se limpia **solo** el formulario enviado y se avisa éxito; se invalida/refresca contexto/captura/detalle/eventos/listas. El otro borrador no se resetea al recibir nuevos objetos remotos. Dos POST independientes pueden terminar en distinto orden: cada uno solo cambia su estado si sigue vigente y las lecturas anteriores a la última invalidación no pisan la versión actual. Una nueva toma se muestra desde lectura del servidor, sin inserción optimista.

## Historial, errores y borradores

- Historial muestra todas las tomas/hallazgos en el orden contractual ascendente recibido de recordedAt/id, unidades, responsable y hora Bogotá. Null de signos opcionales se muestra como ausencia; no 0 inventado. No invertir orden, editar, borrar ni ofrecer diagnóstico/nivel. El motivo original no se modifica al guardar COMPLAINT_NOTE.
- Las lecturas tienen Loading/Empty/Success/Error/Forbidden/NotFound explícitos por panel. Un error no muestra las tomas/caché anteriores como actuales; captura no habilita escrituras hasta revalidar, pero los dos borradores permanecen RAM. Los paneles válidos del detalle general pueden seguir mostrando su resultado propio autorizado; un POST403/404 de captura retira el contexto clínico y las acciones afectadas.
- Campos de400/422: nombres publicados de signos; `items`→error de lote, `items.<n>`→fila, `items.<n>.kind` o `.description`→control de la fila enviada. El backend usa notación con puntos (comprobada en sus pruebas DTO); no depender de que venga `items[n]`. Índices inválidos o rutas desconocidas→alerta general. No mostrar errors[].message/title/detail; traducir mediante recursos de UI01. Si se modifica la estructura después de un error, limpiar errores de índices que ya no correspondan. Durante POST del lote no se añaden/quitan filas ni cambian datos de ese formulario.
- Red/5xx conserva borradores y muestra error sin retry; reenvío requiere acción explícita. No hay endpoint de idempotencia nuevo.409 revalida como arriba.401 usa cierre de generación vigente;403/404 marca permiso/noexiste sin mantener contexto viejo accionable. Un callback de otro id/sesión/revisión se ignora; el viejo401 no cierra otro login.
- No PHI en DataStore/SavedStateHandle/rememberSaveable/logs/NavKey/TTS; DTO/UiState sensibles con toString enmascarado. Configuración conserva VM por entrada y generación, pero cambiar atención/sesión o perder proceso descarta borradores. Navegar A→B no reutiliza valores/errores de A en B; regreso tras cambio de contexto inicia borrador nuevo, sin restaurarlo desde estado serializado.
- Cinco idiomas/errores/semántica, tipos/valores wire no traducidos, paciente/motivo/descripciones literales. Reusar estilos/fuentes/preferencias/UI01 para 320dp/tablet/texto200/IME/TalkBack/teclado y foco; sin aside/KPIs nuevos. Guía estática específica de bandeja/captura/detalle clínico, sin nombres, cifras clínicas ni promesa de actualización periódica en este bloque; el controlador UI01 cancela/reset al cambiar contexto.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.4.0→0.4.0**: GET `/api/v1/encounters`/`{id}` ya compartidos y tres consumos publicados: POST `/{id}/vital-signs`→201 VitalSignsDto; POST `/{id}/symptom-findings`→201 SymptomFindingListDto; GET `/{id}/clinical-capture`→200 ClinicalCaptureDto, todos bajo encounters. Requests exactos, errores problem+json y roles de API existentes. No endpoint nuevo, campo clínico adicional, PUT/PATCH/DELETE, actor/hora/tenant enviado ni cambios en API/web/legacy.

## Impacto en datos

**Sin migraciones ni cambios de servidor.** La API hace inserciones append-only y eventos/auditoría en transacción. El móvil muestra las horas y responsables devueltos, no calcula horas legales. Datos y borradores solo RAM por generación/contexto; el evento de invalidación solo lleva recurso/id opaco. No persistencia/listas globales, audio ni clínicas en logs/guía.

## Riesgos y pruebas

- JUnit `R<n> ` y MockWebServer real: roles completos y cero GET clínico para RECEPTION/ADMIN/PATIENT, cero POST PHYSICIAN; contrato de lista/captura y DTO/enums/rangos. Siete obligatorios ausentes por separado, elecciones explícitas, false válido, enteros, temperatura coma/punto/precisión, mínimo/máximo y fuera de cada rango, diastólica opcional o sys≤dia, opcionales vacíos omitidos.
- Hallazgos1/20/21, trim/blanco/500/501 y tipos; JSONexacto y sin campos extra.400/422 en segundo elemento y lote/fila/ruta desconocida, foco/error localizado e índices estables. Un POST por envío, sin retry ni optimismo; éxito limpia solo su formulario, respuestas invertidas conservan el otro borrador e invalidan correctamente.
- Conflicto/carrera: GETWAITING iniciado antes del409 termina después de GETCANCELLED nuevo; no reabre gate. Error de revalidación conserva borradores y bloqueo; status distintos entre detalle/captura; otro POST concurrente y nueva revisión,403/404 unavailable,401 viejo y sesiónA/B, idA/B, red con borrador intacto.
- Historial acumulado con fechas/responsable/eventos, null opcionales, orden por servidor, motivo original intacto, sin edición. Queryfallida no muestra caché como vigente. Bandeja sedes/p50/retorno/manual/sin temporizador; pacienteF02 y recepciónF02staff regresión.
- UI01: cinco idiomas/temas/rotación/escala no borran inputs; otro id/sesión/proceso sí. Teléfono/tablet API31/37/320dp/texto200/TalkBack/IME/teclado/movimiento reducido; guía recibe solo instrucciones estáticas. Capturas comparativas con referencia web equivalente.
- Al activar: lint/testDebugUnitTest/assembleDebug/connectedDebugAndroidTest/init y reviewer C1–C8, trazabilidad y matriz permisos. Distinguir fixtures contractuales de API/DB real pendiente de cuentas/configuración; no modificar servidor o base para fabricar una validación real.
