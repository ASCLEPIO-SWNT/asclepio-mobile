package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.queue.EstimateBasis
import co.asclepio.mobile.feature.encounters.queue.QueueConnection
import co.asclepio.mobile.feature.encounters.queue.QueuePhase
import co.asclepio.mobile.feature.encounters.queue.QueuePositionDto
import co.asclepio.mobile.feature.encounters.queue.QueueUiState
import co.asclepio.mobile.feature.encounters.queue.WaitEstimateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Contenido de la tarjeta de fila (función pura) y su catálogo; el render Compose está en androidTest. */
class QueuePositionCardTest {
    private val received = 5_000_000L

    private fun live(
        phase: QueuePhase,
        position: Int? = null,
        estimate: WaitEstimateDto? = null,
        basis: EstimateBasis? = null,
        connection: QueueConnection = QueueConnection.LIVE,
        ended: Boolean = false,
    ) = QueueUiState(
        QueuePositionDto(phase, position, position?.let { it - 1 }, estimate, basis, "2026-10-27T15:00:00.000Z"),
        received, connection, ended,
    )

    private fun m(res: Int, vararg args: Any) = UiMessage(res, args.toList())

    @Test fun `R3 en espera de triage con posicion mayor que 1 muestra fase posicion personas delante y rango`() {
        val content = queueCardContent(live(QueuePhase.WAITING_TRIAGE, 4, WaitEstimateDto(15, 35), EstimateBasis.RECENT_THROUGHPUT), received)!!
        assertEquals(
            listOf(
                m(R.string.queue_phase_waiting_triage),
                m(R.string.queue_position, 4),
                m(R.string.queue_ahead_many, 3),
                m(R.string.queue_estimate_range, 15, 35),
                m(R.string.queue_estimate_may_change),
            ),
            content.lines,
        )
        assertNull(content.offline)
        assertFalse(content.reconnecting)
    }

    @Test fun `R3 en espera del medico con una persona delante usa el singular`() {
        val content = queueCardContent(live(QueuePhase.WAITING_CARE, 2, WaitEstimateDto(0, 5), EstimateBasis.RECENT_THROUGHPUT), received)!!
        assertEquals(
            listOf(
                m(R.string.queue_phase_waiting_care),
                m(R.string.queue_position, 2),
                m(R.string.queue_ahead_one),
                m(R.string.queue_estimate_range, 0, 5),
                m(R.string.queue_estimate_may_change),
            ),
            content.lines,
        )
    }

    @Test fun `R4 con posicion 1 muestra Eres el siguiente sin numero ni personas delante`() {
        val lines = queueCardContent(live(QueuePhase.WAITING_CARE, 1, WaitEstimateDto(0, 5), EstimateBasis.RECENT_THROUGHPUT), received)!!.lines
        assertEquals(m(R.string.queue_phase_waiting_care), lines.first())
        assertTrue(m(R.string.queue_next) in lines)
        assertTrue(lines.none { it.resId == R.string.queue_position || it.resId == R.string.queue_ahead_one || it.resId == R.string.queue_ahead_many })
    }

    @Test fun `R5 en CALLED muestra Te estan llamando y en DONE Atencion finalizada`() {
        assertEquals(listOf(m(R.string.queue_called)), queueCardContent(live(QueuePhase.CALLED), received)!!.lines)
        assertEquals(listOf(m(R.string.queue_done)), queueCardContent(live(QueuePhase.DONE, ended = true), received)!!.lines)
    }

    @Test fun `R6 con INSUFFICIENT_DATA muestra el aviso en lugar del rango`() {
        val lines = queueCardContent(live(QueuePhase.WAITING_TRIAGE, 3, null, EstimateBasis.INSUFFICIENT_DATA), received)!!.lines
        assertEquals(
            listOf(
                m(R.string.queue_phase_waiting_triage),
                m(R.string.queue_position, 3),
                m(R.string.queue_ahead_many, 2),
                m(R.string.queue_estimate_insufficient),
            ),
            lines,
        )
    }

    @Test fun `R7 en NO_ACTIVE muestra Acercate a recepcion sin posicion`() {
        assertEquals(listOf(m(R.string.queue_no_active)), queueCardContent(live(QueuePhase.NO_ACTIVE), received)!!.lines)
    }

    @Test fun `R8 al reconectar conserva el ultimo dato y muestra Sin conexion con su antiguedad y el indicador`() {
        val state = live(QueuePhase.WAITING_TRIAGE, 4, WaitEstimateDto(15, 35), EstimateBasis.RECENT_THROUGHPUT, QueueConnection.RECONNECTING)
        val content = queueCardContent(state, received + (7 * 60 + 30) * 1_000L)!!
        assertEquals(m(R.string.queue_position, 4), content.lines[1])
        assertEquals(m(R.string.queue_offline_age, 7), content.offline)
        assertTrue(content.reconnecting)
    }

    @Test fun `R8 sin dato previo muestra Sin conexion y el indicador y sin dato aun Conectando`() {
        val offline = queueCardContent(QueueUiState(connection = QueueConnection.RECONNECTING), received)!!
        assertEquals(emptyList<UiMessage>(), offline.lines)
        assertEquals(m(R.string.queue_offline), offline.offline)
        assertTrue(offline.reconnecting)
        assertEquals(listOf(m(R.string.queue_connecting)), queueCardContent(QueueUiState(), received)!!.lines)
    }

    @Test fun `R5 la tarjeta no muestra Atencion finalizada para un prerregistro nuevo en curso`() {
        val ended = live(QueuePhase.DONE, ended = true).copy(key = "pre-1")
        assertTrue(showsQueueCard("pre-1", ended))
        assertFalse(showsQueueCard("pre-2", ended))
        assertTrue(showsQueueCard(null, ended))
        assertTrue(showsQueueCard("pre-2", QueueUiState(key = "pre-2")))
        assertFalse(showsQueueCard(null, QueueUiState(key = "pre-2")))
    }

    @Test fun `R9 terminado sin dato o con una fase desconocida no se muestra la tarjeta`() {
        assertNull(queueCardContent(QueueUiState(ended = true), received))
        assertNull(queueCardContent(live(QueuePhase.UNKNOWN), received))
    }

    @Test fun `R10 la tarjeta solo muestra los campos de la instantanea`() {
        val content = queueCardContent(live(QueuePhase.WAITING_TRIAGE, 4, WaitEstimateDto(15, 35), EstimateBasis.RECENT_THROUGHPUT), received)!!
        val args = content.lines.flatMap { it.args }
        assertEquals(listOf<Any>(4, 3, 15, 35), args)
    }

    private val queueKeys = listOf(
        "queue_title", "queue_phase_waiting_triage", "queue_phase_waiting_care", "queue_position", "queue_next",
        "queue_ahead_one", "queue_ahead_many", "queue_estimate_range", "queue_estimate_may_change",
        "queue_estimate_insufficient", "queue_called", "queue_done", "queue_no_active", "queue_connecting",
        "queue_offline", "queue_offline_age", "queue_reconnecting",
    )

    private fun strings(folder: String): Map<String, String> {
        val dir = File("src/main/res/$folder")
        return dir.listFiles { f -> f.extension == "xml" }.orEmpty().flatMap { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) }.map { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }.toMap()
    }

    @Test fun `R11 los textos de la tarjeta estan en los cinco catalogos con los textos del requisito en espanol`() {
        val es = strings("values")
        for (folder in listOf("values", "values-en", "values-pt", "values-fr", "values-de")) {
            val catalog = strings(folder)
            for (key in queueKeys) assertTrue("$folder: $key", catalog[key]?.isNotBlank() == true)
        }
        assertEquals("Esperando triage", es["queue_phase_waiting_triage"])
        assertEquals("Esperando al médico", es["queue_phase_waiting_care"])
        assertEquals("Eres el %1\$d.º", es["queue_position"])
        assertEquals("%1\$d personas delante", es["queue_ahead_many"])
        assertEquals("%1\$d–%2\$d min", es["queue_estimate_range"])
        assertEquals("Puede cambiar si llegan casos más urgentes", es["queue_estimate_may_change"])
        assertEquals("Eres el siguiente", es["queue_next"])
        assertEquals("Te están llamando: acércate al consultorio", es["queue_called"])
        assertEquals("Atención finalizada", es["queue_done"])
        assertEquals("Aún no hay datos suficientes para estimar el tiempo", es["queue_estimate_insufficient"])
        assertEquals("Acércate a recepción para entrar en la fila", es["queue_no_active"])
        assertEquals("Sin conexión · actualizado hace %1\$d min", es["queue_offline_age"])
    }

    @Test fun `R11 la tarjeta se anuncia con liveRegion educado y no tiene textos literales`() {
        val source = File("src/main/java/co/asclepio/mobile/feature/encounters/ui/QueuePositionCard.kt").readText()
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
        assertFalse(source.contains("LiveRegionMode.Assertive"))
        assertFalse("Sin Text(\"…\") literal", Regex("""Text\(\s*"""").containsMatchIn(source))
    }
}
