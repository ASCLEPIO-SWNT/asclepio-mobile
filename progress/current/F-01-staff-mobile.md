# F-01-staff-mobile — Acceso del personal

Fecha:2026-10-29 America/Bogota. Estado: preparación de spec. El usuario aprobó el plan móvil revisado íntegro mediante PLEASE IMPLEMENT THIS PLAN; docs/mobile-migration.md materializa su alcance antes del código.

Base móvil:ffea8e0, incorporada por fast-forward desde origin/main. API2f2672b, contrato0.4.0. Referencia web main dc280a9 (UI02). F02 del equipo ya está done y se conserva.

Entorno local configurado fuera del repositorio: ../../local-analysis/mobile-env.sh (JDK21, SDK37, Gradle cache local), local.properties no versionado. ./init.sh inicial completo VERDE, FAIL0/WARN0; log ../../local-analysis/mobile-verification/baseline-init.log.

Plan de esta sesión lógica: spec_author escribe requisitos/diseño/tareas y ADR local del nuevo alcance; registrar spec_ready y aprobación ya recibida; implementer amplía sesión/login/navegación; reviewer independiente C1–C8 e init; publicar sólo el bloque verificado en main, cerrar y continuar UI01.

Spec completada y revisada: 21 requisitos, sin preguntas abiertas. Estado spec_ready. Aprobación humana ya recibida del usuario mediante PLEASE IMPLEMENT THIS PLAN (2026-10-29), que autoriza estas mismas decisiones; se aplica sin pedir una confirmación duplicada.

Estado: in_progress. Implementación delegada a foundation; prueba primero, compatibilidad F02 y revisión independiente obligatorias.

Verificación de dispositivo inicial: Android31 AOSP_ATD arrancado con emulador37.2.12 y renderizador Lavapipe; cinco pruebas Keystore instrumentadas PASSED usando APK baseline. Log local ../../local-analysis/mobile-verification/baseline-instrumented-api31.log. SwiftShader producía SIGSEGV en RenderThread; no era fallo del APK. Android37 en preparación. Integración API/DB real aún no ejecutada.
