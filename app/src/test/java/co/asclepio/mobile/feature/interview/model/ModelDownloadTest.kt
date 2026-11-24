package co.asclepio.mobile.feature.interview.model

import androidx.work.NetworkType
import androidx.work.WorkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Descarga del modelo con OkHttp real contra MockWebServer (T6): Range, SHA-256, espacio y cancelación. */
class ModelDownloadTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val content = Random(11).nextBytes(300_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private var free = Long.MAX_VALUE
    private lateinit var store: ModelStore
    private val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()

    @Before fun setUp() {
        server.start()
        store = ModelStore(tmp.root, ModelSpec(server.url("/m/model.litertlm").toString(), content.size.toLong(), sha, "test"), { free })
    }

    @After fun tearDown() = server.shutdown()

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    private suspend fun download(onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) { ModelDownloader(client, store).download(onProgress) }

    @Test fun `R4 una descarga completa con el SHA-256 correcto se renombra al destino final`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(content)))
        val progress = mutableListOf<Long>()
        assertEquals(DownloadResult.Verified, download { written, _ -> progress += written })
        assertTrue(store.isVerified())
        assertFalse(store.partFile.exists())
        assertArrayEquals(content, store.modelFile.readBytes())
        assertEquals(content.size.toLong(), progress.last())
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals(File(tmp.root, "models"), store.modelFile.parentFile)
    }

    @Test fun `R4 un SHA-256 incorrecto borra el part y marca el error`() = runTest {
        val bad = content.copyOf().also { it[10] = (it[10] + 1).toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(bad)))
        assertEquals(DownloadResult.HashMismatch, download())
        assertFalse(store.partFile.exists())
        assertFalse(store.isVerified())
    }

    @Test fun `R3 una descarga cortada conserva el part y se reanuda con Range desde los bytes escritos`() = runTest {
        val cut = 120_000
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", content.size).setBody(body(content.copyOf(cut)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )
        assertEquals(DownloadResult.Interrupted, download())
        assertEquals(cut.toLong(), store.partialBytes())
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(206).setBody(body(content.copyOfRange(cut, content.size))))
        assertEquals(DownloadResult.Verified, download())
        assertEquals("bytes=$cut-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, store.modelFile.readBytes())
    }

    @Test fun `R3 si el servidor ignora Range y responde 200 se reescribe desde cero`() = runTest {
        store.dir.mkdirs()
        store.partFile.writeBytes(ByteArray(5_000) { 7 })
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(content)))
        assertEquals(DownloadResult.Verified, download())
        assertArrayEquals(content, store.modelFile.readBytes())
    }

    @Test fun `R2 con menos de 2 veces el tamano libre no se inicia y se informa cuanto falta`() = runTest {
        free = content.size * 2L - 1_000
        assertEquals(DownloadResult.InsufficientSpace(1_000), download())
        assertEquals(0, server.requestCount)
        assertEquals(1_000L, store.missingSpace())
        free = content.size * 2L
        assertNull(store.missingSpace())
    }

    @Test fun `R5 cancelar corta la llamada conserva el part y borrar elimina modelo y part`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(content)).throttleBody(16_384, 50, TimeUnit.MILLISECONDS))
        withContext(Dispatchers.IO) {
            val job = launch { ModelDownloader(client, store).download() }
            withTimeout(5_000) { while (store.partialBytes() == 0L) kotlinx.coroutines.delay(10) }
            job.cancel()
            job.join()
        }
        assertTrue(store.partialBytes() in 1 until content.size)
        assertFalse(store.isVerified())
        store.deleteAll()
        assertFalse(store.partFile.exists())
        assertFalse(store.modelFile.exists())
    }

    @Test fun `R1 el trabajo exige red no medida`() {
        val request = modelDownloadRequest()
        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(ModelDownloadWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test fun `R5 el estado de la UI muestra progreso en porcentaje, espera de Wi-Fi, errores y verificado`() {
        val total = 2_000L
        assertEquals(ModelDownloadState.Verified, modelDownloadState(WorkSnapshot(WorkInfo.State.RUNNING), true, 0, total, null))
        val running = modelDownloadState(WorkSnapshot(WorkInfo.State.RUNNING, 500, total), false, 0, total, null)
        assertEquals(ModelDownloadState.Downloading(500, total), running)
        assertEquals(25, (running as ModelDownloadState.Downloading).percent)
        assertEquals(ModelDownloadState.Waiting(300, total), modelDownloadState(WorkSnapshot(WorkInfo.State.ENQUEUED), false, 300, total, null))
        assertEquals(ModelDownloadState.Corrupted, modelDownloadState(WorkSnapshot(WorkInfo.State.FAILED, error = ModelWork.ERROR_HASH), false, 0, total, null))
        assertEquals(ModelDownloadState.NotEnoughSpace(9), modelDownloadState(null, false, 0, total, 9))
        assertEquals(ModelDownloadState.NotDownloaded, modelDownloadState(WorkSnapshot(WorkInfo.State.SUCCEEDED), false, 0, total, null))
        assertEquals(ModelDownloadState.NotDownloaded, modelDownloadState(WorkSnapshot(WorkInfo.State.CANCELLED), false, 0, total, null))
    }

    @Test fun `R5 el APK no incluye el modelo ni assets litertlm`() {
        val assets = File("src/main/assets").walkTopDown().filter { it.isFile }.map { it.name }.toList()
        assertTrue(assets.none { it.endsWith(".litertlm") || it.endsWith(".task") || it.endsWith(".bin") })
        val build = File("build.gradle.kts").readText()
        assertFalse(build.contains("litertlm\"") && build.contains("assets.srcDirs"))
    }
}
