package org.coralprotocol.coralserver.mcptools

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the add participant tool to a server.
 */
fun CoralAgentIndividualMcp.addAddParticipantTool() {
    addTool(
        name = "add_participant",
        description = "Add a participant to a thread",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("participantId") {
                    put("type", "string")
                    put("description", "ID of the agent to add")
                }
            },
            required = listOf("threadId", "participantId")
        )
    ) { request ->
        handleAddParticipant(request)
    }
}

/**
 * Handles the add participant tool request.
 */
private fun CoralAgentIndividualMcp.handleAddParticipant(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<AddParticipantInput>(request.arguments.toString())
        val success = coralAgentGraphSession.addParticipantToThread(
            threadId = input.threadId,
            participantId = input.participantId
        )

        if (success) {
            return CallToolResult(
                content = listOf(TextContent("Participant added successfully to thread ${input.threadId}"))
            )
        } else {
            val errorMessage = "Failed to add participant: Thread not found, participant not found, or thread is closed"
            logger.error { errorMessage }
            return CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error adding participant: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
