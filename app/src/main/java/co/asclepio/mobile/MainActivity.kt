package co.asclepio.mobile

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.core.ui.guide.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"))
        }
        val container = (application as AsclepioApp).container
        setContent {
            val preferences by container.uiPreferencesRepository.preferences.collectAsStateWithLifecycle()
            val dark = preferences.isDark(isSystemInDarkTheme())
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val scope = rememberCoroutineScope()
            val guide = remember { createAndroidSpeechGuide(applicationContext) }
            val guideState by guide.state.collectAsStateWithLifecycle()
            DisposableEffect(guide) { onDispose { guide.release() } }
            var showGuide by remember { mutableStateOf(false) }
            // Only a public presentation boolean is saveable; fields remain in feature ViewModels.
            var showPreferences by rememberSaveable { mutableStateOf(false) }
            AsclepioTheme(preferences) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Brand(Modifier.weight(1f).testTag("app-brand"))
                            IconButton(onClick = { showGuide = true }, modifier = Modifier.testTag("open-guide")) {
                                Icon(painterResource(R.drawable.ic_voice), contentDescription = stringResource(R.string.guide_title))
                            }
                            IconButton(onClick = { showPreferences = true }, modifier = Modifier.testTag("open-preferences")) {
                                Icon(painterResource(R.drawable.ic_preferences), contentDescription = stringResource(R.string.preferences_title))
                            }
                        }
                        Box(Modifier.weight(1f)) { AppNavigation(container, calculateWindowSizeClass(this@MainActivity).widthSizeClass, guide, showPreferences) }
                    }
                }
                if (showPreferences) PreferencesPanel(
                    preferences = preferences,
                    locale = LocalConfiguration.current.locales[0].language,
                    onChange = { transform -> scope.launch { container.uiPreferencesRepository.update(transform) } },
                    onLocaleChange = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
                    onClose = { showPreferences = false },
                    onGuide = { showGuide = true },
                )
                if (showGuide) SpeechGuidePanel(guide, guideState, onClose = { showGuide = false })
            }
        }
    }
}
