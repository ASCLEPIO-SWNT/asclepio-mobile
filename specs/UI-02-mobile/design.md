# UI-02-mobile — Diseño

## Resumen y límites

Spec futura de cuatro dashboards reales, con UI-02-web `dc280a9`/`c812c63` como referencia de lectura y el plan Android aprobado como fuente de adaptación. Se activa tras UI-01/F-02-staff/F-03 cerrados. Los flujos ya implementados siguen siendo la fuente de acciones; no se crean pantallas clínicas, de prioridad o reportes que el contrato no ofrece. Los destinos de inicio MEDICINE y ADMINISTRATION se convierten en dashboards con resúmenes reales y paneles útiles, al igual que RECEPTION/TRIAGE; la franja pendiente señala solo capacidades futuras, no reemplaza esas pantallas. El principal PATIENT y su refresco no cambian.

## Decisiones

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | Header/resumen/panel principal/franja pendiente con una marca privada, Compose adaptativo y sin aside de enfermería. | Dashboard genérico igual para cuatro roles o copiar DOM/CSS web. | Mantiene la composición/identidad con datos y flujos diferentes por rol. |
| D2 | Conteos por una página límite100 y metadato `{cantidad,limitado}`; pendientes/enfermería derivados. | Escanear todas las páginas, inventar total o guardar listas para indicadores. | OpenAPI no publica total y permite hasta100 por petición. |
| D3 | Un coordinador de refresco por dashboard, un reloj30s visible y una petición por key. | Intervalos por widget más invalidaciones/foco que duplican red. | Hace predecible la carga y la pausa por lifecycle. |
| D4 | Estados independientes por lectura,403 desactiva su key y errores de red reintentan en próximo ciclo. | Fallo global o repetir una petición prohibida cada30s. | Los paneles útiles siguen trabajando sin datos obsoletos. |
| D5 | Borradores separados de respuesta remota por id/generación, sin reset por refresco/error. | Reproducir la pérdida de casilla/motivo del desmontaje web. | El plan móvil exige conservar RAM y bloquear acciones hasta revalidar. |
| D6 | Slot/composición raíz y core común; vistas de trabajo existentes reciben el nuevo marco/resumen. | Features importadas entre sí o nuevo framework de caché. | Reutiliza los contratos de bloques anteriores. |

## Modelos y consultas exactas

En `core/encounters`, añadir `EncounterCount(cantidad:Int,limitado:Boolean)` y operaciones de lectura agregada que piden el GET existente y **proyectan inmediatamente** su respuesta. No conservar `EncounterSummaryDto` ni documento/motivo en estado de un contador; el cuerpo HTTP transitorio sigue siendo el del contrato y no se loguea/persiste. `CountKey(status,siteId,generation)` identifica la lectura. No paginar por detrás ni afirmar total real.

| Dashboard | Consultas por ciclo | Datos mostrados |
|---|---|---|
| Recepción | Pendientes por sede opcional + conteo de espera `status=WAITING_TRIAGE&limit=100&offset=0` con misma sede | Número exacto de pendientes derivado de la bandeja; espera acotada; sede; tarjetas y acciones de F-02-staff; Registro directo |
| Enfermería | Lista de espera `status=WAITING_TRIAGE&limit=50&offset=N` y sede opcional | Cifra derivada de offset+n, filtro, tabla/tarjetas y Capturar de F-03, paginación50 |
| Medicina/Cola | Tres conteos globales límite100 por WAITING_TRIAGE/TRIAGED/IN_CARE + lista sin filtros `limit=8&offset=0` | Resumen3 + ocho recientes orden arrivalAt/id descendente recibido; nombre/sede/llegada/estado, Ver atención y Ver todas |
| Administración/Reportes | Tres conteos globales anteriores + una consulta de espera límite100 por sede | Resumen3 + tabla sede/cantidad, sin datos de pacientes, y Ver todas |

`GET public/tenants` se pide/cacha en RAM por sesión para sedes; no se repite cada30s tras éxito. Cada fila de sede es una lectura independiente (no petición por paciente). Recientes proyecta a `{id,nombre,sede,llegada,estado}` en RAM, sin documento ni motivo retenidos para ese panel. Administración solo conserva sedes públicas y metadatos agregados. Los dashboards no piden detail/clinical-capture: navegar al detalle lo hará mediante su ruta autorizada.

Cotas: respuesta de100→`100+`, accesibilidad «Al menos100»;99→99;0→0. Enfermería deriva N=offset+recibidas y limitado=recibidas==50; página completa0→50+, página completa50→100+, última página50+12→62. **Vacía con offset>0 no publica ese offset como total:** cambia offset0, reconsulta y solo entonces actualiza indicador/lista. No petición de conteo adicional para enfermería ni pendientes. La bandeja de pendientes no tiene paginación y su longitud es el dato mostrado.

## Estados, refresco e invalidación

- `DashboardRefreshCoordinator` común en core recibe `activeDashboard`, generación, foreground y colección de `ReadKey`. Un solo Job de reloj por destino: al entrar/retomar visible hace una lectura coordinada y luego ticks30s. Al salir/background/logout cancela el reloj; no WorkManager, alarmas, WebSocket ni tarea persistente. Ninguna fuente de lifecycle/foco/reconexión instala otro intervalo.
- Cada key tiene estado remoto/revisión y como máximo una petición en curso. Tick/manual sobre una petición en curso se coalescen, sin colas de ticks. Cambio de filtro o invalidación aumenta revisión; si hay petición anterior se descarta y se hace una sola lectura posterior necesaria. No dos dueños (VM de F-02/F-03 y dashboard) disparando a la vez la misma consulta: ambos triggers pasan al coordinador o método de refresco coalescido común.
- Actualizar recorre las keys del dashboard, Reintentar solo la key; una key403 detiene sus ticks y no se reintenta automáticamente. Un reintento explícito puede volver a comprobar permiso; cambio de contexto/filtro/sesión crea la key correspondiente.403 de una fila no detiene las demás.401 solo cierra si su generación es la actual; root cancela todos los ciclos. Red/5xx muestra error y conserva elegibilidad para el siguiente tick y botón manual.
- Las invalidaciones `StaffDataChanges` se reutilizan: confirm/direct/reject/cancel afectan bandeja/listas/cifras según sus recursos y captura invalida Encounters para contadores/recientes del dashboard visible. El evento no contiene PHI ni una copia de cifras autoritativas. Las vistas ocultas revalidan al volver; no crean lecturas de fondo. Nunca se reenvía un POST por el scheduler.
- Carga inicial solo afecta su panel/celda;0 es éxito, lista vacía es Empty. Durante refresco con resultado válido se conserva contenido/altura, foco y scroll sin spinner que remonte todo; al completar éxito se actualiza por claves estables. Si el resultado falla, se muestra error en vez de datos antiguos como actuales y se deshabilitan las acciones dependientes; los borradores siguen en sus VMs y reaparecen intactos al revalidar el mismo id. No se vacía el mapa de casillas/rechazos por sustituir `remoteState`.
- Lecturas independientes pueden terminar en distinto orden sin error global. Enfermería contador/panel y recepción pendientes/contador comparten respuesta, por lo que sus estados derivados cambian conjuntamente sin GET extra. No anunciar cada tick como una nueva región viva global; los errores/sucesos accionables conservan semántica accesible.

## Composición por rol y compatibilidad

La raíz conserva `StaffDestinations` de F-01: los homes son RECEPTION, TRIAGE, MEDICINE y ADMINISTRATION para sus roles respectivos. MEDICINE y ADMINISTRATION son destinos propios de inicio con dashboards reales, distintos de los accesos QUEUE y REPORTS. QUEUE se permite a NURSE/PHYSICIAN/ADMIN y REPORTS solo a ADMIN. Sus componentes de resumen/panel pueden reutilizar los de Medicina y Administración según la referencia web, sin fusionar los destinos ni reducir un home a Próximamente. Reusar pantallas/VM de recepción y bandeja, con slots de resumen/marco o composición raíz; ninguna feature importa otra. `core/ui` contiene DashboardHeader/SummaryCell/WorkPanel/PendingStrip presentacionales; core/encounters y coordinador poseen lecturas compartidas, no la UI de una feature.

Recepción muestra tarjetas de pendientes con casilla de identidad y rechazo en la misma tarjeta, y Registro directo sólido/Actualizar secundario. Enfermería usa filas/cards con paciente/documento, sede, llegada y Capturar, sin aside y sin nivel/estado inventado. Medicina muestra nombre pero **ningún documento** de las ocho recientes; estado con icono/texto y enlace detalle/lista. Administración muestra solo sedes/cifras. Cada zona tiene una acción principal. Los resúmenes y paneles reales de Medicina/Cola y Administración/Reportes permanecen visibles y operativos con las lecturas y enlaces de R5/R6. Como complemento, una franja anuncia las capacidades futuras F-06 o F-07 con Pendiente/Próximamente e información estática; solo dentro de esa franja se omiten acciones operativas futuras, gráficas y datos ficticios. No se sustituye el dashboard completo por un marcador de pendiente.

Mantener filtros/página/scroll al volver, inputs escritos y reglas/payloads/error de F-02/F-03. Solo dashboards STAFF pasan de entrada/manual a30s; detalle, captura y paciente siguen su política propia. Un refresh de lectura no debe perder foco ni reiniciar el formulario por la instancia del DTO. Un nuevo id/sesión sí descarta los borradores correspondientes conforme a los slices anteriores.

Identidad/fonts/paletas/iconos/espaciado UI-01 y estructura UI-02 fieles; en compacto se apilan celdas/cards, en tablet columnas/tablas solo si texto/ancho caben, a320dp/texto200%. Encabezados, estado, acciones repetidas y tabla/cards con semántica TalkBack/teclado. Cinco idiomas con placeholders equivalentes; nombres/datos literales. Una marca privada, no repetida en cada panel. Horas del servidor en `America/Bogota`, sin segundos; normalizar espacio del indicador a. m./p. m. a NBSP para no partirlo, sin alterar fecha/hora de origen. Español «Preregistro» sin guion. Guías estáticas específicas por dashboard, indicando30s sin leer cifras, nombres, listas o motivo. Sin animación decorativa fuera del movimiento permitido por sistema.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.4.0→0.4.0**. GET `/api/v1/encounters` con filtros/límites ya publicados, GET `/api/v1/pre-registrations` solo RECEPTION y GET `/api/v1/public/tenants`. No totales/aggregates/endpoints clínicos/reportes nuevos ni mutación modificada. No prioridades, niveles o tiempos de espera derivados de reloj local. Las preguntas F-06/F-07 siguen pendientes para sus slices, sin resolverlas aquí.

## Impacto en datos

**Sin migraciones ni persistencia privada nueva.** Conteos solo cantidad/limitado en RAM; recientes/bandejas solo datos mínimos de la generación actual, nunca DataStore/SavedStateHandle/log/TTS. No guardar respuesta completa en un agregado ni payload por paciente en la tabla admin. El polling se destruye al perder visibilidad/sesión; no trabajo programado ni datos en segundo plano.

## Riesgos y pruebas

- JUnit `R<n> ` y MockWebServer: consultas exactas por dashboard/roles/sede/límite, cantidad0/99/100+, enfermería offset0/50 y50vacía→0 antes de cifra; cero GETextra derivados/detalle/clínica, admin una petición por sede y nada de pacientes en sus estados, medicina sin documento.
- Reloj virtual30s: un solo ciclo visible+foreground, pausa al cambiar ruta/background/logout, reentrada y recomposición/configuración sin duplicados, tick/manual/invalidación concurrentes coalescidos, respuesta antigua descartada,403 detiene solo key, red continúa al siguiente tick y reintento manual solo key,401 viejo no cierra otra sesión. Ningún POST automático.
- Estados independientes y retención real: foco en checkbox/motivo y scroll medio, llega tick exitoso o fallido; draft no se pierde, datos fallidos no aparecen como actuales y acciones bloqueadas hasta revalidación. Probar eliminación/resolución de fila e identidad de contexto al cambiar filtros.
- Capturas de cuatro dashboards equivalentes a referencia, claro/oscuro, teléfono320dp/tablet API31/37, cinco idiomas, texto200/modos/fuentes/teclado/TalkBack/movimiento reducido. Hora fija UTC, salida Bogotá sin segundos/NBSP. Guía solo instrucciones estáticas; pruebas de voz real se distinguen de controlador simulado.
- Al activar: regresión F-01/F-02/F-03 y paciente sin polling, lint/testDebugUnitTest/assembleDebug/connectedDebugAndroidTest/init, tabla R→prueba y reviewerC1–C8. Separar servidor fixture contractual de API/DB real que requiera cuentas/configuración; no inventar evidencia ni modificar backend.
