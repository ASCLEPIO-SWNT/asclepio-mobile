package co.asclepio.mobile

import co.asclepio.mobile.core.tenants.TenantsApi
import co.asclepio.mobile.feature.encounters.data.CreatePreRegistrationRequest
import co.asclepio.mobile.feature.encounters.data.PreRegistrationsApi
import co.asclepio.mobile.feature.patients.data.PatientProfileApi
import co.asclepio.mobile.feature.patients.data.UpdatePatientProfileRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url
import java.io.File
import java.lang.reflect.Method

/** Las features del paciente solo usan rutas propias del paciente o públicas (R26), por reflexión sobre Retrofit. */
class ApiScopeTest {
    private val apis = listOf(PatientProfileApi::class.java, PreRegistrationsApi::class.java, TenantsApi::class.java)

    private fun Method.route(): String? = annotations.firstNotNullOfOrNull {
        when (it) {
            is GET -> it.value
            is POST -> it.value
            is PATCH -> it.value
            is PUT -> it.value
            is DELETE -> it.value
            else -> null
        }
    }

    @Test fun `R26 las interfaces Retrofit de patients y encounters usan solo rutas bajo api v1 patient y api v1 public`() {
        val routes = apis.flatMap { api -> api.declaredMethods.map { "${api.simpleName}.${it.name}" to it.route() } }
        assertEquals(5, routes.size)
        for ((name, route) in routes) {
            assertTrue("$name → $route", route != null && (route.startsWith("api/v1/patient/") || route.startsWith("api/v1/public/")))
            assertFalse("$name → $route", route!!.contains("{"))
        }
    }

    @Test fun `R26 sin parametros de ruta ni de query y los cuerpos no llevan patientId ni tenantId`() {
        val forbidden = setOf(Path::class.java, Query::class.java, QueryMap::class.java, Url::class.java)
        for (api in apis) {
            for (method in api.declaredMethods) {
                val params = method.parameterAnnotations.flatMap { it.toList() }
                assertTrue("${method.name}: $params", params.none { it.annotationClass.java in forbidden })
                assertTrue(params.all { it is Body || (it is Header && it.value == "Authorization") })
            }
        }
        val bodies = listOf(UpdatePatientProfileRequest.serializer().descriptor, CreatePreRegistrationRequest.serializer().descriptor)
        for (descriptor in bodies) {
            val names = (0 until descriptor.elementsCount).map(descriptor::getElementName)
            assertFalse(names.toString(), "patientId" in names || "tenantId" in names)
        }
    }

    @Test fun `R26 patients y encounters no crean otras interfaces Retrofit ni llaman a endpoints de personal`() {
        val sources = listOf("feature/patients", "feature/encounters").flatMap {
            File("src/main/java/co/asclepio/mobile/$it").walkTopDown().filter { f -> f.extension == "kt" }.toList()
        }
        assertTrue(sources.isNotEmpty())
        val route = Regex("""@(GET|POST|PATCH|PUT|DELETE)\("([^"]*)"\)""")
        for (f in sources) {
            for (m in route.findAll(f.readText())) {
                val path = m.groupValues[2]
                assertTrue("${f.name}: $path", path.startsWith("api/v1/patient/") || path.startsWith("api/v1/public/"))
            }
        }
    }
}
