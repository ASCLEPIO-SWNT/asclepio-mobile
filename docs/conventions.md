# Convenciones — asclepio-mobile

## Nombres
- Clases en `PascalCase`; funciones y propiedades en `camelCase`; constantes en `UPPER_SNAKE_CASE`.
- `XxxScreen` (Composable de pantalla), `XxxViewModel`, `XxxRepository`, `XxxApi` (Retrofit) y `XxxDto` (`@Serializable`).
- El paquete base es `co.asclepio.mobile`.
- Identificadores en inglés; textos de UI en español en `strings.xml` (no hay strings literales en los Composables).

## Formato
- ktlint con el estilo oficial de Kotlin. El hook PostToolUse ejecuta `ktlint -F` sobre el archivo tocado si está instalado.

## Estados y errores
- Cada ViewModel expone `StateFlow<UiState>`, con `UiState` como sealed: `Loading`, `Success(data)` y `Error(message)` (más `Idle` si aplica).
- Los errores HTTP `application/problem+json` se convierten en `core/network` a un `ProblemDetails` (`title`, `detail`, `errors[]`) y luego a un mensaje de UI.

## Fechas
- La API entrega ISO-8601 en UTC y la UI muestra la hora en `America/Bogota` (`java.time`, `ZoneId.of("America/Bogota")`).
- La app no envía horas legales.

## Validación
- La validación en la UI es para la experiencia de uso; la API valida igual.

## Pruebas
- JUnit (`src/test`) con nombres en backticks que empiezan por `R<n> ` (R, número y espacio) cuando cubren un requisito. **No se usa `:`** porque no es válido en nombres de método JVM, **ni caracteres parecidos** (como `꞉` U+A789), porque rompen el grep de trazabilidad.

## Commits
- Conventional Commits (`feat(voice): …`, `fix(patients): …`, `chore: …`), con `Refs: F-0X-mobile` en el cuerpo.

## Ramas y PRs
Decisión del humano (2026-11-10), igual en los tres repos:
- `develop` es la rama de integración. Cada feature va en `feature/<id>` (p. ej. `feature/F-08-mobile`), creada desde `develop` actualizado, y entra por PR a `develop`.
- Correcciones y cierres de harness: `fix/<id>` o `chore/<id>`, también desde `develop` y por PR.
- `main` solo se actualiza por PR `develop → main`.
- Nunca push directo a `develop` ni a `main`; nunca force-push ni reescritura de historial.
- Los PRs los fusiona el humano.
