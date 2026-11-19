package co.asclepio.mobile.feature.interview.model

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** Estado de la descarga del asistente para la UI (R5). */
sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState

    /** Encolada a la espera de Wi-Fi (R1), con lo ya escrito si se reanuda. */
    data class Waiting(val written: Long, val total: Long) : ModelDownloadState

    data class Downloading(val written: Long, val total: Long) : ModelDownloadState {
        val percent: Int get() = if (total <= 0) 0 else (written * 100 / total).toInt()
    }

    data object Verified : ModelDownloadState

    /** R2: falta espacio. */
    data class NotEnoughSpace(val missingBytes: Long) : ModelDownloadState

    /** R4: el SHA-256 no coincidió y se borró el `.part`. */
    data object Corrupted : ModelDownloadState
}

/** Lo que importa de un `WorkInfo` (para probar el mapeo en la JVM). */
data class WorkSnapshot(
    val state: WorkInfo.State,
    val written: Long? = null,
    val total: Long? = null,
    val error: String? = null,
    val missing: Long? = null,
)

/** Estado de la UI: el archivo verificado manda; si no, el trabajo; si no hay trabajo, lo escrito en disco. */
fun modelDownloadState(work: WorkSnapshot?, verified: Boolean, partial: Long, total: Long, spaceError: Long?): ModelDownloadState {
    if (verified) return ModelDownloadState.Verified
    spaceError?.let { return ModelDownloadState.NotEnoughSpace(it) }
    return when (work?.state) {
        WorkInfo.State.RUNNING -> ModelDownloadState.Downloading(work.written ?: partial, work.total ?: total)
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadState.Waiting(partial, total)
        WorkInfo.State.FAILED -> when (work.error) {
            ModelWork.ERROR_SPACE -> ModelDownloadState.NotEnoughSpace(work.missing ?: 0L)
            ModelWork.ERROR_HASH -> ModelDownloadState.Corrupted
            else -> ModelDownloadState.NotDownloaded
        }
        else -> ModelDownloadState.NotDownloaded
    }
}

/**
 * Descarga opcional del modelo (R1–R5): encola el trabajo único, lo cancela y borra el modelo. El modelo vive solo en
 * `filesDir/models/`; nunca va en el APK.
 */
class AssistantModelRepository(private val workManager: WorkManager, val store: ModelStore) {
    private val spaceError = MutableStateFlow<Long?>(null)
    private val refresh = MutableStateFlow(0)

    val state: Flow<ModelDownloadState> =
        combine(workManager.getWorkInfosForUniqueWorkFlow(ModelWork.NAME), spaceError, refresh) { infos, space, _ ->
            val info = infos.firstOrNull()
            val snapshot = info?.let {
                WorkSnapshot(
                    it.state,
                    it.progress.getLong(ModelWork.WRITTEN, -1).takeIf { v -> v >= 0 },
                    it.progress.getLong(ModelWork.TOTAL, -1).takeIf { v -> v > 0 },
                    it.outputData.getString(ModelWork.ERROR),
                    it.outputData.getLong(ModelWork.MISSING, -1).takeIf { v -> v >= 0 },
                )
            }
            modelDownloadState(snapshot, store.isVerified(), store.partialBytes(), store.spec.bytes, space)
        }

    fun isVerified(): Boolean = store.isVerified()

    /** R1, R2: solo se encola si hay espacio; si no, se muestra cuánto falta. */
    fun download() {
        val missing = store.missingSpace()
        spaceError.value = missing
        if (missing == null) workManager.enqueueUniqueWork(ModelWork.NAME, ExistingWorkPolicy.KEEP, modelDownloadRequest())
        refresh.update { it + 1 }
    }

    /** R5: cancela la descarga y borra lo descargado a medias. */
    fun cancel() {
        workManager.cancelUniqueWork(ModelWork.NAME)
        store.deletePart()
        spaceError.value = null
        refresh.update { it + 1 }
    }

    /** R5: borra el modelo. */
    fun delete() {
        workManager.cancelUniqueWork(ModelWork.NAME)
        store.deleteAll()
        spaceError.value = null
        refresh.update { it + 1 }
    }
}
