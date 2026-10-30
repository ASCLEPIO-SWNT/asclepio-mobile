package co.asclepio.mobile.core.encounters

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardRefreshCoordinatorTest {
    @Test fun `R10 one visible clock reads on entry and each30seconds only`() = runTest {
        var calls = 0
        val coordinator = DashboardRefreshCoordinator(backgroundScope) { true }
        coordinator.register(DashboardReadKey.Recent) { calls++; false }
        coordinator.setActive(true); runCurrent()
        assertEquals(1, calls)
        coordinator.setActive(true); advanceTimeBy(29_999); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, calls)
        coordinator.setActive(false); advanceTimeBy(60_000); runCurrent()
        assertEquals(2, calls)
        coordinator.setActive(true); runCurrent()
        assertEquals(3, calls)
        coordinator.setActive(false)
    }
    @Test fun `R12 busy manual ticks coalesce and repeated invalidation queues exactly one new revision`() = runTest {
        val hold = CompletableDeferred<Unit>()
        val revisions = mutableListOf<Long>()
        val coordinator = DashboardRefreshCoordinator(backgroundScope) { true }
        coordinator.register(DashboardReadKey.Recent) { revision ->
            revisions += revision
            if (revisions.size == 1) hold.await()
            false
        }
        coordinator.setActive(true); runCurrent()
        coordinator.refresh(); coordinator.refresh(); advanceTimeBy(30_000); runCurrent()
        assertEquals(1, revisions.size)
        val first = revisions.single()
        coordinator.invalidate(setOf(DashboardReadKey.Recent)); coordinator.invalidate(setOf(DashboardReadKey.Recent))
        assertFalse(coordinator.isLatest(DashboardReadKey.Recent, first))
        hold.complete(Unit); runCurrent()
        assertEquals(2, revisions.size)
        assertTrue(revisions.last() > first)
        coordinator.setActive(false)
    }
    @Test fun `R16 forbidden key stops alone while failures and other reads continue and manual retries permission`() = runTest {
        val key = DashboardReadKey.Count(EncounterStatus.WAITING_TRIAGE)
        var denied = 0
        var recent = 0
        val coordinator = DashboardRefreshCoordinator(backgroundScope) { true }
        coordinator.register(key) { denied++; true }
        coordinator.register(DashboardReadKey.Recent) { recent++; false }
        coordinator.setActive(true); runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(1, denied); assertEquals(2, recent)
        coordinator.refresh(setOf(key)); runCurrent()
        assertEquals(2, denied); assertEquals(2, recent)
        coordinator.setActive(false)
    }
    @Test fun `R11 exit and changed session invalidate delayed callbacks without background requests`() = runTest {
        val hold = CompletableDeferred<Unit>()
        var generationCurrent = true
        var delivered = false
        var calls = 0
        val coordinator = DashboardRefreshCoordinator(backgroundScope) { generationCurrent }
        coordinator.register(DashboardReadKey.Recent) { revision ->
            calls++; hold.await()
            if (coordinator.isLatest(DashboardReadKey.Recent, revision)) delivered = true
            false
        }
        coordinator.setActive(true); runCurrent()
        generationCurrent = false
        coordinator.setActive(false); hold.complete(Unit); runCurrent()
        advanceTimeBy(90_000); runCurrent()
        assertFalse(delivered); assertEquals(1, calls)
    }
    @Test fun `R13 invalidation refreshes only affected keys and manual retry preserves other readings`() = runTest {
        val counts = mutableMapOf<DashboardReadKey, Int>()
        val coordinator = DashboardRefreshCoordinator(backgroundScope) { true }
        val waiting = DashboardReadKey.Count(EncounterStatus.WAITING_TRIAGE)
        val triaged = DashboardReadKey.Count(EncounterStatus.TRIAGED)
        val recent = DashboardReadKey.Recent
        for (key in listOf(waiting, triaged, recent)) coordinator.register(key) { counts[key] = (counts[key] ?: 0) + 1; false }
        coordinator.setActive(true); runCurrent()
        assertEquals(setOf(1), counts.values.toSet())
        coordinator.invalidate(setOf(waiting)); runCurrent()
        assertEquals(2, counts[waiting]); assertEquals(1, counts[triaged]); assertEquals(1, counts[recent])
        coordinator.refresh(setOf(recent)); runCurrent()
        assertEquals(2, counts[waiting]); assertEquals(1, counts[triaged]); assertEquals(2, counts[recent])
        coordinator.setActive(false)
    }
}
