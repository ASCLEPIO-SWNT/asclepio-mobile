package co.asclepio.mobile.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.asclepio.mobile.R

@Composable
fun PreferencesPanel(
    preferences: UiPreferences,
    locale: String,
    onChange: ((UiPreferences) -> UiPreferences) -> Unit,
    onLocaleChange: (String) -> Unit,
    onClose: () -> Unit,
    onGuide: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageHeader(stringResource(R.string.preferences_title), stringResource(R.string.preferences_description))
                TextButton(onClick = onClose, modifier = Modifier.testTag("preferences-close")) { Text(stringResource(R.string.close)) }
                OutlinedButton(onClick = onGuide) { Text(stringResource(R.string.guide_title)) }
                PreferenceGroup(stringResource(R.string.preferences_theme)) {
                    listOf(UiTheme.LIGHT to R.string.theme_light, UiTheme.DARK to R.string.theme_dark, UiTheme.SYSTEM to R.string.theme_system).forEach { (value, label) ->
                        PreferenceOption(stringResource(label), value == preferences.theme, "preference-theme-$value") { onChange { it.copy(theme = value) } }
                    }
                }
                PreferenceGroup(stringResource(R.string.preferences_color_mode)) {
                    listOf(UiColorMode.NORMAL to R.string.color_normal, UiColorMode.HIGH_CONTRAST to R.string.color_high_contrast, UiColorMode.SEPIA to R.string.color_sepia, UiColorMode.GRAYSCALE to R.string.color_grayscale, UiColorMode.ALTERNATIVE to R.string.color_alternative).forEach { (value, label) ->
                        PreferenceOption(stringResource(label), value == preferences.colorMode, "preference-color-$value") { onChange { it.copy(colorMode = value) } }
                    }
                }
                PreferenceGroup(stringResource(R.string.preferences_text_scale)) {
                    val labels = listOf(R.string.text_scale_875, R.string.text_scale_100, R.string.text_scale_125, R.string.text_scale_150, R.string.text_scale_200)
                    UiPreferences.TEXT_SCALES.forEachIndexed { index, scale ->
                        PreferenceOption(stringResource(labels[index]), scale == preferences.textScale, "preference-scale-$scale") { onChange { it.copy(textScale = scale) } }
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("preference-dyslexia").toggleable(value = preferences.dyslexiaFont, role = Role.Switch, onValueChange = { checked -> onChange { it.copy(dyslexiaFont = checked) } }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = preferences.dyslexiaFont, onCheckedChange = null)
                    Text(stringResource(R.string.preferences_dyslexia_font), Modifier.weight(1f))
                }
                Text(stringResource(R.string.preferences_font_description), style = MaterialTheme.typography.bodyMedium)
                PreferenceGroup(stringResource(R.string.preferences_language)) {
                    listOf("es" to R.string.language_es, "en" to R.string.language_en, "pt" to R.string.language_pt, "fr" to R.string.language_fr, "de" to R.string.language_de).forEach { (value, label) ->
                        PreferenceOption(stringResource(label), value == locale, "preference-locale-$value") { onLocaleChange(value) }
                    }
                }
                AsclepioButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
private fun PreferenceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun PreferenceOption(label: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).selectable(selected, role = Role.RadioButton, onClick = onSelect), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.weight(1f))
    }
}
