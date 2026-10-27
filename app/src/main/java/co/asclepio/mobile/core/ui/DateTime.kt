package co.asclepio.mobile.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val bogota: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Bogota"))

/** Muestra una hora ISO-8601 (UTC, del servidor) en America/Bogota. */
fun formatBogota(iso: String): String = bogota.format(Instant.parse(iso))
