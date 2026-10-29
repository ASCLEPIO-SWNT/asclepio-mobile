package co.asclepio.mobile.core.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.platform.LocalConfiguration

/** Fecha privada en RAM. rememberDatePickerState usa un Saver y no debe emplearse con fechas del paciente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPrivateDatePickerState(initialSelectedDateMillis: Long?): DatePickerState {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, initialSelectedDateMillis) {
        DatePickerState(locale = locale, initialSelectedDateMillis = initialSelectedDateMillis)
    }
}

/** Dentro del contenido del Dialog: bloquea también los Savers internos del modo de entrada de fecha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateDatePicker(state: DatePickerState) {
    CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
        DatePicker(state)
    }
}
