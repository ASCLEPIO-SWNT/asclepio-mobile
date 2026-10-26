# AGENTS.md — mapa de asclepio-mobile

App Android (Kotlin + Jetpack Compose) de Asclepio para **admisión**: login, buscar o crear paciente, crear la atención y dictar el motivo en el dispositivo. *La IA propone, el profesional decide.* Lee solo lo que necesites en cada momento.

## 1. Antes de empezar
1. Ejecuta `./init.sh`. **Si falla, para** y reporta.
2. Lee `feature_list.json` y los archivos de `progress/current/`.
3. Antes de escribir o revisar cualquier spec, lee `docs/specs.md`.

## 2. Archivos y carpetas

| Ruta | Qué contiene | Cuándo leerlo |
|---|---|---|
| `feature_list.json` | Slices de este repo, estados, dependencias y `consumes_contract` | Siempre, al empezar |
| `progress/current/<id>.md` | Estado vivo de la feature en curso | Siempre, al empezar |
| `progress/history.md` | Registro append-only de sesiones | Para contexto histórico |
| `specs/<id>/` | requirements, design y tasks del slice | Al especificar, implementar o revisar |
| `docs/specs.md` | Proceso SDD, EARS y la puerta de aprobación | Antes de cualquier spec |
| `docs/architecture.md` | Paquetes, MVVM y dependencias permitidas | Antes de diseñar o implementar |
| `docs/conventions.md` | Nombres, estados de UI, fechas, commits | Antes de implementar |
| `docs/verification.md` | Cómo probar y la trazabilidad | Antes de implementar o revisar |
| `docs/adr/` | ADR propios del repo (minSdk, llega con F-00) | Antes de cambiar la plataforma |
| `CHECKPOINTS.md` | C1–C8 | Al revisar o cerrar |
| `../asclepio-api/docs/product/` | Visión, glosario, RNF y **briefs F-0X** (solo lectura) | Al especificar |
| `../asclepio-api/docs/adr/0006-voz-en-dispositivo.md` | Decisión de voz en el dispositivo | En F-08 |
| `../asclepio-api/openapi/asclepio-api.v1.json` | **Contrato** (solo lectura) | Al consumir endpoints |
| `.claude/agents/` | Roles | Según tu rol |

## 3. Reglas duras
- **Una feature por sesión.**
- **No se marca `done` sin pruebas en verde** (`./init.sh` completo: `./gradlew lint testDebugUnitTest`).
- **No se salta la spec** cuando `sdd: true`.
- **No se salta la aprobación humana**, que se da **por chat**.
- Documenta en `progress/current/<id>.md` mientras trabajas.
- **No inventes requisitos clínicos.**
- **El contrato va primero:** un slice no pasa a `in_progress` si `F-0X-api` no está `done` o si su versión de contrato no existe. Declárala en `consumes_contract`.
- **No escribas en `../asclepio-api/`** (hay un hook que lo bloquea).
- **El audio nunca sale del dispositivo** (ADR 0006): sin subidas de audio ni ASR en la nube.
- El tráfico HTTP en claro solo se permite en `debug`.

## 4. Flujo
```
pending → [spec_author] → spec_ready → ⏸ aprobación por chat → in_progress → [implementer → reviewer] → done
```
F-00 (`sdd: false`) salta la spec, pero no el reviewer.

## 5. Cierre de sesión
1. `./init.sh` en verde.
2. Actualiza el estado en `feature_list.json`.
3. Mueve el resumen de `progress/current/<id>.md` al final de `progress/history.md` y borra el archivo de la feature (y `impl_`/`review_`).
4. Sin archivos temporales, `Log.d` de depuración ni TODOs sin contexto.

## 6. Si te bloqueas
Documenta el bloqueo en `progress/current/<id>.md` y **para**. No inventes workarounds.
