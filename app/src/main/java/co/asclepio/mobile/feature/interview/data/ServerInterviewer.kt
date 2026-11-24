package co.asclepio.mobile.feature.interview.data

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.patientCall
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.InterviewLimits
import co.asclepio.mobile.core.interview.InterviewUnavailableException
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.SymptomInterviewer
import co.asclepio.mobile.core.interview.Turn
import co.asclepio.mobile.core.network.ApiResult

/**
 * Motor del servidor (F-11-mobile R6, R7): `POST /patient/symptom-interview/next-question` con el Bearer capturado
 * por [patientCall]. Cualquier error HTTP (429, 503…), corte de red, timeout o cuerpo fuera del contrato lanza
 * [InterviewUnavailableException]: la sesión termina con lo respondido. Sin logs (R16).
 */
class ServerInterviewer(
    private val api: SymptomInterviewApi,
    private val sessionManager: SessionManager,
) : SymptomInterviewer {
    override val engine = InterviewEngine.SERVER
    override val modelId = MODEL_ID

    override suspend fun next(complaint: String, turns: List<Turn>): NextStep {
        val request = NextQuestionRequest(complaint, turns.take(InterviewLimits.MAX_REQUEST_TURNS))
        val result = sessionManager.patientCall { bearer -> api.nextQuestion(request, bearer) }
        val dto = (result as? ApiResult.Success)?.value ?: throw InterviewUnavailableException()
        return when (dto.kind) {
            "QUESTION" -> NextStep.Question(
                dto.dimension ?: throw InterviewUnavailableException(),
                dto.question ?: throw InterviewUnavailableException(),
            )
            "DONE" -> NextStep.Done
            "RED_FLAG" -> NextStep.RedFlagHit(dto.redFlags.orEmpty())
            else -> throw InterviewUnavailableException()
        }
    }

    companion object {
        /** `modelId` del pre-registro cuando las preguntas las generó el servidor (prompt de F-11-api). */
        const val MODEL_ID = "server:interview-v2"
    }
}
