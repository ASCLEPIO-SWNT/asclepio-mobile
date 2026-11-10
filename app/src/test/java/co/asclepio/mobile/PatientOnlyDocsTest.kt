package co.asclepio.mobile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La documentación que leen los agentes describe una app solo del paciente (DT-01-mobile). */
class PatientOnlyDocsTest {
    private val root = File("..")
    private val current = listOf("AGENTS.md", "docs/architecture.md", "docs/verification.md", "CHECKPOINTS.md")
    private val history = "docs/mobile-migration.md"
    private val historicMarkers = listOf("sustitu", "retirad", "histórico")

    private fun lines(path: String): List<String> {
        val file = File(root, path)
        assertTrue("Falta $file", file.isFile)
        return file.readLines()
    }

    private fun String.historic() = historicMarkers.any { lowercase().contains(it) }

    /** Líneas que presentan como vigente algo retirado: un mismo renglón puede citarlo solo como sustituido/retirado. */
    private fun currentStaffMentions(path: String): List<String> = lines(path).withIndex().filter { (_, line) ->
        val lower = line.lowercase()
        lower.contains("staffaccount") || line.contains("StaffDestinations") ||
            ((lower.contains("pacientes y personal") || line.contains("F-01-staff-mobile") || line.contains("0002")) && !line.historic())
    }.map { (index, line) -> "$path:${index + 1}: $line" }

    @Test fun `R13 AGENTS architecture verification y CHECKPOINTS describen la app solo del paciente`() {
        for (path in current) {
            assertEquals(path, emptyList<String>(), currentStaffMentions(path))
            assertTrue("$path debe decir que la app es solo del paciente", lines(path).any { it.contains("solo del paciente") })
        }
    }

    @Test fun `R13 CHECKPOINTS C3 lista solo paquetes del paciente y C7 no separa principales de personal`() {
        val text = lines("CHECKPOINTS.md").joinToString("\n")
        val c3 = text.substringAfter("## C3").substringBefore("## C4")
        val c7 = text.substringAfter("## C7").substringBefore("## C8")
        assertTrue(c3.contains("feature/{account,patients,encounters,status,voice}"))
        for (retired in listOf("staffaccount", "staffencounters", "reception", "triage", "dashboards", "core/encounters")) {
            assertTrue("C3 menciona $retired", !c3.contains(retired))
        }
        assertTrue("C7 no debe describir STAFF", !c7.contains("STAFF") || c7.lines().filter { it.contains("STAFF") }.all { it.contains("nunca") })
    }

    @Test fun `R13 el plan de migracion movil queda como registro historico sustituido por ADR 0008`() {
        val text = lines(history)
        val header = text.take(5).joinToString("\n")
        assertTrue(header, header.contains("Sustituido el 2026-11-09 por ADR 0008 (Ratificación)") && header.contains("DT-01-mobile"))
        val mentions = text.withIndex().filter { (_, line) ->
            line.lowercase().contains("staffaccount") || line.contains("StaffDestinations") ||
                (line.lowercase().contains("pacientes y personal") && !line.historic())
        }
        assertEquals(emptyList<String>(), mentions.map { it.value })
    }
}
