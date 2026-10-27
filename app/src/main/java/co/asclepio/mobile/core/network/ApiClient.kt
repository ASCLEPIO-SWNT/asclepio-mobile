package co.asclepio.mobile.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val json = Json { ignoreUnknownKeys = true }

/** Retrofit + kotlinx.serialization. `baseUrl` es el origen de la API; las rutas del contrato ya incluyen /api/v1. */
fun createRetrofit(baseUrl: String): Retrofit =
    Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
