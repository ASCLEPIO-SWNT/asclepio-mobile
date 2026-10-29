package co.asclepio.mobile.core.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import co.asclepio.mobile.core.auth.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiPreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `R10 defaults and invalid preferences are safe and storage failure retains RAM choices`() = runTest {
        val failed = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("synthetic unavailable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("synthetic unavailable")
        }
        val repository = UiPreferencesRepository(failed, backgroundScope)
        runCurrent()
        assertEquals(UiPreferences(), repository.preferences.value)
        repository.update { it.copy(theme = UiTheme.DARK, colorMode = UiColorMode.SEPIA, textScale = 99f, dyslexiaFont = true) }
        assertEquals(UiPreferences(UiTheme.DARK, UiColorMode.SEPIA, 1f, true), repository.preferences.value)
        repository.update { it.copy(textScale = 1.5f) }
        assertEquals(1.5f, repository.preferences.value.textScale)
    }

    @Test fun `R10 visual preferences persist separately and survive clearing authentication`() = runTest {
        val ui = TestDataStore(tmp.root.resolve("asclepio_ui.preferences_pb"))
        val auth = TestDataStore(tmp.root.resolve("asclepio_session.preferences_pb"))
        try {
            val repository = UiPreferencesRepository(ui.dataStore, backgroundScope)
            val session = SessionStore(auth.dataStore, FakeTokenCipher())
            session.saveSession("synthetic-secret", Instant.parse("2026-10-30T00:00:00Z"))
            repository.update { UiPreferences(UiTheme.SYSTEM, UiColorMode.ALTERNATIVE, 2f, true) }
            session.clearSession()
            assertEquals(StoredSession.None, session.session())
            val raw = ui.dataStore.data.first()
            assertEquals(setOf("theme", "color_mode", "text_scale", "dyslexia_font"), raw.asMap().keys.map { it.name }.toSet())
            assertEquals(repository.preferences.value, decodeUiPreferences(raw))
            assertFalse(raw.toString().contains("synthetic-secret"))
        } finally { ui.close(); auth.close() }
    }

    @Test fun `R7 system theme follows device while explicit theme remains stable`() {
        assertFalse(UiPreferences().isDark(true))
        assertTrue(UiPreferences(theme = UiTheme.DARK).isDark(false))
        assertTrue(UiPreferences(theme = UiTheme.SYSTEM).isDark(true))
        assertFalse(UiPreferences(theme = UiTheme.SYSTEM).isDark(false))
    }

    @Test fun `R8 all color modes round trip as typed values and invalid stored values use defaults`() {
        UiColorMode.entries.forEach { mode -> assertEquals(mode, decodeUiPreferences(preferencesOf(stringPreferencesKey("color_mode") to mode.name)).colorMode) }
        assertEquals(UiPreferences(), decodeUiPreferences(preferencesOf(stringPreferencesKey("theme") to "broken", stringPreferencesKey("color_mode") to "bad", floatPreferencesKey("text_scale") to Float.NaN)))
        assertEquals(UiPreferences(), decodeUiPreferences(preferencesOf(intPreferencesKey("theme") to 42, stringPreferencesKey("text_scale") to "bad")))
    }

    @Test fun `R9 only approved text scales are accepted and accessible font is independent`() {
        UiPreferences.TEXT_SCALES.forEach { assertEquals(it, UiPreferences(textScale = it, dyslexiaFont = true).validated().textScale) }
        assertEquals(1f, UiPreferences(textScale = -1f).validated().textScale)
        assertTrue(UiPreferences(dyslexiaFont = true).validated().dyslexiaFont)
    }

    @Test fun `R20 preferences schema cannot contain private data or locale duplicate`() {
        assertEquals(setOf("theme", "colorMode", "textScale", "dyslexiaFont"), UiPreferences::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
    }
}
