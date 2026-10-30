package co.asclepio.mobile.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dashboardDateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    .withLocale(Locale.forLanguageTag("es-CO")).withZone(ZoneId.of("America/Bogota"))
private val meridiemSpaces = Regex("""[\s\u00a0\u202f]+([ap])\.[\s\u00a0\u202f]+m\.""")

/** Same source time as the server; dashboards alone omit seconds, as in UI-02-web. */
fun formatDashboardBogota(iso: String): String = meridiemSpaces.replace(dashboardDateTime.format(Instant.parse(iso))) { match ->
    "\u00a0${match.groupValues[1]}.\u00a0m."
}
