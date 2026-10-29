package co.asclepio.mobile.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.ScreenColumn
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.ui.text

/** Inicio de sesión de la IPS recordada (R3, R17, R18). El contenido se limita a 600 dp (D16). */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onFormChange: (LoginForm) -> Unit,
    onSubmit: () -> Unit,
    onChangeTenant: () -> Unit,
    onGoToSignUp: () -> Unit,
    onOpenStatus: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val enabled = !state.submitting
    ScreenColumn(modifier) {
            PageHeader(stringResource(R.string.login_title))
            state.tenant?.let {
                Text(stringResource(R.string.tenant_and_site, it.tenantName, it.siteName), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onChangeTenant, enabled = enabled) { Text(stringResource(R.string.login_change_tenant)) }
            DocumentTypeSelector(form.documentType, state.fieldErrors[LoginField.DocumentType], enabled) {
                onFormChange(form.copy(documentType = it))
            }
            FormTextField(form.documentNumber, R.string.field_document_number, state.fieldErrors[LoginField.DocumentNumber], enabled) {
                onFormChange(form.copy(documentNumber = it))
            }
            FormTextField(form.password, R.string.field_password, state.fieldErrors[LoginField.Password], enabled, password = true) {
                onFormChange(form.copy(password = it))
            }
            state.message?.let { UiNotice(it.text(), NoticeTone.Error) }
            AsclepioButton(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (state.submitting) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.login_submit))
            }
            OutlinedButton(onClick = onGoToSignUp, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login_go_to_signup))
            }
            onOpenStatus?.let { TextButton(onClick = it) { Text(stringResource(R.string.login_status)) } }
            }
}
