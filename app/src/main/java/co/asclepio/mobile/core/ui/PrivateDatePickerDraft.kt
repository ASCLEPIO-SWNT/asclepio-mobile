package co.asclepio.mobile.core.ui

import co.asclepio.mobile.core.model.datePickerMillisToLocalDate
import co.asclepio.mobile.core.model.toDatePickerMillis
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val dateInputFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
private val compactDateInputFormat = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)
private val slashDateInput = Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}")
private val compactDateInput = Regex("[0-9]{8}")

/** Calendar mechanics include the RAM snapshot, including an older month after invalid input. */
internal fun privateDatePickerYearRange(selectedMillis: Long?, displayedMonthMillis: Long?): IntRange {
    val years = listOfNotNull(selectedMillis, displayedMonthMillis).map { datePickerMillisToLocalDate(it).year }
    return minOf(1900, years.minOrNull() ?: 1900)..maxOf(2100, years.maxOrNull() ?: 2100)
}

/** Unconfirmed modal draft: RAM in the owning feature VM, never a Saver, Bundle or persistent preference. */
data class PrivateDatePickerDraft(
    val isOpen: Boolean = false,
    val selectedDateMillis: Long? = null,
    val displayedMonthMillis: Long? = null,
    val inputMode: Boolean = false,
    val inputText: String = "",
) {
    val date: LocalDate? get() = selectedDateMillis?.let(::datePickerMillisToLocalDate)
    val canAccept: Boolean get() = isOpen && date != null

    fun withInput(text: String): PrivateDatePickerDraft {
        val format = when {
            slashDateInput.matches(text) -> dateInputFormat
            compactDateInput.matches(text) -> compactDateInputFormat
            else -> null
        }
        val selected = format?.let { runCatching { LocalDate.parse(text, it).toDatePickerMillis() }.getOrNull() }
        return copy(inputText = text, selectedDateMillis = selected)
    }

    fun withCalendarSelection(selected: Long?, month: Long): PrivateDatePickerDraft = copy(
        selectedDateMillis = selected,
        displayedMonthMillis = month,
        inputText = selected?.let { datePickerMillisToLocalDate(it).format(dateInputFormat) }.orEmpty(),
    )

    override fun toString(): String = "PrivateDatePickerDraft(isOpen=$isOpen, inputMode=$inputMode, date=***, text=***)"

    companion object {
        fun open(date: LocalDate?): PrivateDatePickerDraft = PrivateDatePickerDraft(
            isOpen = true,
            selectedDateMillis = date?.toDatePickerMillis(),
            displayedMonthMillis = date?.withDayOfMonth(1)?.toDatePickerMillis(),
            inputText = date?.format(dateInputFormat).orEmpty(),
        )
    }
}
