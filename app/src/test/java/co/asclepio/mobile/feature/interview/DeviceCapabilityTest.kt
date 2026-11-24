package co.asclepio.mobile.feature.interview

import co.asclepio.mobile.core.auth.TestDataStore
import co.asclepio.mobile.feature.interview.data.ServerNoticeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Equipo capaz y selección del motor (F-11-mobile T7), con RAM, modelo y arranque inyectables. */
class DeviceCapabilityTest {
    @get:Rule val tmp = TemporaryFolder()
    private val gib = 1024L * 1024 * 1024

    private fun capability(mem: Long = 6 * gib, model: Boolean = true) = DeviceCapability({ mem }, { model })

    @Test fun `R6 es capaz con al menos 5,5 GiB y el modelo verificado`() {
        assertTrue(capability(mem = 5 * gib + gib / 2).isCapable())
        assertFalse(capability(mem = 5 * gib + gib / 2 - 1).isCapable())
        assertFalse(capability(model = false).isCapable())
        assertTrue(capability(model = false).hasEnoughMemory())
    }

    @Test fun `R6 un arranque del Engine de mas de 20 s deja el equipo no capaz en esta sesion`() = runTest {
        val c = capability()
        val start = currentTime
        assertFalse(c.initialize { delay(21_000) })
        assertEquals(20_000L, currentTime - start)
        assertFalse(c.isCapable())
        var called = false
        assertFalse(c.initialize { called = true })
        assertFalse(called)
    }

    @Test fun `R6 un arranque que falla tambien deja el equipo no capaz y uno rapido lo mantiene`() = runTest {
        val failing = capability()
        assertFalse(failing.initialize { error("GPU") })
        assertFalse(failing.isCapable())
        val ok = capability()
        assertTrue(ok.initialize { delay(3_000) })
        assertTrue(ok.isCapable())
    }

    @Test fun `R6 sin equipo capaz el aviso del servidor se muestra una sola vez`() = runTest {
        assertEquals(EngineChoice.LOCAL, selectEngine(capable = true, serverNoticeSeen = false))
        val ds = TestDataStore(tmp.root.resolve("interview.preferences_pb"))
        try {
            val notice = ServerNoticeStore(ds.dataStore)
            assertEquals(EngineChoice.SERVER_NOTICE, selectEngine(false, notice.seen()))
            notice.markSeen()
            assertEquals(EngineChoice.SERVER, selectEngine(false, notice.seen()))
            assertEquals(EngineChoice.LOCAL, selectEngine(true, notice.seen()))
        } finally {
            ds.close()
        }
    }
}
