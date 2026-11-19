package co.asclepio.mobile.feature.interview.model

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Modelo fijado en el build (BuildConfig MODEL_URL, MODEL_BYTES, MODEL_SHA256 y MODEL_ID; D2). */
data class ModelSpec(val url: String, val bytes: Long, val sha256: String, val id: String) {
    val fileName: String get() = url.substringAfterLast('/').substringBefore('?')
}

/**
 * Archivos del modelo en `filesDir/models/` (excluidos de copias de seguridad). El destino final solo existe tras
 * verificar el SHA-256 y renombrar el `.part` atómicamente (R4); el APK no incluye el modelo (R5).
 * [freeBytes] es el espacio libre del volumen (inyectable para las pruebas).
 */
class ModelStore(
    filesDir: File,
    val spec: ModelSpec,
    private val freeBytes: () -> Long = { filesDir.usableSpace },
) {
    val dir: File = File(filesDir, "models")
    val modelFile: File get() = File(dir, spec.fileName)
    val partFile: File get() = File(dir, spec.fileName + ".part")

    /** El modelo está verificado: el destino final existe con el tamaño fijado. */
    fun isVerified(): Boolean = modelFile.isFile && modelFile.length() == spec.bytes

    /** Bytes ya escritos en el `.part` (para reanudar con `Range`, R3). */
    fun partialBytes(): Long = if (partFile.isFile) partFile.length() else 0L

    /**
     * R2: bytes que faltan para tener libres 2 × el tamaño del modelo, o `null` si alcanza. Al reanudar, lo ya escrito
     * en el `.part` cuenta como disponible.
     */
    fun missingSpace(): Long? {
        val missing = 2 * spec.bytes - partialBytes() - freeBytes()
        return missing.takeIf { it > 0 }
    }

    /** R4: rename atómico del `.part` verificado al destino final. */
    fun promotePart() {
        Files.move(partFile.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun deletePart() {
        partFile.delete()
    }

    /** R5: borra el modelo y cualquier descarga parcial. */
    fun deleteAll() {
        deletePart()
        modelFile.delete()
    }
}
