package org.coralprotocol.coralserver.gaia

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.session.CoralAgentGraphSession

@Serializable
data class GaiaAnswerAttempt(
    val questionId: String,
    val answer: String,
    val sessionId: String,
    val certaintyPercentage: Int? = null,
    val justification: String
) {
    @Transient
    var session: CoralAgentGraphSession? = null
}