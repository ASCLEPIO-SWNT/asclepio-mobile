package co.asclepio.mobile.feature.interview.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.asclepio.mobile.BuildConfig
import co.asclepio.mobile.R
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Modelo del build (D2). */
val BUILD_MODEL = ModelSpec(BuildConfig.MODEL_URL, BuildConfig.MODEL_BYTES, BuildConfig.MODEL_SHA256, BuildConfig.MODEL_ID)

/** Claves de progreso y salida del trabajo. */
object ModelWork {
    const val NAME = "assistant-model-download"
    const val WRITTEN = "written"
    const val TOTAL = "total"
    const val ERROR = "error"
    const val MISSING = "missing"
    const val ERROR_HASH = "hash"
    const val ERROR_SPACE = "space"
}

/**
 * R1: descarga en WorkManager solo con red no medida (Wi-Fi), con reintento exponencial que reanuda el `.part` (R3).
 * Se construye aparte para probar sus restricciones en la JVM.
 */
fun modelDownloadRequest(): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

/**
 * Trabajo en primer plano (`dataSync`) con notificación de progreso (R1) que delega en [ModelDownloader]. El progreso
 * se publica para la UI (porcentaje y MB, R5). Sin logs.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val store = ModelStore(context.filesDir, BUILD_MODEL)
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private var lastPercent = -1

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo(0))
        val result = ModelDownloader(client, store).download { written, total ->
            val percent = (written * 100 / total).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                setProgressAsync(workDataOf(ModelWork.WRITTEN to written, ModelWork.TOTAL to total))
                notificationManager().notify(NOTIFICATION_ID, notification(percent))
            }
        }
        return when (result) {
            DownloadResult.Verified -> Result.success()
            is DownloadResult.InsufficientSpace ->
                Result.failure(workDataOf(ModelWork.ERROR to ModelWork.ERROR_SPACE, ModelWork.MISSING to result.missingBytes))
            DownloadResult.HashMismatch -> Result.failure(workDataOf(ModelWork.ERROR to ModelWork.ERROR_HASH))
            DownloadResult.Interrupted -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    private fun notificationManager(): NotificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val name = applicationContext.getString(R.string.assistant_download_channel)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW))
        }
        return ForegroundInfo(NOTIFICATION_ID, notification(percent), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notification(percent: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.assistant_download_notification))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private companion object {
        const val CHANNEL_ID = "assistant-model"
        const val NOTIFICATION_ID = 1101
    }
}
