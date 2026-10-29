# Recursos de identidad Android

Referencia: UI-01/UI-02-web (main dc280a9, árbol c812c63) y assets del frontend anterior. Sólo se copian los recursos utilizados; no se conecta con esos repositorios en ejecución.

- Axiforma Regular/Semibold/Bold: TTF existentes en `legacy/asclepio-frontend/public/fonts`, pesos 400/600/700. Se conserva su procedencia; no se encontró un aviso de licencia adjunto y no se les atribuye una licencia abierta.
- OpenDyslexic Regular: OTF nativo derivado localmente del WOFF2 ya incluido en la web mediante FreeType 2.14.3 (`FT_Load_Sfnt_Table`, extracción SFNT CFF). Mismos glifos, nombres, métricas y muestras de rasterización; sin red. Licencia SIL OFL original en `app/src/main/assets/licenses/OpenDyslexic-OFL.txt`.
- Símbolo: PNG aprobado, recortado únicamente por margen transparente. No cambia proporciones ni color.
- Fondos login/registro: render completo de los SVG web a WebP nativo 960×540, manteniendo máscara/transparencia, mediante sharp 0.35.5/librsvg 2.63.2/vips 8.18.7. No se empaquetan SVG ni motor de render web.
- Paletas: diez variantes sRGB resueltas de los mismos tokens CSS web (claro/oscuro × normal/contraste/sepia/grises/alternativo); `UiPalette.kt` conserva los valores. La marca no usa color dinámico del dispositivo.

El manifiesto `app/src/main/assets/ui-resource-sha256.txt` contiene nueve hashes SHA-256 comprobados por prueba JVM. Las herramientas, fuentes y manifiestos de preparación reproducible se conservan fuera del repositorio en `local-analysis/mobile-ui-assets`; no son dependencias de la app.

El icono adaptativo reutiliza el mismo PNG en un lienzo transparente 432×432, reducido con Lanczos a un máximo de 172×256 y centrado, dentro de la zona segura de 108 dp. No modifica colores ni proporciones. Fondo `#F8FCFC`; capa monocroma para el tintado nativo del launcher. Heroicons (engranaje/flecha atrás) conserva su MIT en `assets/licenses/Heroicons-MIT.txt`.
