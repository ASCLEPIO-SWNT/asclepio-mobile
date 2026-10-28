package co.asclepio.mobile.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Datos del paciente compartidos por el alta (F-01) y "Mis datos" (F-02) (D2 de F-02-mobile).

/** Sexo del contrato (`M`, `F`, `X`). */
@Serializable
enum class Sex { M, F, X }

/** Longitud máxima de `fullName` en el contrato. */
const val FULL_NAME_MAX = 200

/** El DatePicker de Material 3 entrega la medianoche UTC del día elegido: es una fecha civil, sin zona (D6 de F-01). */
fun datePickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.toDatePickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
