package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.core.network.appJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Borrador fuera del repo: ejecutar solo al activar F-03-mobile. */
class ClinicalRulesTest {
    private val valid = RawVitalSigns(
        respiratoryRate = "18", oxygenSaturation = "98", supplementalOxygen = false,
        systolicBp = "120", heartRate = "75", consciousness = ConsciousnessLevel.A,
        temperatureC = "36.7",
    )

    private fun RawVitalSigns.withNumber(field: VitalField, value: String): RawVitalSigns = when (field) {
        VitalField.RespiratoryRate -> copy(respiratoryRate = value)
        VitalField.OxygenSaturation -> copy(oxygenSaturation = value)
        VitalField.SystolicBp -> copy(systolicBp = value)
        VitalField.HeartRate -> copy(heartRate = value)
        VitalField.TemperatureC -> copy(temperatureC = value)
        VitalField.DiastolicBp -> copy(diastolicBp = value)
        VitalField.GlucoseMgDl -> copy(glucoseMgDl = value)
        else -> error("No es un campo numérico")
    }

    @Test fun `R5 campos iniciales vacios exigen siete elecciones sin crear ceros ni oxigeno implicito`() {
        val raw = RawVitalSigns()
        val errors = ClinicalRules.validateVitalSigns(raw)
        assertEquals(ClinicalRules.vitalDefinitions.filterValues { it.required }.keys, errors.keys)
        assertEquals(7, errors.size)
        assertTrue(errors.values.all { it == ClinicalValidationError.Required })
        assertNull(raw.supplementalOxygen)
        assertNull(raw.consciousness)
        assertEquals("", raw.diastolicBp)
        assertEquals("", raw.glucoseMgDl)
        assertNull(ClinicalRules.vitalSignsRequest(raw))
    }

    @Test fun `R6 todos los rangos numericos son inclusivos y rechazan ambos extremos exteriores`() {
        for ((field, rule) in ClinicalRules.vitalDefinitions) {
            val min = rule.minimum ?: continue
            val max = requireNotNull(rule.maximum)
            // Un valor alto de diastólica requiere sistólica mayor, sin alterar el límite publicado.
            val base = if (field == VitalField.DiastolicBp) valid.copy(systolicBp = "250") else valid
            for (bound in listOf(min, max)) {
                val raw = base.withNumber(field, bound.toString())
                assertEquals(field.name, emptyMap<VitalField, ClinicalValidationError>(), ClinicalRules.validateVitalSigns(raw))
                assertNotNull(field.name, ClinicalRules.vitalSignsRequest(raw))
            }
            for (outside in listOf(min - 1, max + 1)) {
                val raw = base.withNumber(field, outside.toString())
                assertEquals(field.name, ClinicalValidationError.Range, ClinicalRules.validateVitalSigns(raw)[field])
                assertNull(field.name, ClinicalRules.vitalSignsRequest(raw))
            }
        }
    }

    @Test fun `R6 enteros rechazan fracciones sin truncarlas y numeros invalidos nunca se convierten en cero`() {
        for ((field, rule) in ClinicalRules.vitalDefinitions) {
            if (rule.type != ClinicalValueType.Integer) continue
            val raw = valid.withNumber(field, "${rule.minimum}.5")
            assertEquals(field.name, ClinicalValidationError.Integer, ClinicalRules.validateVitalSigns(raw)[field])
            assertNull(ClinicalRules.vitalSignsRequest(raw))
        }
        for (input in listOf("NaN", "Infinity", "-Infinity", "abc", "1.2.3", "1,2.3", "1 000")) {
            val raw = valid.copy(heartRate = input)
            assertEquals(input, ClinicalValidationError.Number, ClinicalRules.validateVitalSigns(raw)[VitalField.HeartRate])
            assertNull(ClinicalRules.vitalSignsRequest(raw))
        }
        // 75.0 representa un entero; no se añade una restricción léxica ajena al contrato.
        assertEquals(75, ClinicalRules.vitalSignsRequest(valid.copy(heartRate = "75.0"))?.heartRate)
    }

    @Test fun `R6 temperatura acepta coma o punto y verifica decimal exacto sin epsilon ni redondeo`() {
        for (input in listOf("36.7", "36,7", " 36,7 ", "36.7000", "25", "45.0")) {
            val raw = valid.copy(temperatureC = input)
            assertTrue(input, ClinicalRules.validateVitalSigns(raw).isEmpty())
            assertNotNull(input, ClinicalRules.vitalSignsRequest(raw))
        }
        assertEquals(36.7, ClinicalRules.vitalSignsRequest(valid.copy(temperatureC = "36,7"))!!.temperatureC, 0.0)
        for (input in listOf("36.75", "36.7000000001", "36.70000000000000000000000001")) {
            val raw = valid.copy(temperatureC = input)
            assertEquals(input, ClinicalValidationError.Precision, ClinicalRules.validateVitalSigns(raw)[VitalField.TemperatureC])
            assertNull(input, ClinicalRules.vitalSignsRequest(raw))
        }
    }

    @Test fun `R6 exponentes extremos pegados producen rango local sin desbordar la normalizacion`() {
        for (field in listOf(VitalField.HeartRate, VitalField.TemperatureC)) {
            for (input in listOf("100e2147483647", "100e-2147483647")) {
                val raw = valid.withNumber(field, input)
                assertEquals(input, ClinicalValidationError.Range, ClinicalRules.validateVitalSigns(raw)[field])
                assertNull(ClinicalRules.vitalSignsRequest(raw))
            }
        }
    }

    @Test fun `R6 presion sistolica debe superar diastolica y ambos campos reciben coherencia`() {
        for (diastolic in listOf("120", "121")) {
            val errors = ClinicalRules.validateVitalSigns(valid.copy(diastolicBp = diastolic))
            assertEquals(ClinicalValidationError.Coherence, errors[VitalField.SystolicBp])
            assertEquals(ClinicalValidationError.Coherence, errors[VitalField.DiastolicBp])
            assertNull(ClinicalRules.vitalSignsRequest(valid.copy(diastolicBp = diastolic)))
        }
        assertTrue(ClinicalRules.validateVitalSigns(valid.copy(diastolicBp = "119")).isEmpty())
        val ordered = ClinicalRules.validateVitalSigns(valid.copy(diastolicBp = "120", temperatureC = "36.75"))
        assertEquals(listOf(VitalField.SystolicBp, VitalField.TemperatureC, VitalField.DiastolicBp), ordered.keys.toList())
        val range = ClinicalRules.validateVitalSigns(valid.copy(systolicBp = "301", diastolicBp = "200"))
        assertEquals(ClinicalValidationError.Range, range[VitalField.SystolicBp])
        assertFalse(range.containsKey(VitalField.DiastolicBp))
    }

    @Test fun `R7 toma conserva false enum elegido y opcionales blancos ausentes del cuerpo`() {
        val raw = valid.copy(diastolicBp = " \t", glucoseMgDl = "\n")
        val request = requireNotNull(ClinicalRules.vitalSignsRequest(raw))
        val body = appJson.parseToJsonElement(appJson.encodeToString(request)).jsonObject
        assertEquals(setOf("respiratoryRate", "oxygenSaturation", "supplementalOxygen", "systolicBp", "heartRate", "consciousness", "temperatureC"), body.keys)
        assertFalse(body.getValue("supplementalOxygen").jsonPrimitive.boolean)
        assertEquals("A", body.getValue("consciousness").jsonPrimitive.content)
        for (level in ConsciousnessLevel.entries) {
            assertEquals(level, ClinicalRules.vitalSignsRequest(valid.copy(consciousness = level))?.consciousness)
        }
        val complete = requireNotNull(ClinicalRules.vitalSignsRequest(valid.copy(diastolicBp = "80", glucoseMgDl = "100")))
        assertEquals(80, complete.diastolicBp)
        assertEquals(100, complete.glucoseMgDl)
        assertEquals(9, appJson.parseToJsonElement(appJson.encodeToString(complete)).jsonObject.size)
    }

    @Test fun `R8 hallazgos aceptan uno a veinte y rechazan lote vacio o veintiuno`() {
        val row = RawSymptomFinding(SymptomFindingKind.SYMPTOM, "Texto literal")
        for (count in listOf(1, 20)) {
            val rows = List(count) { row }
            assertTrue(ClinicalRules.validateFindings(rows).isValid)
            assertEquals(count, ClinicalRules.findingsRequest(rows)?.items?.size)
        }
        for (count in listOf(0, 21)) {
            val rows = List(count) { row }
            assertEquals(ClinicalValidationError.Count, ClinicalRules.validateFindings(rows).batch)
            assertNull(ClinicalRules.findingsRequest(rows))
        }
    }

    @Test fun `R8 tipo y descripcion se validan por fila y solo se recortan extremos al enviar`() {
        val invalid = listOf(RawSymptomFinding(), RawSymptomFinding(SymptomFindingKind.FINDING, " \t\n"), RawSymptomFinding(SymptomFindingKind.COMPLAINT_NOTE, "x".repeat(501)))
        val result = ClinicalRules.validateFindings(invalid)
        assertEquals(ClinicalValidationError.Required, result.rows[0]?.get(FindingField.Kind))
        assertEquals(ClinicalValidationError.Required, result.rows[0]?.get(FindingField.Description))
        assertEquals(ClinicalValidationError.Required, result.rows[1]?.get(FindingField.Description))
        assertEquals(ClinicalValidationError.Length, result.rows[2]?.get(FindingField.Description))
        assertNull(ClinicalRules.findingsRequest(invalid))
        val literal = "  DEV José Müller: dolor  3/10\nconservado.  "
        val rows = listOf(RawSymptomFinding(SymptomFindingKind.COMPLAINT_NOTE, literal), RawSymptomFinding(SymptomFindingKind.FINDING, "  ${"x".repeat(500)}  "))
        val request = requireNotNull(ClinicalRules.findingsRequest(rows))
        assertEquals(literal, rows[0].description)
        assertEquals(literal.trim(), request.items[0].description)
        assertEquals(SymptomFindingKind.COMPLAINT_NOTE, request.items[0].kind)
        assertEquals(500, request.items[1].description.length)
        assertEquals(setOf("items"), appJson.parseToJsonElement(appJson.encodeToString(request)).jsonObject.keys)
    }

    @Test fun `R19 borradores y resultados de validacion no vuelcan texto privado en toString`() {
        assertEquals("RawVitalSigns(***)", valid.toString())
        assertEquals("RawSymptomFinding(***)", RawSymptomFinding(SymptomFindingKind.FINDING, "DEV secreto").toString())
        assertFalse(ClinicalRules.validateFindings(listOf(RawSymptomFinding(description = "DEV secreto"))).toString().contains("DEV secreto"))
    }

    @Test fun `R20 tabla tipos obligatoriedad rangos y dominios coinciden con snapshot readonly 040`() {
        val path = listOf(File("../asclepio-api/openapi/asclepio-api.v1.json"), File("../../asclepio-api/openapi/asclepio-api.v1.json"))
            .firstOrNull { it.isFile } ?: error("Falta el snapshot readonly de asclepio-api para contrastar el contrato")
        val root = Json.parseToJsonElement(path.readText()).jsonObject
        assertEquals("0.4.0", root.getValue("info").jsonObject.getValue("version").jsonPrimitive.content)
        val schemas = root.getValue("components").jsonObject.getValue("schemas").jsonObject
        val vital = schemas.getValue("CreateVitalSignsDto").jsonObject
        val props = vital.getValue("properties").jsonObject
        val required = vital.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(props.keys, ClinicalRules.vitalDefinitions.keys.map { it.wireName }.toSet())
        for ((field, rule) in ClinicalRules.vitalDefinitions) {
            val prop = props.getValue(field.wireName).jsonObject
            assertEquals(field.wireName, field.wireName in required, rule.required)
            if (rule.type != ClinicalValueType.Enum) assertEquals(rule.type.wireType, prop.getValue("type").jsonPrimitive.content)
            assertEquals(field.wireName, prop["minimum"]?.jsonPrimitive?.int, rule.minimum)
            assertEquals(field.wireName, prop["maximum"]?.jsonPrimitive?.int, rule.maximum)
        }
        fun enumValues(name: String) = schemas.getValue(name).jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(enumValues("ConsciousnessLevel"), ConsciousnessLevel.entries.map { it.name })
        assertEquals(enumValues("SymptomFindingKind"), SymptomFindingKind.entries.map { it.name })
        val items = schemas.getValue("CreateSymptomFindingsDto").jsonObject.getValue("properties").jsonObject.getValue("items").jsonObject
        assertEquals(items.getValue("minItems").jsonPrimitive.int, ClinicalRules.FINDINGS_MIN)
        assertEquals(items.getValue("maxItems").jsonPrimitive.int, ClinicalRules.FINDINGS_MAX)
        val description = schemas.getValue("SymptomFindingInputDto").jsonObject.getValue("properties").jsonObject.getValue("description").jsonObject
        assertEquals(description.getValue("minLength").jsonPrimitive.int, ClinicalRules.DESCRIPTION_MIN)
        assertEquals(description.getValue("maxLength").jsonPrimitive.int, ClinicalRules.DESCRIPTION_MAX)
    }
}
