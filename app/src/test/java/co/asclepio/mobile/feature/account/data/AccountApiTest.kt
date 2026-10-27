package co.asclepio.mobile.feature.account.data

import co.asclepio.mobile.core.auth.MeApi
import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// DTOs según el snapshot 0.2.1 del contrato; Retrofit real contra MockWebServer.
class AccountApiTest {
    private lateinit var server: MockWebServer
    private lateinit var publicApi: PublicApi
    private lateinit var authApi: PatientAuthApi
    private lateinit var meApi: MeApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(server.url("/").toString())
        publicApi = retrofit.create(PublicApi::class.java)
        authApi = retrofit.create(PatientAuthApi::class.java)
        meApi = retrofit.create(MeApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private val request = CreatePatientAccountRequest(
        tenantId = "11111111-1111-1111-1111-111111111111",
        documentType = DocumentType.CC,
        documentNumber = "DEV-123",
        fullName = "DEV Paciente",
        birthDate = "1990-05-17",
        sex = Sex.X,
        password = "secreta-123",
        consentAccepted = true,
        consentVersion = "2026-10-v1",
    )

    @Test fun `R10 el alta envia exactamente los campos del contrato y nada mas`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"a","patientId":"p"}"""))
        assertEquals(PatientAccountCreatedDto("a", "p"), authApi.createAccount(request))
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/patient/accounts", recorded.path)
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(
            JsonObject(
                mapOf(
                    "tenantId" to JsonPrimitive("11111111-1111-1111-1111-111111111111"),
                    "documentType" to JsonPrimitive("CC"),
                    "documentNumber" to JsonPrimitive("DEV-123"),
                    "fullName" to JsonPrimitive("DEV Paciente"),
                    "birthDate" to JsonPrimitive("1990-05-17"),
                    "sex" to JsonPrimitive("X"),
                    "password" to JsonPrimitive("secreta-123"),
                    "consentAccepted" to JsonPrimitive(true),
                    "consentVersion" to JsonPrimitive("2026-10-v1"),
                ),
            ),
            body,
        )
    }

    @Test fun `R10 los enums viajan con los codigos del contrato`() {
        assertEquals(listOf("CC", "CE", "PA"), DocumentType.entries.map { Json.encodeToString(DocumentType.serializer(), it).trim('"') })
        assertEquals(listOf("M", "F", "X"), Sex.entries.map { Json.encodeToString(Sex.serializer(), it).trim('"') })
    }

    @Test fun `R17 el login envia tenantId, documentType, documentNumber y password`() = runTest {
        server.enqueue(MockResponse().setBody("""{"accessToken":"tok","tokenType":"Bearer","expiresAt":"2026-10-26T23:00:00.000Z"}"""))
        val token = authApi.login(PatientLoginRequest("t-1", DocumentType.PA, "AB123", "secreta-123"))
        assertEquals("tok", token.accessToken)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/patient/login", recorded.path)
        assertEquals(
            setOf("tenantId", "documentType", "documentNumber", "password"),
            Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject.keys,
        )
    }

    @Test fun `R1 la lista de IPS y sedes se lee del contrato`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"t1","name":"IPS Uno","sites":[{"id":"s1","name":"Sede A"}],"extra":1},{"id":"t2","name":"IPS Dos","sites":[]}]""",
            ),
        )
        assertEquals(
            listOf(PublicTenantDto("t1", "IPS Uno", listOf(PublicSiteDto("s1", "Sede A"))), PublicTenantDto("t2", "IPS Dos", emptyList())),
            publicApi.tenants(),
        )
        assertEquals("/api/v1/public/tenants", server.takeRequest().path)
    }

    @Test fun `R20 me de personal se deserializa sin guardar email ni nombre y no es paciente`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"kind":"STAFF","id":"u1","tenantId":"t1","email":"a@b.co","fullName":"Nombre","role":"ADMIN"}""",
            ),
        )
        val me = meApi.me()
        assertFalse(me.isPatient)
        assertFalse(me.toString().contains("a@b.co"))
        assertFalse(me.toString().contains("Nombre"))
    }

    @Test fun `R32 toString de los DTO con contrasena la muestra enmascarada`() {
        val text = request.toString()
        assertFalse(text.contains("secreta-123"))
        assertTrue(text.contains("password=***"))
        val login = PatientLoginRequest("t-1", DocumentType.CC, "123", "otra-secreta-1")
        assertFalse(login.toString().contains("otra-secreta-1"))
        assertTrue(login.toString().contains("password=***"))
        val token = AccessTokenDto("eyJ.secreto.jwt", "Bearer", "2026-10-26T23:00:00.000Z")
        assertFalse(token.toString().contains("eyJ.secreto.jwt"))
    }
}
