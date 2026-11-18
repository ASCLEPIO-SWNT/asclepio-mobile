package co.asclepio.mobile

import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.ui.PreRegistrationStatusUiState
import co.asclepio.mobile.feature.encounters.ui.followsQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** F-10-mobile T5: el stream de la fila solo se recoge dentro de `repeatOnLifecycle(STARTED)` de la entrada Home. */
class QueuePositionLifecycleTest {
    private val main = File("src/main/java/co/asclepio/mobile")
    private val navigation = File(main, "AppNavigation.kt").readText()

    private fun sources() = main.walkTopDown().filter { it.isFile && it.extension == "kt" }

    @Test fun `R1 el stream se sigue con un pre-registro en curso y se conserva el ultimo valor mientras carga o falla`() {
        assertEquals(true, PreRegistrationStatusUiState.Success("Sede", R.string.status_waiting_triage, "s", null, canCreate = false).followsQueue())
        assertEquals(true, PreRegistrationStatusUiState.Success("Sede", R.string.status_pending, "s", "e", canCreate = false).followsQueue())
        assertEquals(false, PreRegistrationStatusUiState.Success("Sede", R.string.status_closed, "s", null, canCreate = true).followsQueue())
        assertEquals(false, PreRegistrationStatusUiState.Empty.followsQueue())
        assertNull(PreRegistrationStatusUiState.Loading.followsQueue())
        assertNull(PreRegistrationStatusUiState.Error(UiMessage(R.string.error_general), refreshEnabled = true).followsQueue())
    }

    @Test fun `R1 R2 follow solo se llama desde AppNavigation y siempre dentro de repeatOnLifecycle STARTED`() {
        val callers = sources().filter { Regex("""\.follow\(\)""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals(listOf("AppNavigation.kt"), callers)
        val calls = Regex("""\.follow\(\)""").findAll(navigation).toList()
        assertEquals(1, calls.size)
        for (call in calls) {
            val before = navigation.substring(0, call.range.first)
            val block = before.lastIndexOf("repeatOnLifecycle(Lifecycle.State.STARTED) {")
            assertTrue("follow() fuera de repeatOnLifecycle(STARTED)", block >= 0)
            // Ninguna llave se cierra entre la apertura del bloque y la llamada: la llamada está dentro.
            val between = navigation.substring(block + "repeatOnLifecycle(Lifecycle.State.STARTED) {".length, call.range.first)
            assertEquals("follow() dentro del bloque", between.count { it == '{' }, between.count { it == '}' })
        }
    }

    @Test fun `R1 R2 el stream solo se abre desde el ViewModel y la tarjeta solo se compone en la entrada Home`() {
        val streamUsers = sources().filter { it.readText().contains("queuePositionStream") }.map { it.name }.toSet()
        assertEquals(setOf("AppContainer.kt", "AppNavigation.kt"), streamUsers)
        val home = navigation.substringAfter("entry<Route.Home>").substringBefore("entry<Route.MyRequests>")
        assertTrue(home.contains("QueuePositionViewModel("))
        assertTrue(home.contains("QueuePositionCard("))
        assertFalse(navigation.substringAfter("entry<Route.MyRequests>").contains("QueuePosition"))
    }

    @Test fun `R2 no hay trabajo en segundo plano para la fila`() {
        val queue = File(main, "feature/encounters/queue").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        for (forbidden in listOf("viewModelScope", "GlobalScope", "Service", "WorkManager", "AlarmManager", "ProcessLifecycleOwner")) {
            assertFalse(forbidden, queue.contains(forbidden))
        }
        val manifest = File("src/main/AndroidManifest.xml").readText()
        // Solo el servicio de metadatos de idioma de AppCompat (UI-01); ningún servicio propio.
        val services = Regex("""<service\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("androidx.appcompat.app.AppLocalesMetadataHolderService"), services)
        assertFalse(manifest.contains("FOREGROUND_SERVICE"))
    }
}
