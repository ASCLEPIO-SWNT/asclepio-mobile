package co.asclepio.mobile.core.network

import kotlinx.serialization.Serializable

/** FieldErrorDto del contrato. `message` nunca se muestra en la UI (R23, D8). */
@Serializable
data class FieldError(val field: String, val message: String)

/**
 * ProblemDetailsDto del contrato (RFC 9457) más el valor de la cabecera `Retry-After` (R24).
 * `status` es siempre el código HTTP de la respuesta. Si el cuerpo no es problem+json válido,
 * solo `status` (y `retryAfterSeconds`, si la cabecera es válida) tienen valor.
 */
data class ProblemDetails(
    val status: Int,
    val type: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<FieldError> = emptyList(),
    val retryAfterSeconds: Int? = null,
) {
    /** Segundos de espera tras un 429 (R22): la cabecera si es un entero positivo; si no, 60 s. */
    fun retryAfterOrDefault(): Int = retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS

    companion object {
        const val DEFAULT_RETRY_AFTER_SECONDS = 60
    }
}

/** Cuerpo problem+json tal como llega; `type`, `title` y `status` son obligatorios en el contrato. */
@Serializable
private data class ProblemBody(
    val type: String,
    val title: String,
    val status: Double,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<FieldError> = emptyList(),
)

/** Convierte una respuesta de error a [ProblemDetails] sin lanzar nunca (R24). */
fun parseProblemDetails(status: Int, body: String?, retryAfterHeader: String?): ProblemDetails {
    val retryAfter = retryAfterHeader?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    val parsed = body?.takeIf { it.isNotBlank() }?.let {
        runCatching { appJson.decodeFromString(ProblemBody.serializer(), it) }.getOrNull()
    } ?: return ProblemDetails(status = status, retryAfterSeconds = retryAfter)
    return ProblemDetails(
        status = status,
        type = parsed.type,
        title = parsed.title,
        detail = parsed.detail,
        instance = parsed.instance,
        errors = parsed.errors,
        retryAfterSeconds = retryAfter,
    )
}
