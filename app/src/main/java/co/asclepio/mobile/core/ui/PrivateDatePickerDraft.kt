package co.asclepio.mobile.core.ui

import co.asclepio.mobile.core.model.datePickerMillisToLocalDate
import co.asclepio.mobile.core.model.toDatePickerMillis
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val dateInputFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
private val compactDateInputFormat = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)

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
        val parsed = runCatching { LocalDate.parse(text, if (text.matches(Regex("\\d{8}"))) compactDateInputFormat else dateInputFormat) }.getOrNull()
        return copy(inputText = text, selectedDateMillis = parsed?.toDatePickerMillis())
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
