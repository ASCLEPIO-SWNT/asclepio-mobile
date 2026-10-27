package co.asclepio.mobile.core.auth

import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

private val BOGOTA: ZoneId = ZoneId.of("America/Bogota")

/**
 * Mayor de edad (D5): `Period.between(birthDate, hoy).years >= 18`, con `hoy` = fecha civil en America/Bogota.
 * Misma regla que la API: quien nació un 29 de febrero cumple el 1 de marzo en años no bisiestos.
 * Una fecha futura da un periodo negativo y por tanto `false`.
 */
fun isAdult(birthDate: LocalDate, clock: Clock): Boolean =
    Period.between(birthDate, LocalDate.now(clock.withZone(BOGOTA))).years >= ADULT_AGE

const val ADULT_AGE = 18
