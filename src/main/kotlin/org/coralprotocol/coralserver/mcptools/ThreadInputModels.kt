package org.coralprotocol.coralserver.mcptools

import kotlinx.serialization.Serializable

/**
 * Tool for creating a new thread.
 */
@Serializable
data class CreateThreadInput(
    val threadName: String,
    val participantIds: List<String>
)

/**
 * Tool for adding a participant to a thread.
 */
@Serializable
data class AddParticipantInput(
    val threadId: String,
    val participantId: String
)

/**
 * Tool for removing a participant from a thread.
 */
@Serializable
data class RemoveParticipantInput(
    val threadId: String,
    val participantId: String
)

/**
 * Tool for closing a thread with a summary.
 */
@Serializable
data class CloseThreadInput(
    val threadId: String,
    val summary: String
)

/**
 * Tool for sending a message to a thread.
 */
@Serializable
data class SendMessageInput(
    val threadId: String,
    val content: String,
    val mentions: List<String> = emptyList()
)

/**
 * Tool for waiting for new messages mentioning an agent.
 */
@Serializable
data class WaitForMentionsInput(
    val timeoutMs: Long = 30000
)

/**
 * Tool for listing all registered agents.
 */
@Serializable
data class ListAgentsInput(
    val includeDetails: Boolean = true // Whether to include agent details in the response
)
