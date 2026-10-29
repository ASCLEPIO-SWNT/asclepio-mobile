# F-01-staff-mobile — Diseño

## Resumen

Se amplía el núcleo de sesión y navegación para una APK con dos principales, conservando el negocio paciente F-01/F-02. El login STAFF vive en `feature/staffaccount`; identidad, guardas y sesión compartidas en `core/auth`; solo la raíz compone varias features. Es la materialización del bloque aprobado el 2026-10-29, no un cambio de API ni una reapertura de F-02. La línea base inicial ya está verde (221 pruebas JVM); el líder registra evidencia y autorización en progreso.

## Decisiones

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | Una sesión y un principal sellado Patient/Staff verificado por `/me`, con generación local. | Dos tokens activos o inferir permisos del botón elegido/JWT. | Separa ámbitos y permite invalidar resultados antiguos. |
| D2 | Mantener solo token cifrado/expiración y datos públicos IPS/sede ya existentes en disco; derivar principal/rol de `/me` en cada restauración. | Persistir perfil, rol o listas para abrir contenido antes de verificar. | No requiere migración de DataStore ni guarda PHI adicional. |
| D3 | STAFF no necesita tenant/sede seleccionados; PATIENT conserva selección y validación de tenant. | Obligar a staff a pasar el selector de paciente. | El tenant staff viene del servidor. |
| D4 | Capturar token/generación antes de una petición protegida y comparar generación al resolver; Authorization de esa operación es explícita/inmutable. | Leer siempre el token actual al ejecutar una petición antigua. | Evita enviar datos de A usando credenciales nuevas de B. |
| D5 | Reiniciar pila y ámbito ViewModel al cambiar generación autenticada; `refresh` mantiene identidad estable si la sesión ya está verificada en este proceso y sigue vigente. | Reutilizar ViewModels entre usuarios o destruir formularios en cada rotación. | Aísla sesión sin perder configuración en memoria. |
| D6 | Conservar Navigation 3, DI manual, StateFlow, Retrofit/kotlinx y Keystore/DataStore. | Nueva librería de auth/DI o reescribir pacientes. | La base ya aporta las piezas necesarias. |
| D7 | Recursos españoles y destinos staff provisionales con guards reales, sin pedir datos funcionales nuevos. | Adelantar UI-01/F-02-staff/F-03 o simular dashboards. | Mantiene una feature activa por bloque. |

## Interfaces de sesión y aislamiento

### Modelos en `core/auth`

- `AccessMode { PATIENT, STAFF }` y `StaffRole { RECEPTION, NURSE, PHYSICIAN, ADMIN }`, mapeo estricto desde códigos publicados.
- `VerifiedPrincipal`: `Patient` con ids/tenant y campos paciente de `/me`, más IPS/sede recordadas necesarias para su inicio; `Staff` con id, tenantId, fullName y rol reconocido. El DTO de `/me` puede ser plano/tolerante a campos desconocidos, pero su conversión a este modelo exige una variante completa válida. No se guarda ni imprime el perfil.
- `SessionState` representa Loading/Welcome, entrada Patient (con o sin tenant), entrada Staff, verificación, error de verificación y `Authenticated(principal, generation)`. Los nombres internos pueden conservar adaptadores de los estados anteriores, pero solo `Authenticated` con principal completo permite montar contenido protegido. No hay estado LoggedIn basado únicamente en la existencia del token.
- `SessionManager.state: StateFlow<SessionState>`, `rememberedTenant()`, `selectAccess(mode)`, `refresh()`, `logout()` y `isCurrent(generation)` centralizan transiciones. `selectAccess` desde una sesión requiere la confirmación de cierre; sin sesión solo cambia entrada. Al cerrar se conserva la IPS paciente y se vuelve al login de ese tipo, con acción de volver a bienvenida/cambiar acceso.
- `beginLogin(mode)` entrega un intento con generación; `completeLogin(attempt, token, expiresAt, me)` acepta únicamente el intento vigente, modo/tenant correctos, rol conocido y expiración futura. No publica ni persiste token antes de `/me`. El acceso paciente existente usa esta misma finalización después de su `/me` correcto; alta/consentimiento no cambian. `AccessTokenDto` común se promueve a core solo si hace falta evitar imports entre features.

### Restauración, vencimiento y respuestas antiguas

1. `refresh()` lee token/expiración **antes** de exigir IPS paciente. Si no hay token muestra bienvenida; el acceso elegido sin sesión vive en memoria. Token ilegible/vencido se elimina. Con token restaurado vigente muestra verificación, sin montar pantallas privadas, y consulta `/me` con ese Bearer.
2. `/me` STAFF conocido entra a su inicio aunque no exista IPS recordada. PATIENT exige IPS recordada y tenant coincidente; si no se cumple, se descarta sesión y se vuelve al acceso. Un principal/rol desconocido nunca autoriza. No hay migración de las seis claves existentes: una sesión paciente anterior se clasifica por `/me`.
3. Red/5xx al restaurar conserva el cifrado, ofrece Reintentar/Cerrar sesión y no monta contenido privado. Un 401 borra la sesión. Una sesión ya verificada en RAM se conserva al rotar/volver a primer plano si sigue vigente; se comprueba vencimiento local y no se duplica `/me` por recomposición.
4. El gestor programa y cancela una tarea de expiración por generación con corrutinas ya disponibles y vuelve a comprobar al iniciar/primer plano. Usa `Clock`; no son horas legales ni se envían al servidor. Logout/expiración/cambio de modo invalida generación antes de permitir otra sesión; token, perfil y ámbitos privados anteriores se limpian.
5. Login, `/me`, temporizador y llamadas autenticadas capturan generación. Tras cualquier suspensión se comprueba vigencia antes de persistir/publicar/cerrar. Escrituras de sesión y cambios de generación se serializan con el mutex existente; un login que terminó tras logout no restaura el token. Tareas/callbacks viejos se cancelan o descartan, sin error de UI sobre la sesión nueva.

### Frontera HTTP y compatibilidad paciente

- Nuevo `StaffAuthApi.login(StaffLoginRequest(email,password))` para POST `/api/v1/auth/login`. `StaffAccountRepository` ejecuta login + `MeApi.meWithToken` y finalización del gestor; ViewModel valida/envía/bloquea usando `UiMessage` y no llama a Retrofit directamente.
- `SessionManager.authenticatedCall(scope, block)` captura un handle inmutable `{ generation, bearer, principal }`; exige scope PATIENT o STAFF autorizado y proporciona ese Bearer al bloque. Nunca consulta un token posterior para completar una operación anterior. Si la generación cambió, cancela/descarta el resultado; si un 401 pertenece a la generación vigente, cierra solo esa sesión. Un 401 viejo no cierra una nueva, incluso si ambos accesos son del mismo tipo.
- Mantener `patientCall` como adaptador al scope PATIENT para los repositorios de F-02, conservando sus APIs públicas y comportamiento. Las interfaces Retrofit protegidas pueden recibir Authorization explícita capturada como cabecera interna; no cambian rutas, cuerpos ni queries. Mantener el interceptor para la regla común y no sobrescribir esa cabecera. Ninguna petición pública/auth lleva Bearer.
- Conservar `feature/patients` y `feature/encounters` como paciente-only y sus pruebas `ApiScopeTest`. STAFF no monta Home/Profile/PreRegistration ni inicia esos repositorios; paciente no monta destinos STAFF. No reutilizar la carpeta encounters paciente para añadir APIs staff futuras.
- Requests y responses sensibles tienen `toString` enmascarado; errores de servidor se usan por código/campo, nunca se muestran sus textos. Password/correo/documento/formularios viven en VM sin SavedStateHandle/rememberSaveable; limpieza al destruir la sesión. No modificar reglas de consentimiento, mayoría de edad, estados, profile editable, llamada de preregistro ni refresco F-02 en este bloque.

## Navegación, guardas y presentación

La bienvenida ofrece Paciente/Personal. Paciente abre TenantPicker si falta IPS o Login si existe; mantiene SignUp y F-02. Personal abre correo/contraseña sin selector de IPS. Sin registro staff ni recuperación/SSO nuevos. Cada raíz autenticada usa una identidad de generación, no solo el tipo del principal: Patient A→Patient B también destruye su pila y VM. No se guardan tokens ni perfiles en NavKeys, SavedStateHandle o estado serializado de Navigation 3.

Matriz única en `core/auth` consumida por la raíz y pruebas; la API sigue autorizando independientemente:

| Principal/rol | Inicio | Otros destinos permitidos de personal |
|---|---|---|
| PATIENT | Inicio paciente existente | Ninguno; solo sus pantallas de F-01/F-02 |
| RECEPTION | Recepción | Atenciones |
| NURSE | Triage | Cola, Atenciones; lectura clínica autorizada cuando llegue su slice |
| PHYSICIAN | Medicina/Cola | Atenciones; lectura clínica autorizada cuando llegue su slice |
| ADMIN | Administración/Reportes | Cola, Atenciones |

Solo ADMIN tiene Reportes. Recepción/Triage/Cola/Reportes/Atenciones se presentan como pendientes del bloque correspondiente, sin consultas ni datos ficticios. Cabecera STAFF con fullName/rol en RAM y cierre con confirmación. Guards niegan destinos ajenos antes de construir la pantalla/VM o llamar al repositorio. La raíz es la única que conoce varias features.

Usar componentes y estilos Compose existentes, etiquetas y recursos `strings.xml`, ancho máximo/scroll, teclado de correo, password oculta y foco/error por campo. `email.trim()` y contraseña literal; no inventar longitud mínima de contraseña de **login**. 401 limpia password; 429 usa contador con tiempo virtual y fallback 60; 400 campos conocidos reciben mensajes locales; resto error general. Rotación conserva formularios VM dentro de su generación. Idiomas/tema/TTS/identidad visual llegan después y no se incorporan en este slice.

## Impacto en contrato

**Sin impacto en contrato.** Consume **0.4.0 → 0.4.0**. Nuevo consumo móvil de POST `/api/v1/auth/login` (`StaffLoginDto` → `AccessTokenDto`) y variante `StaffMeDto` de GET `/api/v1/me`; paciente conserva sus rutas existentes. DTOs Kotlin corresponden al snapshot, con pruebas HTTP MockWebServer y cuerpos exactos. No se añade endpoint logout, refresh, staff-signup, query de tenant ni migración API. No se modifica el snapshot ni el ADR de API.

## Impacto en datos

**Sin impacto en datos de servidor ni migraciones.** Se mantiene DataStore con token AES-256-GCM, expiración e IPS/sede públicas; ningún perfil/rol privado se persiste. No requiere nuevas claves ni cambio de formato cifrado. Tokens anteriores se restauran verificando `/me`; claves ilegibles se limpian. El principal y datos privados solo viven en RAM del proceso/VM por generación. No clínica, listas, contraseña/correo/documento en disco, backup, logs o navegación serializada.

## Riesgos y pruebas

- JUnit `R<n> ` y MockWebServer real en frontera HTTP: login JSON exacto/sin Bearer, `/me` explícito, cuatro roles y kind desconocido, modo equivocado, tenant paciente diferente, STAFF sin IPS, expiry inválida, login/restore 401/429/red/5xx y mensajes locales.
- Sesión: restauración bloquea UI hasta verificar; error conserva token pero no acceso; timer/foreground; logout mantiene IPS y limpia único token; rotación no cambia generación. Intercalar A/B, cambio de acceso durante login o `/me`, 401 viejo después de login nuevo y éxito viejo después de logout; no usar repositorios mockeados para saltarse la frontera HTTP.
- Guards/VM/navegación: todos los roles, enlaces exactos y marcadores; ruta forzada ajena sin petición; staff no activa consultas paciente; paciente sin staff; Atrás tras logout/cambio/A→B no muestra contenido anterior.
- Regresión completa F-01/F-02 (221 pruebas base), ApiScopeTest, PrivacyTest, PatientDataPrivacyTest y cifrado; actualizar solo expectativas de integración de estados cuando sea necesario, sin eliminar garantías. Pruebas de bytes en disco sin datos personales y toString enmascarado de los nuevos modelos.
- `./gradlew lint testDebugUnitTest assembleDebug`, init y reviewer C1–C8. Instrumentación Keystore y humo Compose en teléfono/tablet API31/API37 cuando disponible; capturas del bloque y matriz de permisos. Separar fixtures contractuales de API real, condicionada a configuración/cuentas; no inventar credenciales ni tocar API/DB.
