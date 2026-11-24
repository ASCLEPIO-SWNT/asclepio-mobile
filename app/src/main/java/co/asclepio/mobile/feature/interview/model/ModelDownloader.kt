package co.asclepio.mobile.feature.interview.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Resultado de una pasada de descarga. */
sealed interface DownloadResult {
    data object Verified : DownloadResult

    /** R2: falta espacio; no se inició. */
    data class InsufficientSpace(val missingBytes: Long) : DownloadResult

    /** R4: el SHA-256 no coincide; el `.part` se borró. */
    data object HashMismatch : DownloadResult

    /** Red cortada o error HTTP transitorio: el `.part` se conserva para reanudar (R3). */
    data object Interrupted : DownloadResult
}

/**
 * Descarga reanudable del modelo con OkHttp (D3): `Range: bytes=<escritos>-` sobre el `.part`, SHA-256 calculado
 * mientras se escribe (tras volver a leer lo ya escrito al reanudar) y rename atómico al verificar (R3, R4).
 * La cancelación de la corrutina corta la llamada y conserva el `.part`. Sin logs.
 */
class ModelDownloader(private val client: OkHttpClient, private val store: ModelStore) {
    suspend fun download(onProgress: (written: Long, total: Long) -> Unit = { _, _ -> }): DownloadResult = withContext(Dispatchers.IO) {
        if (store.isVerified()) return@withContext DownloadResult.Verified
        store.missingSpace()?.let { return@withContext DownloadResult.InsufficientSpace(it) }
        store.dir.mkdirs()
        val total = store.spec.bytes
        if (store.partialBytes() > total) store.deletePart()
        var existing = store.partialBytes()
        if (existing == total) return@withContext verify()

        val request = Request.Builder().url(store.spec.url).apply { if (existing > 0) header("Range", "bytes=$existing-") }.build()
        val call = client.newCall(request)
        val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val append = when {
                    response.code == 206 && existing > 0 -> true
                    response.code == 200 -> false
                    response.code == 416 -> {
                        store.deletePart()
                        return@withContext DownloadResult.Interrupted
                    }
                    else -> return@withContext DownloadResult.Interrupted
                }
                if (!append) existing = 0
                val digest = MessageDigest.getInstance("SHA-256")
                if (append) store.partFile.inputStream().use { input -> digestStream(input, digest) }
                var written = existing
                FileOutputStream(store.partFile, append).use { out ->
                    val source = response.body.byteStream()
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        written += n
                        onProgress(written, total)
                    }
                    out.fd.sync()
                }
                if (written < total) return@withContext DownloadResult.Interrupted
                finish(digest, written)
            }
        } catch (e: IOException) {
            coroutineContext.ensureActive()
            DownloadResult.Interrupted
        } finally {
            handle.dispose()
        }
    }

    private fun verify(): DownloadResult {
        val digest = MessageDigest.getInstance("SHA-256")
        store.partFile.inputStream().use { digestStream(it, digest) }
        return finish(digest, store.partialBytes())
    }

    private fun finish(digest: MessageDigest, written: Long): DownloadResult {
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return if (written == store.spec.bytes && hex.equals(store.spec.sha256, ignoreCase = true)) {
            store.promotePart()
            DownloadResult.Verified
        } else {
            store.deletePart()
            DownloadResult.HashMismatch
        }
    }

    private fun digestStream(input: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
