package co.asclepio.mobile.core.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.platform.LocalConfiguration

/** The owner VM holds the unconfirmed snapshot; Android resources determine labels after locale change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPrivateDatePickerState(
    initialSelectedDateMillis: Long?,
    initialDisplayedMonthMillis: Long? = null,
    onChange: (Long?, Long) -> Unit = { _, _ -> },
): DatePickerState {
    val locale = LocalConfiguration.current.locales[0]
    val notify by rememberUpdatedState(onChange)
    return remember(locale) {
        val state = DatePickerState(
            locale = locale,
            initialSelectedDateMillis = initialSelectedDateMillis,
            initialDisplayedMonthMillis = initialDisplayedMonthMillis ?: initialSelectedDateMillis,
            yearRange = privateDatePickerYearRange(initialSelectedDateMillis, initialDisplayedMonthMillis),
        )
        object : DatePickerState by state {
            override var selectedDateMillis: Long?
                get() = state.selectedDateMillis
                set(value) {
                    state.selectedDateMillis = value
                    notify(state.selectedDateMillis, state.displayedMonthMillis)
                }
            override var displayedMonthMillis: Long
                get() = state.displayedMonthMillis
                set(value) {
                    state.displayedMonthMillis = value
                    notify(state.selectedDateMillis, state.displayedMonthMillis)
                }
        }
    }
}

/** Inside the Dialog: both calendar widgets and controlled text input exclude hidden internal Savers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateDatePicker(state: DatePickerState? = null, inputContent: (@Composable () -> Unit)? = null) {
    CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
        if (inputContent != null) inputContent() else DatePicker(checkNotNull(state), showModeToggle = false)
    }
}
