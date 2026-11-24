package co.asclepio.mobile.core.interview

import co.asclepio.mobile.core.ui.UiMessage

/**
 * Envío inmediato del prerregistro ante una señal de alarma (F-11-mobile R12, R13). Lo produce el prerregistro y lo
 * muestra la pantalla de alarma de la entrevista; por eso vive en `core`.
 */
sealed interface UrgentSubmission {
    data object Sending : UrgentSubmission

    /** Falló; se reintenta en [seconds] mientras la pantalla de alarma siga visible (R13). */
    data class Retrying(val seconds: Int) : UrgentSubmission

    data object Sent : UrgentSubmission

    /** Error que no se arregla reintentando (p. ej. ya hay un prerregistro en curso). */
    data class Failed(val message: UiMessage) : UrgentSubmission
}
