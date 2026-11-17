# feature/voice — dictado del motivo en el dispositivo (F-08-mobile)

El paciente dicta el motivo de su pre-registro en español con el reconocedor **local** de Android (ADR 0006: el audio nunca sale del teléfono). El texto se añade al motivo, se puede editar y se envía con `complaintSource = VOICE_ON_DEVICE`.

- `AndroidOnDeviceRecognizer.kt`: único adaptador de `SpeechRecognizer` (implementa `core/voice/OnDeviceRecognizer`). Solo `createOnDeviceSpeechRecognizer`, siempre tras `isOnDeviceRecognitionAvailable`; listener antes de cualquier comando; un reconocedor por escucha y `destroy()` al terminar, cancelar o liberar. Sin resultados parciales, sin grabación propia, sin actividades de reconocimiento y sin `triggerModelDownload`.
- `SpanishOnDeviceSupport.kt`: funciones puras probadas en la JVM (`recognitionStrategy`, `pickSpanishTag`, `mapRecognizerError`, `dictationAction`).
- `DictationGuideExclusion.kt`: `startDictationStoppingGuide(stopGuide, startDictation)` detiene la guía TTS y después inicia la escucha (D8, R13).
- `ui/DictationControls.kt`: "Dictar", "Escuchando…/Procesando…" con "Detener" y "Cancelar", la nota "solo español", el aviso de no disponible y la solicitud de `RECORD_AUDIO` al pulsar.

El estado del dictado y el origen del motivo viven en `feature/encounters/ui/PreRegistrationViewModel` (solo en RAM: sin `SavedStateHandle`, DataStore ni logs). `AppNavigation` compone los controles en el slot `dictation` de la pantalla y cancela la escucha en segundo plano, al salir de la pantalla y cuando la guía empieza a hablar. El callback de "Dictar" se construye en cada composición con la guía vigente (la guía se recrea al rotar o cambiar de idioma; el ViewModel no la conoce).
