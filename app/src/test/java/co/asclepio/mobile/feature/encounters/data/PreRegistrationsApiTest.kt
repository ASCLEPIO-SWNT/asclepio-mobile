package co.asclepio.mobile.feature.encounters.data

import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
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
class PreRegistrationsApiTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val timeFields = listOf("arrivalAt", "preRegisteredAt", "expiresAt", "resolvedAt", "occurredAt", "createdAt", "updatedAt")

    @Test fun `R10 el POST lleva exactamente siteId, chiefComplaintText y complaintSource TYPED`() = runTest {
        env.login()
        env.enqueue(201, PatientTestEnv.preRegistration())
        env.preRegistrationsApi.create(CreatePreRegistrationRequest(PatientTestEnv.SITE_SUR, "Dolor de cabeza", ComplaintSource.TYPED))
        val recorded = env.server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/patient/pre-registrations", recorded.path)
        assertEquals("Bearer ${PatientTestEnv.TOKEN}", recorded.getHeader("Authorization"))
        assertEquals(
            mapOf(
                "siteId" to JsonPrimitive(PatientTestEnv.SITE_SUR),
                "chiefComplaintText" to JsonPrimitive("Dolor de cabeza"),
                "complaintSource" to JsonPrimitive("TYPED"),
            ),
            recorded.jsonBody(),
        )
    }

    @Test fun `R22 el cuerpo del POST no tiene campos de hora, patientId ni tenantId`() = runTest {
        env.login()
        env.enqueue(201, PatientTestEnv.preRegistration())
        env.preRegistrationsApi.create(CreatePreRegistrationRequest(PatientTestEnv.SITE_NORTE, "Fiebre", ComplaintSource.TYPED))
        val keys = env.server.takeRequest().jsonBody().keys
        for (field in timeFields + listOf("patientId", "tenantId")) assertFalse(field, field in keys)
    }

    @Test fun `R21 un level y un arrivalAt añadidos a la respuesta se ignoran al deserializar`() = runTest {
        env.login()
        val extra = ""","level":"ESI_2","arrivalAt":"2026-10-27T14:45:00.000Z","chiefComplaintText":"Fiebre""""
        env.enqueue(200, "[" + PatientTestEnv.preRegistration("CONFIRMED", "TRIAGED", extra = extra) + "]")
        val list = env.preRegistrationsApi.list()
        assertEquals(
            listOf(
                PatientPreRegistrationDto(
                    id = "66666666-6666-6666-6666-666666666666",
                    siteId = PatientTestEnv.SITE_NORTE,
                    siteName = "Sede Norte",
                    status = "CONFIRMED",
                    preRegisteredAt = "2026-10-27T14:30:00.000Z",
                    expiresAt = "2026-10-27T20:30:00.000Z",
                    encounter = EncounterRefDto("55555555-5555-5555-5555-555555555555", "TRIAGED"),
                ),
            ),
            list,
        )
        val text = list.toString()
        assertFalse(text.contains("ESI_2"))
        assertFalse(text.contains("14:45"))
        assertEquals("/api/v1/patient/pre-registrations", env.server.takeRequest().path)
    }

    @Test fun `R17 un status o encounter status desconocidos no rompen la deserializacion`() = runTest {
        env.login()
        env.enqueue(200, "[" + PatientTestEnv.preRegistration("NUEVO_ESTADO", "OTRO") + "]")
        val dto = env.preRegistrationsApi.list().single()
        assertEquals("NUEVO_ESTADO", dto.status)
        assertEquals("OTRO", dto.encounter?.status)
    }

    @Test fun `R27 el toString del cuerpo del POST enmascara el motivo`() {
        val text = CreatePreRegistrationRequest("s", "Dolor toracico intenso", ComplaintSource.TYPED).toString()
        assertFalse(text.contains("Dolor"))
        assertTrue(text.contains("chiefComplaintText=***"))
    }
}
