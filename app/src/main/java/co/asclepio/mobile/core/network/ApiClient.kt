package co.asclepio.mobile.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** JSON común: ignora campos desconocidos y no envía valores por defecto (los cuerpos llevan exactamente sus campos). */
val appJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * Retrofit + kotlinx.serialization. `baseUrl` es el origen de la API; las rutas del contrato ya incluyen /api/v1.
 * Sin interceptor de logs (R33): para depurar la red se usa el inspector de Android Studio.
 */
fun createRetrofit(baseUrl: String, client: OkHttpClient = OkHttpClient()): Retrofit =
    Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
        .build()
