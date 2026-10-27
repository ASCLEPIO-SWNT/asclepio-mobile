package co.asclepio.mobile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Recorre las fuentes de app/src/main (D14, RNF-05). */
class PrivacyTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.toList()

    private fun offenders(pattern: Regex) = sources.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, line -> if (pattern.containsMatchIn(line)) "${f.path}:${i + 1}" else null }
    }

    @Test fun `R33 el codigo de main no usa Log, println, printStackTrace ni HttpLoggingInterceptor`() {
        assertTrue(sources.size > 10)
        val pattern = Regex("""android\.util\.Log|(?<![A-Za-z0-9_])Log\.|println\(|printStackTrace\(|HttpLoggingInterceptor""")
        val found = offenders(pattern)
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test fun `R32 la contrasena no se guarda en SavedStateHandle ni en la IPS recordada`() {
        val found = offenders(Regex("SavedStateHandle|savedStateHandle"))
        assertTrue(found.toString(), found.isEmpty())
        val store = File("src/main/java/co/asclepio/mobile/core/auth/SessionStore.kt").readText()
        val keys = Regex("""PreferencesKey\("([a-z_]+)"\)""").findAll(store).map { it.groupValues[1] }.toSet()
        assertTrue(keys.toString(), keys == setOf("tenant_id", "tenant_name", "site_id", "site_name", "access_token", "expires_at"))
    }
}
