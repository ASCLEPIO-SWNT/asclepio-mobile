package co.asclepio.mobile.core.network

import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Error de una llamada a la API (D7). */
sealed interface ApiError {
    /** Sin respuesta HTTP (IOException). */
    data object Network : ApiError

    /** Respuesta HTTP de error, ya convertida a ProblemDetails. */
    data class Http(val problem: ProblemDetails) : ApiError

    /** Respuesta 2xx con un cuerpo que no corresponde al contrato. */
    data object InvalidBody : ApiError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Ejecuta una llamada de Retrofit y la convierte a [ApiResult] (R23, R24). La cancelación se propaga. */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        val response = e.response()
        val body = runCatching { response?.errorBody()?.string() }.getOrNull()
        ApiResult.Failure(ApiError.Http(parseProblemDetails(e.code(), body, response?.headers()?.get("Retry-After"))))
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network)
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.InvalidBody)
    }

val ApiError.httpStatus: Int?
    get() = (this as? ApiError.Http)?.problem?.status

/** Segundos de bloqueo si el error es un 429 (R22); `null` en otro caso. */
fun ApiError.rateLimitSeconds(): Int? =
    (this as? ApiError.Http)?.problem?.takeIf { it.status == 429 }?.retryAfterOrDefault()

/**
 * Mensaje de la app común a todas las pantallas (R22, R23, R34): 429, sin conexión o general.
 * Nunca usa `title`, `detail` ni `errors[].message` de la API.
 */
fun ApiError.commonMessage(): UiMessage {
    rateLimitSeconds()?.let { return UiMessage(R.string.error_too_many_requests, listOf(it)) }
    return when (this) {
        ApiError.Network -> UiMessage(R.string.error_network)
        else -> UiMessage(R.string.error_general)
    }
}
