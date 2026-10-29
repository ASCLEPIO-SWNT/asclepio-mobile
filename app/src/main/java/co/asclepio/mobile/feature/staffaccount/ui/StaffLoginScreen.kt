package co.asclepio.mobile.feature.staffaccount.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.text

@Composable
fun StaffLoginScreen(
    state: StaffLoginUiState,
    widthSizeClass: WindowWidthSizeClass,
    onFormChange: (StaffLoginForm) -> Unit,
    onSubmit: () -> Unit,
    onChangeAccess: () -> Unit,
) {
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.validationAttempt) {
        if (!state.submitting) when {
            StaffLoginField.Email in state.fieldErrors -> emailFocus.requestFocus()
            StaffLoginField.Password in state.fieldErrors -> passwordFocus.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = if (widthSizeClass == WindowWidthSizeClass.Compact) 600.dp else 720.dp)
                .fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.staff_login_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff_login_intro))
            OutlinedTextField(
                value = state.form.email,
                onValueChange = { onFormChange(state.form.copy(email = it)) },
                label = { Text(stringResource(R.string.staff_email)) },
                modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                enabled = !state.submitting,
                singleLine = true,
                isError = StaffLoginField.Email in state.fieldErrors,
                supportingText = state.fieldErrors[StaffLoginField.Email]?.let { message -> { Text(message.text()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            OutlinedTextField(
                value = state.form.password,
                onValueChange = { onFormChange(state.form.copy(password = it)) },
                label = { Text(stringResource(R.string.field_password)) },
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                enabled = !state.submitting,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = StaffLoginField.Password in state.fieldErrors,
                supportingText = state.fieldErrors[StaffLoginField.Password]?.let { message -> { Text(message.text()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); if (state.canSubmit) onSubmit() }),
            )
            state.message?.let {
                Text(it.text(), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (state.submitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.login_submit))
            }
            TextButton(onClick = onChangeAccess) { Text(stringResource(R.string.change_access)) }
        }
    }
}
