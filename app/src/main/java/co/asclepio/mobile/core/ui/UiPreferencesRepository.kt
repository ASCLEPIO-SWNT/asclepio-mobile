package co.asclepio.mobile.core.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val THEME = stringPreferencesKey("theme")
private val COLOR_MODE = stringPreferencesKey("color_mode")
private val TEXT_SCALE = floatPreferencesKey("text_scale")
private val DYSLEXIA_FONT = booleanPreferencesKey("dyslexia_font")

fun decodeUiPreferences(data: Preferences): UiPreferences = UiPreferences(
    theme = UiTheme.entries.find { it.name == (data.asMap()[THEME] as? String) } ?: UiTheme.LIGHT,
    colorMode = UiColorMode.entries.find { it.name == (data.asMap()[COLOR_MODE] as? String) } ?: UiColorMode.NORMAL,
    textScale = (data.asMap()[TEXT_SCALE] as? Float) ?: 1f,
    dyslexiaFont = (data.asMap()[DYSLEXIA_FONT] as? Boolean) ?: false,
).validated()

/** Singleton visual DataStore, independent from authentication. I/O failure keeps the chosen RAM state. */
class UiPreferencesRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val mutablePreferences = MutableStateFlow(UiPreferences())
    val preferences: StateFlow<UiPreferences> = mutablePreferences.asStateFlow()
    private val mutex = Mutex()
    private var userUpdated = false

    init {
        scope.launch {
            try {
                val stored = store.data.first()
                mutex.withLock { if (!userUpdated) mutablePreferences.value = decodeUiPreferences(stored) }
            } catch (_: IOException) { /* Default preferences remain available in memory. */ }
        }
    }

    suspend fun update(transform: (UiPreferences) -> UiPreferences) = mutex.withLock {
        val next = transform(mutablePreferences.value).validated()
        userUpdated = true
        mutablePreferences.value = next
        try {
            store.edit {
                // A strict schema: no session, locale or private data can enter this store.
                it.clear()
                it[THEME] = next.theme.name
                it[COLOR_MODE] = next.colorMode.name
                it[TEXT_SCALE] = next.textScale
                it[DYSLEXIA_FONT] = next.dyslexiaFont
            }
        } catch (_: IOException) { /* Use next in RAM even if disk cannot be written. */ }
    }
}
