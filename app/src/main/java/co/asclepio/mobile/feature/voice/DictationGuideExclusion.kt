package co.asclepio.mobile.feature.voice

/**
 * D8 (R13): "Dictar" detiene primero la guía TTS y después inicia la escucha. La raíz construye este callback en
 * cada composición con la guía vigente, porque la guía se recrea al rotar o cambiar de idioma y el ViewModel no.
 */
fun startDictationStoppingGuide(stopGuide: () -> Unit, startDictation: () -> Unit) {
    stopGuide()
    startDictation()
}
