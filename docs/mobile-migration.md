# Android: pacientes y personal

Plan completo aprobado expresamente por el usuario mediante «PLEASE IMPLEMENT THIS PLAN» el 2026-10-29 (America/Bogota). La aprobación comprende un APK Kotlin/Compose con accesos separados, una sesión activa y los siete bloques descritos abajo. Los requisitos y diseños de cada slice se materializan antes del código y registran esa aprobación; una decisión nueva fuera del alcance aprobado se consulta por chat.

## Referencias y punto de partida

- Web de referencia: UI-02-web, PR1 integrado en main dc280a9, árbol equivalente al head c812c63.
- API de referencia: 2f2672b, OpenAPI0.4.0. API, web y legacy se consultan en solo lectura.
- Mobile: main actualizado por fast-forward cfc591e→ffea8e0; el equipo ya entregó F-02-mobile y sus correcciones. Se reutilizan perfil, preregistro y pruebas existentes.
- Entorno local preparado fuera del repositorio: JDK21, Gradle9.8.2, Android SDK37 y emuladores API31/API37. local.properties está excluido de Git; API localhost:3000 con adb reverse. Init inicial completo: VERDE, FAIL0/WARN0.

## Alcance aprobado

| Bloque | Resultado |
|---|---|
| F-01-staff-mobile | Bienvenida paciente/personal, login staff, principal Patient/Staff verificado por /me, sesión cifrada única, navegación/guards por rol y limpieza de respuestas/borradores de sesiones anteriores. ADR móvil amplía el enfoque paciente-only; no se cambian los permisos del servidor. |
| UI-01-mobile | Marca, Axiforma, turquesa y componentes Compose con la identidad UI01/UI02; cinco idiomas, temas/modos/tamaño/fuente accesible y guía TTS estática local. Configuración separada de sesión; el cambio de configuración conserva formularios en ViewModel. |
| F-02-mobile | Conservar el trabajo ya entregado por el equipo. Integrar diseño/sesión nueva y mostrar las solicitudes propias y su estado actual reutilizando el GET existente; no se inventa una línea de eventos de preregistro. Documento no editable, datos editables sólo antes de verificación, mayoría de edad/consentimiento y estados sin nivel de triage. |
| F-02-staff-mobile | Recepción: confirmar identidad, rechazar con motivo, buscar/crear pacientes, NN y registro directo. Cuatro roles consultan lista/detalle/eventos; cancelación según RECEPTION/NURSE. Filtros sede/estado y páginas50. DTO/repositorio común de atenciones en core/encounters, sin imports entre features. |
| F-03-mobile | Captura NURSE WAITING_TRIAGE y lectura clínica NURSE/PHYSICIAN; dos forms independientes de vitales/hallazgos y registros append-only. Campos, límites, enums, precisión y payloads exactos del contrato; selecciones explícitas, false válido y opcionales omitidos. Errores localizados,409 revalida/bloquea conservando RAM. |
| UI-02-mobile | Cuatro dashboards de UI02: recepción con indicadores y sede; enfermería con espera/paginación/captura; medicina con tres estados y ocho recientes; administración con tres estados y espera por sede. Counts100+ cuando se llena el límite; sin datos ficticios, prioridades, IA, tiempos ni reportes no publicados. |
| F-08-mobile | Dictado paciente es-CO/es exclusivamente on-device, permiso al usar, revisión/edición del texto y provenance VOICE_ON_DEVICE; alternativa escrita si no existe soporte. Cancelación por pantalla/sesión/background y callbacks antiguos descartados. Sin almacenamiento ni subida de audio. |

## Reglas transversales

- Inicio staff: RECEPTION→Recepción; NURSE→Triage; PHYSICIAN→Medicina/cola; ADMIN→Administración/reportes. NURSE y ADMIN también acceden a cola; reportes sólo ADMIN; clínica sólo NURSE/PHYSICIAN. Las pantallas de destino aún no entregadas se identifican como pendientes durante los bloques intermedios.
- Los dashboards UI02 usan encabezado/resumen/panel/franja pendiente, sin aside de enfermería. Teléfono apila, tablet usa columnas/tablas cuando caben. Una sola marca privada; icono y texto acompañan el color. Dashboards con horas Bogotá sin segundos y sin partir el indicador horario. Español «preregistro».
- Sondeo30s sólo en dashboard visible y app en primer plano; pausa al salir/cerrar/background, sin WorkManager ni reconexión duplicada. Cada consulta resuelve carga/error/vacío/éxito y403 por separado;403 detiene su intervalo. Escrituras sin retry automático.
- Un refresco no borra campos/posición/foco. En error no se presentan datos antiguos como actuales y se bloquean las acciones afectadas hasta revalidar; los borradores permanecen en RAM. Página de enfermería vacía con offset>0 se reconsulta desde0 antes de recalcular el resumen.
- Borradores de paciente y clínicos en ViewModel, sin SavedStateHandle ni persistencia/logs/URL/voz. Rotación/idioma/tema conservan; cambio de atención/sesión y muerte de proceso descartan. Horas, actor y tenant legales vienen del servidor.
- Una feature activa a la vez. Cada bloque es una sesión lógica con spec, implementación, revisión C1–C8, init verde, historial y cierre antes del siguiente. Commits convencionales frecuentes con Refs; ramas codex/<id-feature>; publicación verificada en main de ASCLEPIO-SWNT/asclepio-mobile sin reescritura de historial.

## Verificación y entrega

Regresiones F01/F02, contrato0.4.0, MockWebServer en frontera HTTP, JUnit con prefijos R<n> y espacio, pruebas Compose/instrumentadas, roles/sesiones tardías, errores/paginación/counts/polling, guardados independientes y privacidad. Verificar teléfono/tablet API31/37,320dp, cinco idiomas, temas/modos, texto200%, teclado/TalkBack/movimiento reducido, además de lint, testDebugUnitTest, assembleDebug e init completos.

Entregar APK de pruebas, capturas comparativas con UI02 y matriz de permisos. Distinguir pruebas sintéticas de integración API/PostgreSQL real y reproducción audible real, que dependen de configuración/cuentas/dispositivo compatible. No hay cambios de endpoints ni migraciones.
