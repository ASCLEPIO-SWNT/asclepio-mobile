package co.asclepio.mobile.feature.interview.data

import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.RedFlag
import co.asclepio.mobile.core.interview.Turn
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// DTOs escritos a mano según el snapshot 0.11.0 de ../asclepio-api/openapi/asclepio-api.v1.json
// (NextQuestionRequestDto y NextQuestionDto).

/** NextQuestionRequestDto: motivo de 1 a 2000 y de 0 a 5 turnos. Sin contenido en `toString()` (R16). */
@Serializable
data class NextQuestionRequest(val chiefComplaintText: String, val turns: List<Turn>) {
    override fun toString() = "NextQuestionRequest(chiefComplaintText=***, turns=${turns.size})"
}

/** NextQuestionDto: QUESTION lleva dimension y question; RED_FLAG, redFlags; DONE, nada más. */
@Serializable
data class NextQuestionDto(
    val kind: String,
    val dimension: Dimension? = null,
    val question: String? = null,
    val redFlags: List<RedFlag>? = null,
) {
    override fun toString() = "NextQuestionDto(kind=$kind, dimension=$dimension, question=***, redFlags=$redFlags)"
}

/** Entrevista en el servidor (F-11-api R11): solo el paciente, sin estado (Bearer del paciente). */
interface SymptomInterviewApi {
    @POST("api/v1/patient/symptom-interview/next-question")
    suspend fun nextQuestion(@Body body: NextQuestionRequest, @Header("Authorization") authorization: String): NextQuestionDto
}
