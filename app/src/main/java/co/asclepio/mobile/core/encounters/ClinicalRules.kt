package co.asclepio.mobile.core.encounters

import java.math.BigDecimal

/** Entrada privada en RAM: ningún valor inicial representa una toma ya elegida. */
data class RawVitalSigns(
    val respiratoryRate: String = "",
    val oxygenSaturation: String = "",
    val supplementalOxygen: Boolean? = null,
    val systolicBp: String = "",
    val heartRate: String = "",
    val consciousness: ConsciousnessLevel? = null,
    val temperatureC: String = "",
    val diastolicBp: String = "",
    val glucoseMgDl: String = "",
) {
    override fun toString() = "RawVitalSigns(***)"
}

enum class VitalField(val wireName: String) {
    RespiratoryRate("respiratoryRate"),
    OxygenSaturation("oxygenSaturation"),
    SupplementalOxygen("supplementalOxygen"),
    SystolicBp("systolicBp"),
    HeartRate("heartRate"),
    Consciousness("consciousness"),
    TemperatureC("temperatureC"),
    DiastolicBp("diastolicBp"),
    GlucoseMgDl("glucoseMgDl"),
}

enum class ClinicalValueType(val wireType: String) {
    Integer("integer"), Number("number"), Boolean("boolean"), Enum("enum"),
}

data class VitalRule(
    val type: ClinicalValueType,
    val required: Boolean,
    val minimum: Int? = null,
    val maximum: Int? = null,
)

/** Código local, sin recursos Android, valores privados ni mensajes recibidos del backend. */
enum class ClinicalValidationError {
    Required, Integer, Number, Range, Precision, Coherence, Length, Count,
}

data class RawSymptomFinding(
    val kind: SymptomFindingKind? = null,
    val description: String = "",
) {
    override fun toString() = "RawSymptomFinding(***)"
}

enum class FindingField { Kind, Description }

data class FindingsValidation(
    val batch: ClinicalValidationError? = null,
    val rows: Map<Int, Map<FindingField, ClinicalValidationError>> = emptyMap(),
) {
    val isValid: Boolean get() = batch == null && rows.isEmpty()
}

/**
 * OpenAPI 0.4.0 + PA-F03-1–4: PROVISIONAL, no validado clínicamente.
 * La prueba de contrato contrasta la tabla con el snapshot fuera del APK.
 * Precisión máxima de un decimal y sistólica > diastólica son reglas publicadas explícitas;
 * no se infieren desde prosa, no se redondean medidas ni se calculan niveles.
 */
object ClinicalRules {
    val vitalDefinitions: Map<VitalField, VitalRule> = linkedMapOf(
        VitalField.RespiratoryRate to VitalRule(ClinicalValueType.Integer, true, 4, 80),
        VitalField.OxygenSaturation to VitalRule(ClinicalValueType.Integer, true, 50, 100),
        VitalField.SupplementalOxygen to VitalRule(ClinicalValueType.Boolean, true),
        VitalField.SystolicBp to VitalRule(ClinicalValueType.Integer, true, 40, 300),
        VitalField.HeartRate to VitalRule(ClinicalValueType.Integer, true, 20, 300),
        VitalField.Consciousness to VitalRule(ClinicalValueType.Enum, true),
        VitalField.TemperatureC to VitalRule(ClinicalValueType.Number, true, 25, 45),
        VitalField.DiastolicBp to VitalRule(ClinicalValueType.Integer, false, 20, 200),
        VitalField.GlucoseMgDl to VitalRule(ClinicalValueType.Integer, false, 20, 600),
    )

    const val FINDINGS_MIN = 1
    const val FINDINGS_MAX = 20
    const val DESCRIPTION_MIN = 1
    const val DESCRIPTION_MAX = 500

    /** Orden estable del formulario: permite que la UI enfoque el primer control inválido. */
    fun validateVitalSigns(raw: RawVitalSigns): Map<VitalField, ClinicalValidationError> {
        val errors = linkedMapOf<VitalField, ClinicalValidationError>()
        val numbers = mutableMapOf<VitalField, BigDecimal>()
        for ((field, rule) in vitalDefinitions) {
            when (field) {
                VitalField.SupplementalOxygen -> {
                    if (raw.supplementalOxygen == null) errors[field] = ClinicalValidationError.Required
                }
                VitalField.Consciousness -> {
                    if (raw.consciousness == null) errors[field] = ClinicalValidationError.Required
                }
                else -> {
                    val text = raw.numberText(field).trim()
                    if (text.isEmpty()) {
                        if (rule.required) errors[field] = ClinicalValidationError.Required
                        continue
                    }
                    val number = decimal(text)
                    when {
                        number == null -> errors[field] = ClinicalValidationError.Number
                        // Antes de normalizar escala: un exponente pegado extremo puede desbordar stripTrailingZeros.
                        number < BigDecimal(requireNotNull(rule.minimum)) || number > BigDecimal(requireNotNull(rule.maximum)) ->
                            errors[field] = ClinicalValidationError.Range
                        rule.type == ClinicalValueType.Integer && number.stripTrailingZeros().scale() > 0 ->
                            errors[field] = ClinicalValidationError.Integer
                        field == VitalField.TemperatureC && number.stripTrailingZeros().scale() > 1 ->
                            errors[field] = ClinicalValidationError.Precision
                        else -> numbers[field] = number
                    }
                }
            }
        }
        val systolic = numbers[VitalField.SystolicBp]
        val diastolic = numbers[VitalField.DiastolicBp]
        // No sustituir errores de forma/rango por una comparación de valores ya inválidos.
        if (systolic != null && diastolic != null && systolic <= diastolic) {
            errors[VitalField.SystolicBp] = ClinicalValidationError.Coherence
            errors[VitalField.DiastolicBp] = ClinicalValidationError.Coherence
        }
        return vitalDefinitions.keys.mapNotNull { field -> errors[field]?.let { field to it } }.toMap()
    }

    /** null impide crear un cuerpo de una toma inválida; nunca trunca ni rellena vacíos. */
    fun vitalSignsRequest(raw: RawVitalSigns): CreateVitalSignsRequest? {
        if (validateVitalSigns(raw).isNotEmpty()) return null
        fun number(field: VitalField) = requireNotNull(decimal(raw.numberText(field).trim()))
        fun optional(field: VitalField) = raw.numberText(field).takeIf { it.isNotBlank() }?.let { number(field).intValueExact() }
        return CreateVitalSignsRequest(
            respiratoryRate = number(VitalField.RespiratoryRate).intValueExact(),
            oxygenSaturation = number(VitalField.OxygenSaturation).intValueExact(),
            supplementalOxygen = requireNotNull(raw.supplementalOxygen),
            systolicBp = number(VitalField.SystolicBp).intValueExact(),
            heartRate = number(VitalField.HeartRate).intValueExact(),
            consciousness = requireNotNull(raw.consciousness),
            temperatureC = number(VitalField.TemperatureC).toDouble(),
            diastolicBp = optional(VitalField.DiastolicBp),
            glucoseMgDl = optional(VitalField.GlucoseMgDl),
        )
    }

    fun validateFindings(rows: List<RawSymptomFinding>): FindingsValidation {
        val errors = linkedMapOf<Int, Map<FindingField, ClinicalValidationError>>()
        rows.forEachIndexed { index, row ->
            val fields = linkedMapOf<FindingField, ClinicalValidationError>()
            if (row.kind == null) fields[FindingField.Kind] = ClinicalValidationError.Required
            val length = row.description.trim().length
            when {
                length < DESCRIPTION_MIN -> fields[FindingField.Description] = ClinicalValidationError.Required
                length > DESCRIPTION_MAX -> fields[FindingField.Description] = ClinicalValidationError.Length
            }
            if (fields.isNotEmpty()) errors[index] = fields.toMap()
        }
        return FindingsValidation(
            batch = if (rows.size !in FINDINGS_MIN..FINDINGS_MAX) ClinicalValidationError.Count else null,
            rows = errors.toMap(),
        )
    }

    fun findingsRequest(rows: List<RawSymptomFinding>): CreateSymptomFindingsRequest? {
        if (!validateFindings(rows).isValid) return null
        return CreateSymptomFindingsRequest(rows.map { row ->
            SymptomFindingInput(kind = requireNotNull(row.kind), description = row.description.trim())
        })
    }

    // Coma y punto se admiten como separador decimal; BigDecimal rechaza agrupadores y no finitos.
    // Comprobar precisión antes de Double evita aceptar 36.70000000000000000001 por pérdida binaria.
    private fun decimal(text: String): BigDecimal? = text.replace(',', '.').toBigDecimalOrNull()

    private fun RawVitalSigns.numberText(field: VitalField): String = when (field) {
        VitalField.RespiratoryRate -> respiratoryRate
        VitalField.OxygenSaturation -> oxygenSaturation
        VitalField.SystolicBp -> systolicBp
        VitalField.HeartRate -> heartRate
        VitalField.TemperatureC -> temperatureC
        VitalField.DiastolicBp -> diastolicBp
        VitalField.GlucoseMgDl -> glucoseMgDl
        else -> error("Campo no numérico")
    }
}
