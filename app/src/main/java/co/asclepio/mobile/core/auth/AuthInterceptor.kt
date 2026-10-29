package co.asclepio.mobile.core.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade `Authorization: Bearer <accessToken>` solo fuera de `/api/v1/public/` y `/api/v1/auth/` (D9, R29),
 * y nunca sobrescribe una cabecera explícita (el `GET /me` justo después del login).
 */
class AuthInterceptor(private val accessToken: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!requiresAuth(request.url.encodedPath)) return chain.proceed(request.newBuilder().removeHeader(HEADER).build())
        if (request.header(HEADER) != null) return chain.proceed(request)
        val token = accessToken() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, "Bearer $token").build())
    }

    companion object {
        private const val HEADER = "Authorization"

        fun requiresAuth(path: String): Boolean =
            !path.startsWith("/api/v1/public/") && !path.startsWith("/api/v1/auth/")
    }
}
