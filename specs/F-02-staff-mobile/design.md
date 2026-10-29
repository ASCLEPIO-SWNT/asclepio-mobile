# F-02-staff-mobile — Diseño

## Resumen y dependencias

Slice futuro de recepción y consulta STAFF aprobado en `docs/mobile-migration.md`; se activa solo después del cierre de F-01-staff-mobile y UI-01-mobile. No modifica sus specs ni implementa su código anticipadamente. Se conserva íntegro F-02-mobile del paciente. La app comparte atenciones STAFF en `core/encounters`, usa features separadas de recepción/consulta y solo la raíz las compone. Se consulta API 0.4.0 y web F-02/UI-02 en lectura.

## Decisiones

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | DTOs/API/repositorio compartidos de atenciones en `core/encounters`; recepción específica en `feature/reception`. | Hacer que triage/recepción importen una feature de otra o reutilizar APIs paciente. | Prepara F-03/UI-02 sin violar la separación existente. |
| D2 | `authenticatedCall` STAFF con roles y generación/cabecera capturados de F-01-staff. | Interceptar siempre el token más reciente o confiar solo en botones ocultos. | Las operaciones viejas no pueden afectar a otra sesión. |
| D3 | Estado remoto y borradores RAM separados; refrescar no recrea el mapa de borradores por id. | Reiniciar casillas y motivos al recibir cada lista o persistirlos. | Cumple la retención aprobada y conserva privacidad. |
| D4 | Invalidación local mínima mediante SharedFlow sin replay con generación/recurso/id opcional; entrada revalida. | Framework de caché nuevo, listas globales persistidas o eventos de dominio inventados. | Coordina pantallas y futuras features sin dependencia cruzada. |
| D5 | Refresco al entrar/manual y tras invalidación de mutaciones; sin intervalos ni WorkManager. | Adelantar los 30 s de UI-02 a este bloque. | El sondeo pertenece al slice posterior y solo a dashboards. |
| D6 | Validaciones y payloads de OpenAPI/PA-F02 exactos, con modelos STAFF distintos cuando difieren de cuenta paciente. | Aplicar mayoría de edad del alta paciente o documento CC/CE/PA a todos los pacientes registrados. | Recepción admite menores, TI/RC y NN conforme al backend. |
| D7 | Listas/acciones no vigentes se bloquean; se conserva borrador pero no se muestra caché anterior como actual. | Permitir confirmar/cancelar sobre una lectura fallida o borrar datos escritos al fallar. | Reduce acciones sobre recursos resueltos sin pérdida de trabajo RAM. |

## Propiedad e interfaces comunes

- `core/encounters`: DTOs serializables `StaffPatientDto`, `EncounterSummaryDto`, `EncounterDto`, `EncounterEventDto`, `StaffRefDto`, `EncounterListDto` y enums/códigos compartidos; `EncountersApi` con list/detail/createDirect/cancel; `EncountersRepository` con métodos tipados y `StaffDataChanges`. Los eventos usan id textual (bigint del servidor), tipo tolerante y payload JsonObject; la UI no lo ejecuta ni lo imprime como JSON. Campos desconocidos futuros se ignoran sin autorizar acciones nuevas.
- `EncountersRepository.list(filters)` acepta estado/sede opcionales, limit/offset contractuales; el UI de este bloque fija50. `detail(id)` sirve a los cuatro roles. `createDirect(request)` exige RECEPTION y `cancel(id,reason)` RECEPTION/NURSE. La capa común depende de core/auth/network, nunca de features. F-03/UI-02 futuros reutilizan estos DTO/repositorios sin copiar la lógica.
- `feature/reception/data`: `ReceptionApi` contiene pendientes/confirm/reject y búsqueda/alta pacientes; `ReceptionRepository` lo usa junto con `EncountersRepository`. No importa `feature/patients`, `feature/encounters` paciente ni otra feature. Los DTO de paciente STAFF incluyen CC/TI/CE/PA/RC y NN; no ampliar el enum de credenciales de cuenta paciente para que acepte documentos no publicados allí.
- Sedes: servicio/repositorio en core filtra la lista pública por tenant del principal STAFF. Se puede cachear en RAM por generación; nunca usar tenant/sede recordados de PATIENT ni enviar tenantId en operaciones STAFF. Un fallo de catálogo deshabilita registro directo sin sede; bandeja/lista pueden consultarse sin filtro con alerta de sedes.
- `StaffDataChange(generation, resources, encounterId?)` y `StaffDataChanges.changes: SharedFlow` sin replay: solo invalidaciones, no listas, motivos o datos de paciente. Los consumidores filtran generación. Consultas activas afectadas refrescan, las ocultas al entrar. Coalescer invalidaciones mientras hay lectura y ejecutar como máximo la revalidación necesaria; no lanzar un intervalo. Un evento antiguo nunca limpia ni refresca una sesión nueva.

## Matriz de permisos y endpoints

Todos exigen sesión STAFF verificada; guard de raíz **y** repositorio antes de red. La API sigue autorizando independientemente.

| Operación | RECEPTION | NURSE | PHYSICIAN | ADMIN |
|---|---|---|---|---|
| GET/POST `/api/v1/patients` | Sí | No | No | No |
| GET `/api/v1/pre-registrations` | Sí | No | No | No |
| POST `/api/v1/pre-registrations/{id}/confirm` o `/reject` | Sí | No | No | No |
| POST `/api/v1/encounters` | Sí | No | No | No |
| GET `/api/v1/encounters` o `/{id}` | Sí | Sí | Sí | Sí |
| POST `/api/v1/encounters/{id}/cancel` | Sí | Sí | No | No |

PATIENT no monta ninguna de estas pantallas ni envía sus peticiones. No se llama a `/clinical-capture` en este bloque, ni siquiera desde detalle; F-03 añadirá su guard NURSE/PHYSICIAN. No transición genérica a TRIAGED/IN_CARE/CLOSED ni edición/fusión/eliminación de paciente.

## Flujos y estado

### Recepción y pendiente

`ReceptionViewModel` mantiene `remoteState` (Loading/Success/Empty/Error/Forbidden) por filtro y un mapa de borradores `{ id -> identityChecked, rejecting, reason, actionState }`. Las casillas empiezan falsas, aun si la cuenta venía verificada; se requiere verificar al paciente presente para esta confirmación. Los tipos de eventos/contextos nunca contienen el motivo privado. El filtro por sede es opcional; las filas conservan el orden del servidor sin paginación de pendientes inventada.

Refrescar el mismo contexto conserva borradores, foco y scroll mediante claves estables. Un error oculta los datos remotos como vigentes y bloquea acciones, pero retiene los borradores en VM. Tras éxito de revalidación se restablecen solo acciones de ids actuales; los ya resueltos no pueden confirmarse/rechazarse. Un filtro/petición anterior que llega tarde no reemplaza el nuevo resultado. Al cambiar filtro no se aplica el borrador de un id a otro; valores conservados pertenecen siempre al id y generación originales.

- Confirmación: POST cuerpo exacto `{ identityVerified:true }`, sin hora ni paciente. 201 muestra llegada `arrivalAt` de la respuesta (Bogotá), enlace al detalle y borra solo el borrador de ese pendiente; notifica Pending/Encounters/Detail(id). La API fija la hora legal y genera eventos.
- Rechazo: campo de motivo y acción Volver que cancela la edición sin red. `trim().length` 1–500; POST exacto `{reason}`. 200 borra solo ese borrador, avisa éxito y Pending. 409/404 indican resuelto/no existe y revalidan bandeja; ningún reintento de escritura automático.
- Al fallar un refresco con casilla/motivo escritos, esos valores no se borran ni se convierten en datos guardados. Se recuperan al revalidar el mismo id; logout/cambio de sesión los destruye. La desaparición/resolución de ese pendiente descarta su borrador.

### Búsqueda, alta y registro directo

`DirectRegistrationViewModel` conserva búsqueda, resultado, formulario de alta, paciente seleccionado, sede/motivo y errores en RAM del flujo. Búsqueda exacta con documento trim1–64 y cinco tipos STAFF. Cambiar documento/mode invalida selección anterior. Respuesta `[]` muestra vacío con Crear paciente y documento fijado; `[paciente]` muestra/selecciona ese objeto. Registrar NN no requiere fingir una búsqueda.

| Modo | Validación de alta | Cuerpo POST `/patients` |
|---|---|---|
| Con documento | Tipo+número válidos, nombre trim1–200, fecha civil válida no futura, sexo M/F/X; menores permitidos | Solo documentType/documentNumber/fullName/birthDate/sex |
| NN | Sin documento; nombre trim≤200 opcional, sexo obligatorio, exactamente fecha no futura o entero0–120 | Solo sex + una de birthDate/estimatedAgeYears + fullName si no vacío |

Edad0 es válida y no se pierde por comprobación de truthiness. No enviar un nombre de presentación como «Paciente NN» si no lo escribió el usuario. Fecha civil usa helper DatePicker UTC existente y `YYYY-MM-DD`, no desplaza zona ni aplica mayoría de edad paciente. Si se valida futuro localmente, usar fecha de Bogotá del Clock; backend sigue siendo autoridad. 201 selecciona objeto servidor; 409 documento duplicado, 422 fecha futura, 400 campos conocidos; todos los fallos conservan formulario.

Llegada directa exige paciente seleccionado+sede+motivo trim1–2000; POST exactamente `{patientId,siteId,chiefComplaintText,complaintSource:TYPED}`. No hay dictado staff en este bloque. En 201 se limpia solo el formulario exitoso, se muestra/navega al detalle servidor y notifica Encounters/Detail(id)/Pending para lecturas afectadas. 409 indica preregistro vigente/atención activa y ofrece revisar recepción; no simula una llegada exitosa. 422 revalida referencias paciente/sede sin perder motivo. Nunca se manda arrivalAt, tenantId, registeredBy, status u origin.

### Lista, detalle y cancelación

`feature/staffencounters` contiene la UI/VM de lista y detalle; reutiliza `core/encounters`, UI-01 y guard STAFF. La raíz crea rutas con filtros no privados y un id opaco de detalle, sin pacientes/DTO/motivos serializados. Al abrir otro id no se arrastra motivo de cancelación ni avisos; el cambio de contexto descarta esos borradores privados. Mantener filtro de sede/estado/offset en VM del listado al ir/volver del detalle, incluyendo scroll/foco pertinentes, sin persistir una lista de pacientes.

- Lista pide `limit=50`, offset≥0, estado/sede solo si elegidos. Anterior resta50 con mínimo0; siguiente habilitado si llegan50 y suma50; no inventar total exacto ni paginación de pendientes. Cambiar sede/estado reinicia offset0. Usar orden `arrivalAt DESC/id DESC` recibido; refresco manual/entrada sin polling. Mostrar llegada, paciente/documento, sede, estado, origen y acceso al detalle; las cifras/KPIs/dashboard se dejan para UI-02.
- Detalle muestra los campos contractuales y `events` en orden `occurredAt/id` recibido, sin reordenar bigint como número ni inventar eventos. Etiquetas conocidas y genérica para tipo nuevo, nombre de staff o etiqueta Paciente/Sistema. La carga de datos clínicos/event payload no se amplía a RECEPTION/ADMIN por compartir vista. No imprimir raw payload ni valores clínicos inexistentes.
- Solo RECEPTION/NURSE y estado conocido no terminal vigente permiten abrir cancelación; motivo trim1–500, confirmar/cancelar edición y POST exacto `{reason}`. 200 usa la respuesta como resultado y revalida Detail(id)/Encounters/Pending, con éxito. 409 revalida estado antes de permitir una nueva acción explícita; CLOSED/CANCELLED/estado desconocido o error de revalidación mantiene bloqueado. 404 retira contexto, 403 lo vuelve no disponible. Sin transición adicional ni DELETE/PUT.

## Errores, invalidación y privacidad

- Todos los POST son una sola petición por pulsación válida, sin retry automático ni optimismo; deshabilitar la misma acción mientras corre. Las consultas paralelas/paneles independientes no deben bloquear toda la app al fallar un catálogo; sí las acciones que dependen del dato no validado.
- `400/422` usan `errors[].field` para mensajes locales UI-01, nunca textos del servidor; desconocidos van a alerta general. `409` se explica por operación y revalida el contexto. Rechazo/confirmación obsoletos no se pueden volver a enviar hasta revalidación. Un alta duplicada no borra búsqueda/documento. Red/5xx conservan borradores y ofrecen reintento **explícito**; 429 respeta bloqueo de la acción por Retry-After como la infraestructura actual.
- 401 usa `authenticatedCall` y logout solo si corresponde a la generación vigente; 403 y 404 se presentan sin datos anteriores como actuales y sin bucles de consulta. Un éxito/error tardío con generación o filtro/id antiguo se descarta. No operar con el token de otra sesión.
- Invalidaciones mínimas tras éxito: confirm/direct→Pending+Encounters+Detail nuevo; reject→Pending; cancel→Encounters+Detail afectado+Pending. Alta paciente selecciona respuesta sin cache global de búsquedas. El detalle siempre recupera eventos de API, nunca escribe eventos/auditoría desde el cliente. En 409/404 de mutaciones de recursos se refrescan las mismas consultas afectadas según contexto, sin publicar éxito.
- DTOs/UiState sensibles enmascaran identidad/motivos en toString; datos privados no en DataStore/SavedStateHandle/rememberSaveable/logs/guía. Locales/temas/rotación retienen VM, cambio de sesión/id y muerte de proceso descartan. No datos clínicos en URL/NavKey serializado; un id opaco de ruta y filtros de sede/estado/offset no contienen campos personales.
- Cinco idiomas, tipografías/modos/texto200/TalkBack/foco y layouts UI-01 existentes, español «preregistro». La guía de rutas usa instrucciones fijas, sin nombres/motivos/documentos. Una marca privada y una acción principal por zona; cambios funcionales no reintroducen apartados antiguos del legacy.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.4.0 → 0.4.0**. Se añaden al cliente Android solo consumos STAFF ya publicados: GET/POST `/patients`; GET `/pre-registrations`; POST `/{id}/confirm|reject`; GET/POST `/encounters`; GET `/{id}`; POST `/{id}/cancel`, todos bajo `/api/v1`. Request/response siguen sus esquemas y errores `application/problem+json`. No endpoint, query, evento, campo de tiempo ni migración nuevo. Kotlin DTOs se verifican contra snapshot con MockWebServer. API, móvil paciente, web y legacy no se reescriben para obtener estos flujos.

## Impacto en datos

**Sin impacto en servidor ni migraciones.** La API inserta pacientes/atenciones/eventos/auditoría y establece las horas legales. La app solo presenta horas UTC del servidor en Bogotá y fechas civiles sin desplazamiento. Estado remoto y borradores son RAM de una generación; invalidaciones comparten solo recurso/id, no pacientes/listas. Sin persistencia adicional, audio, PHI en logs ni datos de tenant/actor enviados desde cliente.

## Riesgos y pruebas

- MockWebServer y pruebas Kotlin `R<n> ` para matriz de roles en frontera HTTP, payloads exactos, scopes/generaciones y ausencia de peticiones prohibidas. Regresión patient-only ApiScopeTest y F-01/F-02; no mockear repositorios para omitir autorizaciones.
- Recepción: filtro/sedes/order/empty/error; identidad empieza falsa, true obligatorio, llegada hora servidor; rechazo1/500/501/espacios; casos 409/404/403/red; refresco exitoso conserva casilla/motivo/scroll/foco y fallido conserva RAM pero bloquea acciones hasta revalidar. Filtro/id/sesión viejos no pisan vista nueva.
- Alta/búsqueda/NN: cero/un resultado, documento trim64/65, nombres200/201, menor aceptado, fecha futura, exclusión de fecha/edad, edades0/120/121/fracción y sex obligatorio; JSON sin documentos NN/campos extra y selección no reutilizada tras cambiar documento. Directo motivo1/2000/2001, fuenteTYPED, 201/409/422, una petición sin retries.
- Lista/detalle: páginas0/50 y retorno con filtros, eventos ordenados e id bigint textual, tipos desconocidos, roles de cancelación y estado terminal/desconocido, éxito e invalidación cruzada, fallos sin datos obsoletos y sin clínica adicional. Probar ambos sentidos de navegación y otro usuario con respuestas pendientes.
- UI01 preferencias/idiomas/rotación no borran RAM; logout/cambio id/proceso sí. Instrumentación teléfono/tablet API31/37/320dp/texto200/TalkBack/teclado, capturas con referencia y estados relevantes. lint/testDebugUnitTest/assembleDebug/connectedDebugAndroidTest/init y reviewer al activar el slice; API/DB real condicionada a cuentas/entorno y registrada separadamente.
