package co.asclepio.mobile.core.ui

enum class UiTheme { LIGHT, DARK, SYSTEM }
enum class UiColorMode { NORMAL, HIGH_CONTRAST, SEPIA, GRAYSCALE, ALTERNATIVE }

/** Visual preferences only: deliberately cannot represent a principal or private draft. */
data class UiPreferences(
    val theme: UiTheme = UiTheme.LIGHT,
    val colorMode: UiColorMode = UiColorMode.NORMAL,
    val textScale: Float = 1f,
    val dyslexiaFont: Boolean = false,
) {
    fun validated(): UiPreferences = if (textScale in TEXT_SCALES) this else copy(textScale = 1f)
    companion object { val TEXT_SCALES = listOf(.875f, 1f, 1.25f, 1.5f, 2f) }
}
