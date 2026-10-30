package co.asclepio.mobile.core.ui

/** Independent remote state; sensitive projections are never rendered by toString. */
sealed interface DashboardRead<out T> {
    data object Loading : DashboardRead<Nothing>
    data class Ready<T>(val data: T) : DashboardRead<T> {
        override fun toString() = "DashboardRead.Ready(private=***)"
    }
    data class Error(val message: UiMessage, val retryEnabled: Boolean = true) : DashboardRead<Nothing>
    data object Forbidden : DashboardRead<Nothing>
}
