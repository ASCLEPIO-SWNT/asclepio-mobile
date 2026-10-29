package co.asclepio.mobile.feature.patients.ui

import androidx.annotation.StringRes
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.isAdult
import co.asclepio.mobile.core.model.FULL_NAME_MAX
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.patients.data.PatientProfileDto
import co.asclepio.mobile.feature.patients.data.UpdatePatientProfileRequest
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ProfileField { FullName, BirthDate, Sex }

/** Campos editables de "Mis datos" (PA-F02-5). Solo viven en memoria (R27); el `toString()` enmascara nombre y fecha. */
data class ProfileForm(val fullName: String = "", val birthDate: LocalDate? = null, val sex: Sex? = null) {
    override fun toString(): String = "ProfileForm(fullName=***, birthDate=***, sex=$sex)"
}

fun PatientProfileDto.toForm(): ProfileForm =
    ProfileForm(fullName = fullName.orEmpty(), birthDate = parseBirthDate(birthDate), sex = sex)

private fun parseBirthDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Cuerpo del `PATCH` con solo los campos que difieren del perfil cargado (D9, R3, R4). El nombre se compara y se
 * envía sin espacios en los extremos; la fecha va en `YYYY-MM-DD`.
 */
fun diff(loaded: PatientProfileDto, form: ProfileForm): UpdatePatientProfileRequest {
    val name = form.fullName.trim()
    return UpdatePatientProfileRequest(
        fullName = name.takeIf { it != loaded.fullName.orEmpty() },
        birthDate = form.birthDate?.takeIf { it != parseBirthDate(loaded.birthDate) }?.toString(),
        sex = form.sex?.takeIf { it != loaded.sex },
    )
}

/** Validación local de "Mis datos" (D10, R5). La API valida igual; esto es para la experiencia de uso. */
fun validateProfile(form: ProfileForm, clock: Clock): Map<ProfileField, UiMessage> = buildMap {
    val name = form.fullName.trim()
    when {
        name.isEmpty() -> put(ProfileField.FullName, UiMessage(R.string.error_required))
        name.length > FULL_NAME_MAX -> put(ProfileField.FullName, UiMessage(R.string.error_max_length, listOf(FULL_NAME_MAX)))
    }
    if (form.birthDate != null && !isAdult(form.birthDate, clock)) put(ProfileField.BirthDate, UiMessage(R.string.error_underage_profile))
}

/** Un valor mostrado: un texto de strings.xml o un dato tal cual llega de la API. */
sealed interface DisplayText {
    data class Res(@param:StringRes val resId: Int) : DisplayText

    data class Raw(val value: String) : DisplayText {
        override fun toString(): String = "Raw(***)"
    }
}

/** Lo que "Mis datos" muestra del perfil (R1): etiquetas de documento y sexo, fecha `dd/MM/yyyy` y "—" en los nulos. */
data class ProfileDisplay(
    val documentType: DisplayText,
    val documentNumber: DisplayText,
    val fullName: DisplayText,
    val birthDate: DisplayText,
    val sex: DisplayText,
)

private val missing = DisplayText.Res(R.string.value_missing)
private val birthDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private val documentTypeLabels = mapOf(
    "CC" to R.string.document_type_cc,
    "CE" to R.string.document_type_ce,
    "PA" to R.string.document_type_pa,
    "TI" to R.string.document_type_ti,
    "RC" to R.string.document_type_rc,
)

/** Etiqueta del tipo de documento; un código desconocido se muestra tal cual (PA-M02-1). */
fun documentTypeLabel(code: String?): DisplayText = when (code) {
    null -> missing
    else -> documentTypeLabels[code]?.let { DisplayText.Res(it) } ?: DisplayText.Raw(code)
}

fun sexLabel(sex: Sex?): DisplayText = when (sex) {
    null -> missing
    Sex.M -> DisplayText.Res(R.string.sex_m)
    Sex.F -> DisplayText.Res(R.string.sex_f)
    Sex.X -> DisplayText.Res(R.string.sex_x)
}

fun formatBirthDate(date: LocalDate): String = date.format(birthDateFormat)

fun profileDisplay(dto: PatientProfileDto): ProfileDisplay = ProfileDisplay(
    documentType = documentTypeLabel(dto.documentType),
    documentNumber = dto.documentNumber?.let { DisplayText.Raw(it) } ?: missing,
    fullName = dto.fullName?.let { DisplayText.Raw(it) } ?: missing,
    birthDate = parseBirthDate(dto.birthDate)?.let { DisplayText.Raw(formatBirthDate(it)) } ?: missing,
    sex = sexLabel(dto.sex),
)
