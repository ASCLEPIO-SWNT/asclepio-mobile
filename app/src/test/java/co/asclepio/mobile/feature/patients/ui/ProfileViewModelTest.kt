package co.asclepio.mobile.feature.patients.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private suspend fun TestScope.opened(profile: String = PatientTestEnv.profile()): ProfileViewModel {
        env.login()
        env.enqueue(200, profile)
        val vm = ProfileViewModel(env.profileRepository, env.clock)
        vm.state.first { it !is ProfileUiState.Loading }
        assertEquals("/api/v1/patient/profile", env.server.takeRequest().path)
        return vm
    }

    private fun ProfileViewModel.success() = state.value as ProfileUiState.Success

    private suspend fun ProfileViewModel.settled() = state.first { it is ProfileUiState.Success && !it.saving } as ProfileUiState.Success

    @Test fun `R1 al abrir Mis datos pide el perfil y muestra documento no editable, nombre, fecha y sexo`() = runTest {
        val s = opened().success()
        assertEquals(
            ProfileDisplay(
                DisplayText.Res(R.string.document_type_cc),
                DisplayText.Raw("DEV-1020304050"),
                DisplayText.Raw("DEV Paciente Prueba"),
                DisplayText.Raw("17/05/1990"),
                DisplayText.Res(R.string.sex_f),
            ),
            s.display,
        )
        assertEquals(ProfileForm("DEV Paciente Prueba", LocalDate.parse("1990-05-17"), Sex.F), s.form)
        assertTrue(s.editable)
        assertFalse(s.showLockedNotice)
    }

    @Test fun `R1 los campos nulos se muestran con guion`() = runTest {
        val s = opened(PatientTestEnv.profile(documentType = "RC", fullName = null, birthDate = null, sex = null)).success()
        val missing = DisplayText.Res(R.string.value_missing)
        assertEquals(DisplayText.Res(R.string.document_type_rc), s.display.documentType)
        assertEquals(listOf(missing, missing, missing), listOf(s.display.fullName, s.display.birthDate, s.display.sex))
    }

    @Test fun `R2 con editable false no hay edicion ni Guardar y se muestra el aviso`() = runTest {
        val vm = opened(PatientTestEnv.profile(editable = false))
        val s = vm.success()
        assertFalse(s.editable)
        assertTrue(s.showLockedNotice)
        assertFalse(s.canSave)
        vm.updateForm(s.form.copy(fullName = "Otro"))
        assertEquals(s, vm.success())
        vm.save()
        runCurrent()
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R3 Guardar envia PATCH solo con el nombre cambiado y muestra el perfil de la respuesta y Datos guardados`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "  DEV Nombre Nuevo "))
        assertTrue(vm.success().canSave)
        env.enqueue(200, PatientTestEnv.profile(fullName = "DEV Nombre Nuevo"))
        vm.save()
        val s = vm.settled()
        val recorded = env.server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("/api/v1/patient/profile", recorded.path)
        assertEquals(mapOf("fullName" to JsonPrimitive("DEV Nombre Nuevo")), recorded.jsonBody())
        assertEquals(DisplayText.Raw("DEV Nombre Nuevo"), s.display.fullName)
        assertEquals("DEV Nombre Nuevo", s.form.fullName)
        assertEquals(UiMessage(R.string.profile_saved), s.message)
        assertFalse(s.canSave)
    }

    @Test fun `R3 con dos y tres campos cambiados el PATCH lleva exactamente esos campos`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(birthDate = LocalDate.parse("1991-02-03"), sex = Sex.X))
        env.enqueue(200, PatientTestEnv.profile(birthDate = "1991-02-03", sex = "X"))
        vm.save()
        vm.settled()
        assertEquals(
            mapOf("birthDate" to JsonPrimitive("1991-02-03"), "sex" to JsonPrimitive("X")),
            env.server.takeRequest().jsonBody(),
        )
        vm.updateForm(ProfileForm("DEV Otro", LocalDate.parse("1992-04-05"), Sex.M))
        env.enqueue(200, PatientTestEnv.profile())
        vm.save()
        vm.settled()
        val body = env.server.takeRequest().jsonBody()
        assertEquals(setOf("fullName", "birthDate", "sex"), body.keys)
        for (k in listOf("documentType", "documentNumber", "patientId", "tenantId", "id", "editable")) assertFalse(k, k in body.keys)
    }

    @Test fun `R4 sin cambios Guardar esta deshabilitado y no envia nada`() = runTest {
        val vm = opened()
        assertFalse(vm.success().canSave)
        vm.updateForm(vm.success().form.copy(fullName = " DEV Paciente Prueba "))
        assertFalse(vm.success().canSave)
        vm.save()
        runCurrent()
        assertEquals(1, env.server.requestCount)
        vm.updateForm(vm.success().form.copy(sex = Sex.M))
        assertTrue(vm.success().canSave)
        vm.updateForm(vm.success().form.copy(sex = Sex.F))
        assertFalse(vm.success().canSave)
    }

    @Test fun `R5 nombre vacio o menor de edad marca los campos y no envia la peticion`() = runTest {
        val vm = opened()
        vm.updateForm(ProfileForm("   ", LocalDate.parse("2008-10-28"), Sex.F))
        vm.save()
        runCurrent()
        assertEquals(
            mapOf(ProfileField.FullName to UiMessage(R.string.error_required), ProfileField.BirthDate to UiMessage(R.string.error_underage_profile)),
            vm.success().fieldErrors,
        )
        assertEquals(1, env.server.requestCount)
        vm.updateForm(vm.success().form.copy(fullName = "a".repeat(201)))
        assertEquals(setOf(ProfileField.BirthDate), vm.success().fieldErrors.keys)
        vm.save()
        runCurrent()
        assertEquals(UiMessage(R.string.error_max_length, listOf(200)), vm.success().fieldErrors[ProfileField.FullName])
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R6 un 409 muestra el aviso de bloqueo y vuelve a pedir el perfil`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "DEV Otro"))
        env.enqueue(409, env.problem(409))
        env.enqueue(200, PatientTestEnv.profile(editable = false, fullName = "DEV Verificado"))
        vm.save()
        val s = vm.state.first { it is ProfileUiState.Success && it.display.fullName == DisplayText.Raw("DEV Verificado") }
            as ProfileUiState.Success
        assertEquals("PATCH", env.server.takeRequest().method)
        val reload = env.server.takeRequest()
        assertEquals("GET", reload.method)
        assertEquals("/api/v1/patient/profile", reload.path)
        assertTrue(s.showLockedNotice)
        assertFalse(s.editable)
        assertFalse(s.canSave)
        assertEquals("DEV Verificado", s.form.fullName)
    }

    @Test fun `R6 tras un 409 el aviso se mantiene aunque el perfil recibido siga marcado editable`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "DEV Otro"))
        env.enqueue(409, env.problem(409))
        env.enqueue(200, PatientTestEnv.profile(editable = true))
        vm.save()
        vm.state.first { env.server.requestCount == 3 && it is ProfileUiState.Success && !it.saving && !it.reloading }
        assertTrue(vm.success().showLockedNotice)
        assertFalse(vm.success().editable)
    }

    @Test fun `R6 tras un 409 si la recarga falla, Reintentar conserva el aviso de bloqueo`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "DEV Otro"))
        env.enqueue(409, env.problem(409))
        env.enqueue(503, env.problem(503))
        vm.save()
        assertEquals(
            ProfileUiState.Error(UiMessage(R.string.error_general), retryEnabled = true),
            vm.state.first { it is ProfileUiState.Error },
        )
        env.enqueue(200, PatientTestEnv.profile(editable = true))
        vm.retry()
        val s = vm.state.first { it is ProfileUiState.Success } as ProfileUiState.Success
        assertEquals(4, env.server.requestCount)
        assertTrue(s.showLockedNotice)
        assertFalse(s.editable)
        assertFalse(s.canSave)
    }

    @Test fun `R7 un 422 marca la fecha de nacimiento y conserva lo escrito`() = runTest {
        val vm = opened()
        val form = vm.success().form.copy(fullName = "DEV Otro", birthDate = LocalDate.parse("2000-01-01"))
        vm.updateForm(form)
        env.enqueue(422, env.problem(422))
        vm.save()
        val s = vm.settled()
        assertEquals(mapOf(ProfileField.BirthDate to UiMessage(R.string.error_underage_profile)), s.fieldErrors)
        assertEquals(form, s.form)
        assertNull(s.message)
    }

    @Test fun `R7 un 400 marca los campos de errors o muestra el error general, conservando lo escrito`() = runTest {
        val vm = opened()
        val form = vm.success().form.copy(fullName = "DEV Otro")
        vm.updateForm(form)
        env.enqueue(400, env.problem(400, "fullName", "documentNumber"))
        vm.save()
        var s = vm.settled()
        assertEquals(mapOf(ProfileField.FullName to UiMessage(R.string.error_max_length, listOf(200))), s.fieldErrors)
        assertEquals(form, s.form)
        env.enqueue(400, env.problem(400))
        vm.save()
        s = vm.settled()
        assertEquals(UiMessage(R.string.error_general), s.message)
        assertEquals(form, s.form)
    }

    @Test fun `R24 un 429 en Guardar deshabilita el boton durante Retry-After`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "DEV Otro"))
        env.enqueue(429, env.problem(429), retryAfter = "30")
        vm.save()
        val s = vm.settled()
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(30)), s.message)
        assertFalse(s.canSave)
        vm.save()
        advanceTimeBy(29_000)
        runCurrent()
        assertFalse(vm.success().canSave)
        assertEquals(2, env.server.requestCount)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(vm.success().canSave)
    }

    @Test fun `R24 un 429 sin Retry-After bloquea 60 s y 403, 5xx y sin red dan sus mensajes sin textos de la API`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "DEV Otro"))
        env.enqueue(429, env.problem(429), retryAfter = "abc")
        vm.save()
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(60)), vm.settled().message)
        advanceTimeBy(60_001)
        runCurrent()
        for (code in listOf(403, 404, 500)) {
            env.enqueue(code, env.problem(code))
            vm.save()
            assertEquals(UiMessage(R.string.error_general), vm.settled().message)
        }
        env.server.shutdown() // sin respuesta HTTP
        vm.save()
        assertEquals(UiMessage(R.string.error_network), vm.settled().message)
        assertFalse(vm.state.value.toString().contains("de la API"))
    }

    @Test fun `R24 si el perfil no se puede cargar muestra el error con reintentar`() = runTest {
        env.login()
        env.enqueueDisconnect()
        val vm = ProfileViewModel(env.profileRepository, env.clock)
        assertEquals(ProfileUiState.Error(UiMessage(R.string.error_network), retryEnabled = true), vm.state.first { it !is ProfileUiState.Loading })
        env.enqueue(200, PatientTestEnv.profile())
        vm.retry()
        assertTrue(vm.state.first { it !is ProfileUiState.Loading } is ProfileUiState.Success)
    }

    @Test fun `R23 un 401 al cargar el perfil borra la sesion`() = runTest {
        env.login()
        env.enqueue(401, env.problem(401))
        val vm = ProfileViewModel(env.profileRepository, env.clock)
        vm.state.first { it !is ProfileUiState.Loading }
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
    }

    @Test fun `R25 mientras el PATCH esta en curso muestra carga y una segunda pulsacion no envia otra peticion`() = runTest {
        val vm = opened()
        vm.updateForm(vm.success().form.copy(fullName = "DEV Otro"))
        env.enqueue(200, PatientTestEnv.profile(fullName = "DEV Otro"))
        vm.save()
        assertTrue(vm.success().saving)
        assertFalse(vm.success().canSave)
        vm.save()
        vm.updateForm(vm.success().form.copy(fullName = "Cambiado durante el envio"))
        assertEquals("DEV Otro", vm.success().form.fullName)
        vm.settled()
        assertEquals(2, env.server.requestCount)
    }

    @Test fun `R27 el estado de Mis datos enmascara nombre, documento y fecha en toString`() = runTest {
        val text = opened().state.value.toString()
        assertFalse(text, text.contains("DEV Paciente"))
        assertFalse(text, text.contains("DEV-1020304050"))
        assertFalse(text, text.contains("1990"))
    }
    @Test fun `R6 modal date draft stays in VM across form updates and cancel never accepts it`() = runTest {
        val vm = opened()
        val original = vm.success().form.birthDate
        val draft = co.asclepio.mobile.core.ui.PrivateDatePickerDraft.open(original).copy(inputMode = true).withInput("03/0")
        vm.updateDatePicker(draft)
        vm.updateForm(vm.success().form.copy(fullName = "Otro nombre sintético"))
        assertEquals(draft, vm.success().datePicker)
        assertEquals(original, vm.success().form.birthDate)
        vm.updateDatePicker(co.asclepio.mobile.core.ui.PrivateDatePickerDraft())
        assertFalse(vm.success().datePicker.isOpen)
        assertEquals(original, vm.success().form.birthDate)
        assertEquals(1, env.server.requestCount)
    }
}
