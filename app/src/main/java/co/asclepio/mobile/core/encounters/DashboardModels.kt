package co.asclepio.mobile.core.encounters

object DashboardLimits {
    const val COUNT = 100
    const val WAITING_PAGE = 50
    const val RECENT = 8
}

/** Cantidad conocida y si solo es una cota inferior; el contrato no publica un total. */
data class EncounterCount(val quantity: Int, val limited: Boolean)

data class CountKey(val status: EncounterStatus, val siteId: String?, val generation: Long)

/** Proyección mínima de recientes. No retiene documento, motivo ni el DTO de paciente. */
data class RecentEncounter(
    val id: String,
    val fullName: String?,
    val siteName: String,
    val arrivalAt: String,
    val status: String,
) {
    override fun toString() = "RecentEncounter(***)"
}

/** Solo sede pública y metadatos agregados, sin pacientes. */
data class SiteEncounterCount(val siteId: String, val siteName: String, val count: EncounterCount)

/** Un offset sin elementos nunca se presenta como un total exacto. La VM debe volver a cero y leer. */
data class PageCount(val count: EncounterCount?, val resetOffset: Boolean)

fun pendingCount(size: Int): EncounterCount {
    require(size >= 0)
    return EncounterCount(size, limited = false)
}

fun deriveWaitingPage(offset: Int, itemCount: Int): PageCount {
    require(offset >= 0 && itemCount >= 0)
    if (offset > 0 && itemCount == 0) return PageCount(count = null, resetOffset = true)
    return PageCount(
        count = EncounterCount(offset + itemCount, limited = itemCount >= DashboardLimits.WAITING_PAGE),
        resetOffset = false,
    )
}
