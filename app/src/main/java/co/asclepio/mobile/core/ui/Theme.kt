package co.asclepio.mobile.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R

val Axiforma = FontFamily(
    Font(R.font.axiforma_regular, FontWeight.Normal),
    Font(R.font.axiforma_semibold, FontWeight.SemiBold),
    Font(R.font.axiforma_bold, FontWeight.Bold),
)
val OpenDyslexic = FontFamily(Font(R.font.opendyslexic_regular))
val LocalUiPalette = staticCompositionLocalOf { uiPalette(UiColorMode.NORMAL, false) }
val LocalUiPreferences = staticCompositionLocalOf { UiPreferences() }

fun UiPreferences.isDark(systemDark: Boolean): Boolean = when (theme) {
    UiTheme.LIGHT -> false
    UiTheme.DARK -> true
    UiTheme.SYSTEM -> systemDark
}

@Composable
fun AsclepioTheme(preferences: UiPreferences = UiPreferences(), content: @Composable () -> Unit) {
    val settings = preferences.validated()
    val palette = uiPalette(settings.colorMode, settings.isDark(isSystemInDarkTheme()))
    val defaults = if (settings.isDark(isSystemInDarkTheme())) darkColorScheme() else lightColorScheme()
    val colors = defaults.copy(
        primary = Color(palette.accent), onPrimary = Color(palette.onAccent),
        primaryContainer = Color(palette.accentSoft), onPrimaryContainer = Color(palette.foreground),
        secondary = Color(palette.accent), onSecondary = Color(palette.onAccent),
        secondaryContainer = Color(palette.accentSoft), onSecondaryContainer = Color(palette.foreground),
        tertiary = Color(palette.accent), onTertiary = Color(palette.onAccent),
        tertiaryContainer = Color(palette.accentSoft), onTertiaryContainer = Color(palette.foreground),
        background = Color(palette.background), onBackground = Color(palette.foreground),
        surface = Color(palette.card), onSurface = Color(palette.foreground),
        surfaceVariant = Color(palette.accentSoft), onSurfaceVariant = Color(palette.muted),
        surfaceDim = Color(palette.background), surfaceBright = Color(palette.card),
        surfaceContainerLowest = Color(palette.card), surfaceContainerLow = Color(palette.card),
        surfaceContainer = Color(palette.card), surfaceContainerHigh = Color(palette.accentSoft),
        surfaceContainerHighest = Color(palette.accentSoft), surfaceTint = Color(palette.accent),
        error = Color(palette.error), onError = Color(palette.errorBackground),
        errorContainer = Color(palette.errorBackground), onErrorContainer = Color(palette.error),
        outline = Color(palette.muted), outlineVariant = Color(palette.border),
        inverseSurface = Color(palette.foreground), inverseOnSurface = Color(palette.card),
        inversePrimary = Color(palette.onAccent), scrim = Color.Black,
    )
    val typography = Typography()
    val family = if (settings.dyslexiaFont) OpenDyslexic else Axiforma
    // Typography uses sp, retaining the device fontScale in addition to this user multiplier.
    fun TextStyle.scaled() = copy(fontFamily = family, fontSize = fontSize * settings.textScale, lineHeight = lineHeight * settings.textScale)
    CompositionLocalProvider(LocalUiPalette provides palette, LocalUiPreferences provides settings) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(
                displayLarge = typography.displayLarge.scaled(), displayMedium = typography.displayMedium.scaled(), displaySmall = typography.displaySmall.scaled(),
                headlineLarge = typography.headlineLarge.scaled(), headlineMedium = typography.headlineMedium.scaled(), headlineSmall = typography.headlineSmall.scaled(),
                titleLarge = typography.titleLarge.scaled(), titleMedium = typography.titleMedium.scaled(), titleSmall = typography.titleSmall.scaled(),
                bodyLarge = typography.bodyLarge.scaled(), bodyMedium = typography.bodyMedium.scaled(), bodySmall = typography.bodySmall.scaled(),
                labelLarge = typography.labelLarge.scaled(), labelMedium = typography.labelMedium.scaled(), labelSmall = typography.labelSmall.scaled(),
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
