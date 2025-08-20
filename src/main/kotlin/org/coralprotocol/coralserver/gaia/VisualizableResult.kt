package org.coralprotocol.coralserver.gaia

import kotlinx.serialization.Serializable

@Serializable
data class VisualizableResult(
    val result: GaiaResult
) {
    /**
     * The total character count of all messages in the threads of the result.
     *
     * Note this will be significantly smaller than the total number of characters/tokens used in the answer attempt.
     */
    val characterCount: Int = result.threads?.sumOf { thread -> thread.messages.sumOf { it.content.length } } ?: run {
        throw IllegalStateException("Threads are null for result: ${result.question.taskId}")
    }

}
