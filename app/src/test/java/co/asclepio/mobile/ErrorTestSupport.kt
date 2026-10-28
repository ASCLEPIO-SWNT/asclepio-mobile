package co.asclepio.mobile

import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.FieldError
import co.asclepio.mobile.core.network.ProblemDetails

/** Error HTTP con `title`, `detail` y `errors[].message` de la API, que la app nunca debe mostrar (R24). */
fun httpError(status: Int, vararg fields: String, retryAfter: Int? = null) = ApiError.Http(
    ProblemDetails(
        status = status,
        title = "Título de la API",
        detail = "Detalle de la API",
        errors = fields.map { FieldError(it, "mensaje de la API") },
        retryAfterSeconds = retryAfter,
    ),
)

/** Todos los errores que puede devolver una petición de F-02. */
val allApiErrors: List<ApiError> = listOf(
    ApiError.Network, ApiError.InvalidBody,
    httpError(400), httpError(400, "fullName", "birthDate", "sex"), httpError(400, "siteId", "chiefComplaintText"),
    httpError(400, "complaintSource"), httpError(401), httpError(403), httpError(404), httpError(409), httpError(422),
    httpError(429, retryAfter = 30), httpError(429), httpError(500), httpError(503),
)
