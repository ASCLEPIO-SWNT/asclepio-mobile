package co.asclepio.mobile.feature.interview

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.OutputValidator
import co.asclepio.mobile.core.interview.Validation
import co.asclepio.mobile.feature.interview.model.BUILD_MODEL
import co.asclepio.mobile.feature.interview.model.ModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo MANUAL con el modelo real (T8, riesgo "ResponseFormat reciente" del design): una generación con el
 * esquema. Se omite si el modelo verificado no está en `filesDir/models/` (descárgalo antes desde Perfil).
 */
@RunWith(AndroidJUnit4::class)
class LiteRtSmokeInstrumentedTest {
    @Test fun `R9 una generacion local con ResponseFormat json devuelve JSON que pasa el esquema`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ModelStore(context.filesDir, BUILD_MODEL)
        assumeTrue("Modelo no descargado", store.isVerified())
        val prompt = stripPromptComment(context.assets.open(INTERVIEW_PROMPT_ASSET).bufferedReader().readText())
        val interviewer = LiteRtInterviewer(store.modelFile.path, context.cacheDir.path, prompt)
        try {
            interviewer.initialize()
            val step = interviewer.next("Me duele la cabeza hace días", emptyList()) as NextStep.ModelOutput
            val checked = OutputValidator.validate(step.raw, emptyList())
            // El esquema debe cumplirse siempre; el texto puede caer en el validador (se reporta en la evaluación).
            assertTrue(checked is Validation.Valid || (checked as Validation.Invalid).redFlagSuspected != null)
        } finally {
            interviewer.close()
        }
    }
}
