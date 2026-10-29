package co.asclepio.mobile.feature.patients.data

import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
import co.asclepio.mobile.core.model.Sex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// DTOs según el snapshot 0.4.0 del contrato; Retrofit real contra MockWebServer.
class PatientProfileApiTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    @Test fun `R1 el perfil se lee del contrato con valores nulos y un tipo de documento fuera del alta`() = runTest {
        env.login()
        env.enqueue(200, PatientTestEnv.profile(documentType = "TI", fullName = null, birthDate = null, sex = null))
        val dto = env.profileApi.profile("Bearer ${PatientTestEnv.TOKEN}")
        assertEquals(PatientProfileDto("44444444-4444-4444-4444-444444444444", "TI", "DEV-1020304050", null, null, null, true), dto)
        val recorded = env.server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/patient/profile", recorded.path)
        assertEquals("Bearer ${PatientTestEnv.TOKEN}", recorded.getHeader("Authorization"))
    }

    @Test fun `R3 el PATCH lleva solo los campos distintos de null`() = runTest {
        env.login()
        env.enqueue(200, PatientTestEnv.profile())
        env.profileApi.update(UpdatePatientProfileRequest(fullName = "DEV Nuevo"), "Bearer ${PatientTestEnv.TOKEN}")
        val recorded = env.server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("/api/v1/patient/profile", recorded.path)
        assertEquals(mapOf("fullName" to JsonPrimitive("DEV Nuevo")), recorded.jsonBody())

        env.enqueue(200, PatientTestEnv.profile())
        env.profileApi.update(UpdatePatientProfileRequest(fullName = "A", birthDate = "1991-01-02", sex = Sex.X), "Bearer ${PatientTestEnv.TOKEN}")
        assertEquals(
            mapOf("fullName" to JsonPrimitive("A"), "birthDate" to JsonPrimitive("1991-01-02"), "sex" to JsonPrimitive("X")),
            env.server.takeRequest().jsonBody(),
        )
    }

    @Test fun `R27 el toString del perfil y del PATCH enmascara documento, nombre y fecha de nacimiento`() {
        val dto = PatientProfileDto("p", "CC", "DEV-1020304050", "DEV Paciente Prueba", "1990-05-17", Sex.F, true)
        val request = UpdatePatientProfileRequest("DEV Paciente Prueba", "1990-05-17", Sex.F)
        for (text in listOf(dto.toString(), request.toString())) {
            assertFalse(text, text.contains("DEV-1020304050"))
            assertFalse(text, text.contains("DEV Paciente"))
            assertFalse(text, text.contains("1990-05-17"))
            assertTrue(text, text.contains("fullName=***"))
        }
    }
}
