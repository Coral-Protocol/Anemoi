package org.coralprotocol.coralserver.mcptools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the remove participant tool to a server.
 */
fun CoralAgentIndividualMcp.addRemoveParticipantTool() {
    addTool(
        name = "remove_participant",
        description = "Remove a participant from a thread",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("participantId") {
                    put("type", "string")
                    put("description", "ID of the agent to remove")
                }
            },
            required = listOf("threadId", "participantId")
        )
    ) { request ->
        handleRemoveParticipant(request)
    }
}

/**
 * Handles the remove participant tool request.
 */
private fun CoralAgentIndividualMcp.handleRemoveParticipant(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<RemoveParticipantInput>(request.arguments.toString())
        val success = coralAgentGraphSession.removeParticipantFromThread(
            threadId = input.threadId,
            participantId = input.participantId
        )

        if (success) {
            return CallToolResult(
                content = listOf(TextContent("Participant removed successfully from thread ${input.threadId}"))
            )
        } else {
            val errorMessage = "Failed to remove participant: Thread not found, participant not found, or thread is closed"
            logger.error { errorMessage }
            return CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error removing participant: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
