# F-11-mobile — Entrevista de síntomas con IA en el teléfono

Brief global: ../asclepio-api/docs/product/features/F-11.md (AC1–AC5, AC8)
Contrato: consume 0.11.0 (`interview` en `POST /patient/pre-registrations`, `POST /patient/symptom-interview/next-question`)
RNF aplicables: RNF-04, RNF-05, RNF-06, RNF-13, RNF-17
Depende de: F-11-api (`done`), F-08-mobile (dictado), F-02-mobile (prerregistro).
ADR: 0010. La investigación está en `../asclepio-api/docs/research/llm-on-device-android.md`.

Glosario de la spec:
- **Motor local**: LiteRT-LM (`litertlm-android`, versión fijada) con Gemma 4 E2B-it `.litertlm`. La URL, el tamaño y el SHA-256 van en `BuildConfig`.
- **Equipo capaz**: cumple todo lo siguiente:
  - `ActivityManager.MemoryInfo.totalMem ≥ 5,5 GiB`;
  - el modelo está verificado en `filesDir/models/`;
  - el `Engine` se inicializó en ≤20 s en esta sesión de la app.
- **Motor del servidor**: el endpoint `next-question` (F-11-api R11).
- **Dimensiones, señales, validador y preguntas fijas**: las mismas definiciones que F-11-api. El fixture `app/src/test/resources/red-flag-cases.json` es idéntico al de la API.
  - Fixture: copia literal de `../asclepio-api/test/fixtures/red-flag-cases.json`, SHA-256 `2d75bdbd0ba08e8e1c9d29ccc40c94ab8c9ca003c34ee8c642409c4da83fe6b7`. Los patrones de `RedFlagRules` son los de `RED_FLAG_PATTERNS` de `interview.rules.ts`, portados literalmente.
- **Entrevista**: hasta 6 turnos. Termina por:
  - `COMPLETE`: el modelo devuelve `done` con ≥3 dimensiones respondidas;
  - `MAX_QUESTIONS`: se llega a 6;
  - `PATIENT`: el paciente pulsa "Terminar";
  - `RED_FLAG`.
  - Enmienda (humano, 2026-11-18, igual que R14 de F-11-api): `COMPLETE` solo se acepta si además está cubierta `ONSET` y, cuando el motivo o alguna respuesta mencionen dolor (`dolor`, `duele`, `doler`, normalizado), también `LOCATION`. Si falta alguna, se hace la pregunta fija de la primera dimensión obligatoria no cubierta.

## Requisitos

### Descarga del modelo
- **R1** (Por evento) Cuando el paciente acepte descargar el asistente (desde Perfil o desde el prerregistro), la app deberá encolar en WorkManager una descarga que solo corra con red no medida (`UNMETERED`), como trabajo en primer plano con notificación de progreso. (→ F-11.AC4)
- **R2** (No deseado) Si el espacio libre es menor que 2 × el tamaño del modelo, entonces la app deberá no iniciar la descarga y mostrar cuánto espacio falta. (→ F-11.AC4)
- **R3** (Por evento) Cuando la descarga se interrumpa, la app deberá reanudarla desde los bytes ya escritos en `.part` con una cabecera `Range`. (→ F-11.AC4)
- **R4** (Por evento) Cuando termine la descarga, la app deberá verificar el SHA-256. Si coincide, deberá renombrar el archivo atómicamente al destino final. Si no, deberá borrar el `.part` y marcar el error. (→ F-11.AC4)
- **R5** (Ubicuo) La app deberá mostrar el progreso (porcentaje y MB) y permitir cancelar la descarga y borrar el modelo. El APK no deberá incluir el modelo. (→ F-11.AC4)

### Selección de motor
- **R6** (Por evento) Cuando el paciente termine de escribir o dictar el motivo y pulse "Continuar", la app deberá usar el motor local si el equipo es capaz. Si no, deberá mostrar una vez el aviso del motor del servidor (PA-F11-3) con las opciones "Continuar" y "Omitir asistente". (→ F-11.AC4, F-11.AC5)
- **R7** (No deseado) Si el motor del servidor responde con error, 429, 503 o timeout de red, entonces la app deberá terminar la entrevista con los turnos ya respondidos y permitir enviar el prerregistro. (→ F-11.AC5)

### Entrevista
- **R8** (Ubicuo) La app deberá mostrar las preguntas de una en una, con un campo de respuesta (texto o dictado de F-08) y los botones "No sé", "Terminar" y "Siguiente". (→ F-11.AC1)
- **R9** (Ubicuo) El motor local deberá generar cada salida con `ResponseFormat.json` según el esquema, y la app deberá pasarla por el validador. Si no pasa, deberá reintentar una vez y, si vuelve a fallar o tarda más de 20 s, usar la pregunta fija de la primera dimensión no cubierta. (→ F-11.AC2)
  - Enmienda (humano, 2026-11-18, igual que R18 de F-11-api): el prompt local es `interview-v2`, igual al de `../asclepio-api/ai/prompts/interview-v2.md`.
- **R10** (Ubicuo) La app deberá garantizar que ningún texto mostrado al paciente durante la entrevista viene del modelo sin haber pasado el validador. (→ F-11.AC2)
- **R11** (Por evento) Cuando el paciente responda una pregunta, la app deberá aplicar las reglas de alarma al motivo y a todas las respuestas **antes** de pedir la siguiente pregunta. (→ F-11.AC3)
- **R12** (Por evento) Cuando las reglas detecten una señal, o el motor devuelva `redFlagSuspected = true` o `RED_FLAG`, la app deberá cortar la entrevista, mostrar la pantalla "Acude ya a urgencias o llama al 123" y enviar el prerregistro de inmediato con `endedBy = RED_FLAG` y las señales detectadas. (→ F-11.AC3)
  - Enmienda (humano, 2026-11-18, igual que R13 de F-11-api): `redFlagSuspected = true` en una salida con el esquema válido escala **aunque la pregunta no pase el validador**, y la pregunta rechazada nunca se muestra. Con 0 turnos respondidos, `redFlagSuspected` del modelo se ignora. Las reglas deterministas sobre el motivo se aplican antes y escalan igual.
- **R13** (No deseado) Si el envío inmediato de R12 falla, entonces la app deberá mantener la pantalla de alarma visible y reintentar el envío con backoff mientras el paciente esté en ella. (→ F-11.AC3)
- **R14** (Por evento) Cuando la entrevista termine por `COMPLETE`, `MAX_QUESTIONS` o `PATIENT`, la app deberá mostrar un resumen de las respuestas editable y enviar el prerregistro con `interview` al confirmar. (→ F-11.AC1, F-11.AC6)

### Privacidad, rendimiento y ciclo de vida
- **R15** (Por estado) Mientras se use el motor local, la app no deberá hacer ninguna petición de red con el motivo ni con las respuestas hasta enviar el prerregistro. (→ F-11.AC4)
- **R16** (Ubicuo) La app deberá excluir de los logs el motivo, las preguntas y las respuestas. (→ F-11.AC4) (RNF-05)
- **R17** (Por evento) Cuando la pantalla de la entrevista pase a `ON_STOP`, la app deberá cancelar la generación en curso y conservar los turnos en `SavedStateHandle`. Al volver, deberá regenerar la pregunta actual. Al salir de la entrevista, deberá liberar el `Engine`. (→ F-11.AC4)
  - Enmienda (humano, 2026-11-19): los turnos se conservan **solo en la memoria del `InterviewViewModel`**, no en `SavedStateHandle`, por C7 y `PrivacyTest`: no se serializan datos clínicos en el estado de navegación. Sobreviven a la rotación y a `ON_STOP`, pero no a la muerte del proceso; en ese caso el paciente repite la entrevista.
- **R18** (Ubicuo) La app deberá registrar, solo en las compilaciones de depuración, el TTFT y la duración de cada generación local, para la evaluación de D11. (→ F-11.AC8)

### Evaluación
- **R19** (Ubicuo) El repositorio deberá incluir `docs/eval/interview-cases.json` con 14 casos (9 ambiguos y 5 de alarma, 2 de ellos sutiles) y una prueba instrumentada manual, fuera de `init.sh`, que reporte las métricas y los umbrales de D11. (→ F-11.AC8)

## Preguntas abiertas
Resueltas el 2026-11-18 por el humano («aprobado» en el chat): se aceptan todas las propuestas.
- [x] PA-F11-1, PA-F11-3 y PA-F11-4 (en el brief). — bloquean R6, R11, R12
- [x] PA-F11-6 Umbrales de aceptación del modelo (D11). Propuesta:
  - 100 % de las alarmas escaladas;
  - 0 salidas prohibidas mostradas;
  - ≥90 % de JSON válido al primer intento;
  - ≥80 % de preguntas relevantes según rúbrica humana;
  - media ≤6 preguntas;
  - p90 ≤8 s por pregunta en el equipo de gama media de prueba.

  Si no se cumplen, se pasa a la alternativa (llama.cpp + Qwen3.5-2B) o se usa solo el servidor. — bloquea R19
