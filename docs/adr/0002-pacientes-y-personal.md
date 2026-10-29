# ADR 0002 (mobile) — Una APK para pacientes y personal

- **Estado:** Aceptada por autorización explícita del plan móvil completo en chat.
- **Fecha:** 2026-10-29 (America/Bogota).
- **Fuente:** `docs/mobile-migration.md`, mensaje del usuario **«PLEASE IMPLEMENT THIS PLAN»**.
- **Alcance de esta decisión:** cliente Android; el contrato OpenAPI 0.4.0 y las decisiones del servidor se consultan sin modificarlos.

## Contexto

El móvil se creó como app del paciente según ADR 0008 de la API. La base `ffea8e0` ya entrega F-01-mobile y F-02-mobile: cuenta/consentimiento, perfil propio, preregistro y último estado. El usuario ahora autoriza explícitamente una sola APK con acceso paciente y personal y paridad progresiva con la web vigente. Las instrucciones y el checkpoint que prohíben cualquier endpoint staff en la app deben ampliarse de forma consciente; esa regla ya no puede aplicarse globalmente a todo el APK.

Esta ADR **no declara modificado el ADR 0008 de la API**. Registra una ampliación local autorizada que conserva la separación de tokens/principales, las reglas de tenant y el flujo paciente del servidor. Los permisos del backend no cambian.

## Decisión

1. Mantener un APK Kotlin/Compose/MVVM, con entradas Paciente/Personal y **una sola sesión activa**. No existe registro público staff. La sesión se convierte a principal Patient/Staff y rol reconocido solo tras `/me`; la restauración no abre contenido protegido antes de verificar.
2. Paciente conserva sus credenciales/documento, IPS/sede elegidas, alta/consentimiento y funciones F-02. Personal usa correo/contraseña del endpoint publicado, sin tenant/sede impuestos por el paciente; su tenant y rol vienen de `/me`.
3. El token único se cifra mediante Keystore AES-256-GCM y se guarda con expiración en DataStore. Principal, perfil, listas y borradores privados viven en memoria; no hay persistencia adicional de PHI ni sesiones paralelas. Las claves existentes se conservan; los tokens anteriores se clasifican con `/me`.
4. Cada sesión/intento tiene generación local. Logout, cambio de acceso, expiración y 401 vigente invalidan la generación y limpian navegación/consultas/borradores. Peticiones/callbacks anteriores se descartan y nunca usan el token de una sesión nueva ni la cierran por un 401 viejo.
5. Los guards y llamadas se separan por **principal y rol**. PATIENT solo usa sus APIs paciente/públicas; sus features actuales permanecen así. STAFF solo accede a destinos de su rol, conforme al contrato. Los datos clínicos siguen limitados a NURSE/PHYSICIAN; no se amplían permisos del servidor.
6. Se conserva la arquitectura core/feature, Navigation 3, DI manual y separación entre features. El núcleo comparte sesión/guards/modelos, la raíz compone pantallas y los bloques staff se añaden en sus propias features. F-01-staff no implementa recepción, clínica, dashboards ni voz: usa marcadores pendientes hasta sus slices.
7. Los siete bloques, idiomas/identidad/preferencias y capacidades futuras se incorporan en el orden de `docs/mobile-migration.md`, con una feature activa, spec previa, implementación, revisión y validación por bloque. Se mantienen HTTP claro solo debug, no subida de audio y no horas legales del cliente.

## Alternativas descartadas

- **Dos aplicaciones o dos tokens concurrentes:** contradice la APK/sesión única autorizada y complica aislamiento/cambio de acceso.
- **Convertir todos los repositorios paciente a repositorios staff:** eliminaría garantías de ámbito propio y duplicaría el trabajo F-02 ya entregado.
- **Confiar en el rol elegido o en JWT decodificado localmente:** `/me` es la autoridad; un principal/rol desconocido no autoriza.
- **Guardar el perfil para restaurar sin red:** abriría contenido no verificado y persistiría datos que el plan exige mantener en memoria. Ante fallo de verificación se conserva el token cifrado y se bloquea contenido con reintento/logout.
- **Editar ADR/contrato de API o crear endpoints móviles propios:** el contrato 0.4.0 ya aporta las capacidades necesarias y es de solo lectura para esta ejecución.

## Consecuencias y compatibilidad

- Actualizar AGENTS, arquitectura, README/checkpoint C7 y pruebas de integración de navegación para expresar alcance por principal. La prohibición anterior de endpoints staff se mantiene en **el contexto PATIENT**, no como prohibición global del APK. Las restricciones y pruebas `ApiScopeTest` de `feature/patients`/`feature/encounters` siguen vigentes.
- F-01-mobile/F-02-mobile permanecen terminados. Este slice adapta su sesión/navegación, sin renumerar requisitos históricos ni reimplementar perfil, preregistro, consentimiento o estados. La verificación previa a contenido sustituye el inicio no verificado de la restauración antigua.
- El usuario aprobó estas decisiones en su plan completo; materializarlas en spec/ADR no crea una nueva puerta de producto. Cualquier ampliación distinta se lleva al chat, sin atribuirla al permiso de este documento.
- Las verificaciones distinguen servidor sintético conforme al contrato, Keystore/dispositivo real y API/DB real. La falta de configuración real se documenta; no autoriza inventar credenciales ni modificar API/base compartida.
