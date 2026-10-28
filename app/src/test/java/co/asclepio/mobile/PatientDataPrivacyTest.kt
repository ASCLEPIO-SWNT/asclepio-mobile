package co.asclepio.mobile

import co.asclepio.mobile.feature.encounters.ui.PreRegistrationViewModel
import co.asclepio.mobile.feature.patients.ui.ProfileUiState
import co.asclepio.mobile.feature.patients.ui.ProfileViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** El motivo y el perfil solo viven en memoria (R27, D13; RNF-05). */
class PatientDataPrivacyTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    @Test fun `R27 patients y encounters no usan SavedStateHandle, rememberSaveable, DataStore ni SharedPreferences`() {
        val sources = listOf("feature/patients", "feature/encounters").flatMap {
            File("src/main/java/co/asclepio/mobile/$it").walkTopDown().filter { f -> f.extension == "kt" }.toList()
        }
        assertTrue(sources.size >= 8)
        val pattern = Regex("""SavedStateHandle|savedStateHandle|rememberSaveable|DataStore|dataStore|SharedPreferences|openFileOutput""")
        val found = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (pattern.containsMatchIn(l)) "${f.name}:${i + 1}" else null }
        }
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test fun `R27 tras cargar el perfil y enviar un pre-registro ningun archivo contiene el motivo ni los datos del perfil`() = runTest {
        env.login()
        env.enqueue(200, PatientTestEnv.profile())
        val profile = ProfileViewModel(env.profileRepository, env.clock)
        profile.state.first { it is ProfileUiState.Success }
        env.enqueue(200, PatientTestEnv.tenants())
        val form = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        form.state.first { it.sites is co.asclepio.mobile.feature.encounters.ui.SitesUiState.Loaded }
        form.updateComplaint("Dolor abdominal secreto")
        env.enqueue(201, PatientTestEnv.preRegistration())
        form.submit()
        form.state.first { it.created }
        val files = tmp.root.walkTopDown().filter(File::isFile).toList()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val raw = f.readBytes().toString(Charsets.ISO_8859_1)
            for (secret in listOf("Dolor abdominal secreto", "DEV-1020304050", "DEV Paciente Prueba", "1990-05-17")) {
                assertFalse("${f.name}: $secret", raw.contains(secret))
            }
        }
    }
}
