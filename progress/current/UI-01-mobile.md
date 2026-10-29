# UI-01-mobile — Identidad, idiomas y accesibilidad

2026-10-29 America/Bogota. Base main548d17c, publicada y verificada tras F-01-staff-mobile APROBADO. Spec revisada:21R, sin preguntas abiertas. Estado spec_ready; aprobación humana ya recibida mediante PLEASE IMPLEMENT THIS PLAN para estas mismas decisiones. No se solicita confirmación duplicada.

Baseline de este bloque: ./init.sh completo VERDE, FAIL0/WARN0;262JVM y lint. Log local ../../local-analysis/mobile-verification/ui01-baseline-init.log. Android37 emulado con Google APIsrev8/Lavapipe, datos de QA ficticios; API real pendiente.

Alcance: identidad/fuentes nativas/5idiomas/preferencias/TTS estático, adaptación de pantallas existentes y solicitudes propias sin polling. No adelantar recepción, clínica, dashboards operativos ni dictado. Recursos preparados fuera del repo en mobile-ui-assets, mobile-ui-translations y fixture mobile-preview-api.

Plan: implementer prueba primero y reutiliza negocio F01/F02; commits convencionales por hitos, reviewer independiente y QA Android antes del cierre/publicación.

Estado: in_progress tras registrar la aprobación existente. Una sola feature activa.
