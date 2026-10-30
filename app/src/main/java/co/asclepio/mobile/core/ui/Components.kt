package co.asclepio.mobile.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.asclepio_mark), contentDescription = null, modifier = Modifier.size(width = 27.dp, height = 40.dp), contentScale = ContentScale.Fit)
        val effectiveScale = androidx.compose.ui.platform.LocalDensity.current.fontScale * LocalUiPreferences.current.textScale
        Text(stringResource(R.string.app_name), style = if (effectiveScale > 1.4f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun PageHeader(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

enum class NoticeTone { Info, Success, Error, Warning }

@Composable
fun UiNotice(message: String, tone: NoticeTone = NoticeTone.Info, modifier: Modifier = Modifier) {
    val palette = LocalUiPalette.current
    val (background, foreground) = when (tone) {
        NoticeTone.Info -> palette.accentSoft to palette.foreground
        NoticeTone.Success -> palette.successBackground to palette.success
        NoticeTone.Error -> palette.errorBackground to palette.error
        NoticeTone.Warning -> palette.warningBackground to palette.warning
    }
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, color = Color(background), contentColor = Color(foreground), shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AsclepioButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val palette = LocalUiPalette.current
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(palette.action), contentColor = Color(palette.onAction)), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), content = content)
}

/** String-state fields retain the draft in the feature ViewModel. No local saved text state. */
@Composable
fun AsclepioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
        label = label, supportingText = supportingText, isError = isError, singleLine = singleLine,
        minLines = minLines, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(LocalUiPalette.current.focus)),
    )
}

@Composable
fun EntryBackdrop(register: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (LocalUiPreferences.current.colorMode == UiColorMode.NORMAL) {
            Image(painterResource(if (register) R.drawable.background_register else R.drawable.background_login), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .22f)
        }
        content()
    }
}

/** One scroll region, bounded reading width and card composition for the existing native screens. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, maxWidth: androidx.compose.ui.unit.Dp = 680.dp, scrollState: androidx.compose.foundation.ScrollState? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxWidth().verticalScroll(scrollState ?: androidx.compose.foundation.rememberScrollState()).padding(16.dp)) {
            Panel(Modifier.fillMaxWidth(), content)
        }
    }
}
