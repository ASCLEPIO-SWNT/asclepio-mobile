package co.asclepio.mobile.core.encounters

import kotlinx.coroutines.*

sealed interface DashboardReadKey {
    data class Count(val status: EncounterStatus, val siteId: String? = null) : DashboardReadKey
    data object Recent : DashboardReadKey
    data object Sites : DashboardReadKey
    data class Site(val id: String) : DashboardReadKey
    data class PendingTray(val siteId: String?) : DashboardReadKey
    data class NursingTray(val siteId: String?, val offset: Int) : DashboardReadKey
}

/** One visible clock; remote state and drafts stay with their feature owners. No cache or DTO storage. */
class DashboardRefreshCoordinator(
    private val scope: CoroutineScope,
    private val isCurrent: () -> Boolean,
) {
    private data class Reader(val eligible: () -> Boolean, val load: suspend (Long) -> Boolean)
    private val readers = linkedMapOf<DashboardReadKey, Reader>()
    private val revisions = mutableMapOf<DashboardReadKey, Long>()
    private val running = mutableMapOf<DashboardReadKey, Job>()
    private val pending = mutableSetOf<DashboardReadKey>()
    private val forbidden = mutableSetOf<DashboardReadKey>()
    private var active = false
    private var clock: Job? = null

    /** The reader returns true only for403. A rate-limited owner can temporarily report ineligible. */
    fun register(key: DashboardReadKey, eligible: () -> Boolean = { true }, load: suspend (Long) -> Boolean) {
        if (key in readers) return
        readers[key] = Reader(eligible, load)
        revisions[key] = (revisions[key] ?: 0) + 1
        if (active) request(key)
    }
    fun unregister(key: DashboardReadKey) {
        revisions[key] = (revisions[key] ?: 0) + 1
        readers.remove(key); pending.remove(key); forbidden.remove(key)
        running.remove(key)?.cancel()
    }
    fun isLatest(key: DashboardReadKey, revision: Long): Boolean = active && isCurrent() && key in readers && revisions[key] == revision
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        clock?.cancel(); clock = null
        if (!value) {
            readers.keys.forEach { revisions[it] = (revisions[it] ?: 0) + 1 }
            running.values.toList().forEach(Job::cancel)
            running.clear(); pending.clear()
            return
        }
        if (!isCurrent()) { active = false; return }
        refreshAutomatic()
        clock = scope.launch {
            while (active && isCurrent()) {
                delay(30_000)
                if (active && isCurrent()) refreshAutomatic()
            }
        }
    }
    fun refresh(keys: Set<DashboardReadKey> = readers.keys.toSet()) {
        if (!active || !isCurrent()) return
        keys.forEach { key -> forbidden.remove(key); request(key) }
    }
    fun invalidate(keys: Set<DashboardReadKey>) {
        keys.filter { it in readers }.forEach { key ->
            revisions[key] = (revisions[key] ?: 0) + 1
            if (running[key]?.isActive == true) pending += key else request(key)
        }
    }
    private fun refreshAutomatic() { readers.keys.toList().forEach(::request) }
    private fun request(key: DashboardReadKey) {
        val reader = readers[key] ?: return
        if (!active || !isCurrent() || key in forbidden || !reader.eligible() || running[key]?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            do {
                pending.remove(key)
                val revision = revisions.getValue(key)
                val blocked = reader.load(revision)
                if (isLatest(key, revision) && blocked) forbidden += key
            } while (pending.remove(key) && active && isCurrent() && key in readers && key !in forbidden && reader.eligible())
        }
        running[key] = job
        job.invokeOnCompletion { if (running[key] === job) running.remove(key) }
        job.start()
    }
}
