package co.asclepio.mobile.feature.encounters.queue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// DTOs escritos a mano según el snapshot 0.10.0 de ../asclepio-api/openapi/asclepio-api.v1.json (QueuePositionDto y
// WaitEstimateDto). Un valor nuevo de un enum no rompe la instantánea: se lee como UNKNOWN.

/** Fase de la atención (F-10-api). [UNKNOWN] es el respaldo local para un valor que el contrato aún no publica. */
@Serializable(with = QueuePhaseSerializer::class)
enum class QueuePhase { NO_ACTIVE, WAITING_TRIAGE, WAITING_CARE, CALLED, DONE, UNKNOWN }

/** Base de la estimación; [UNKNOWN] es el respaldo local. */
@Serializable(with = EstimateBasisSerializer::class)
enum class EstimateBasis { RECENT_THROUGHPUT, INSUFFICIENT_DATA, UNKNOWN }

/** Rango de espera en minutos, calculado por el servidor. */
@Serializable
data class WaitEstimateDto(val minMinutes: Int, val maxMinutes: Int)

/**
 * QueuePositionDto: solo los campos del contrato. Cualquier otro (un nivel, un id, un nombre) se ignora al
 * deserializar (R10, F-10.AC6). No contiene datos personales.
 */
@Serializable
data class QueuePositionDto(
    val phase: QueuePhase,
    val position: Int?,
    val aheadCount: Int?,
    val estimate: WaitEstimateDto?,
    val estimateBasis: EstimateBasis?,
    val serverNow: String,
)

/** Enum leído por nombre, con [fallback] para cualquier valor desconocido. */
abstract class FallbackEnumSerializer<E : Enum<E>>(name: String, private val values: List<E>, private val fallback: E) :
    KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): E = decoder.decodeString().let { s -> values.firstOrNull { it.name == s } ?: fallback }
}

object QueuePhaseSerializer :
    FallbackEnumSerializer<QueuePhase>("co.asclepio.mobile.QueuePhase", QueuePhase.entries, QueuePhase.UNKNOWN)

object EstimateBasisSerializer :
    FallbackEnumSerializer<EstimateBasis>("co.asclepio.mobile.EstimateBasis", EstimateBasis.entries, EstimateBasis.UNKNOWN)
