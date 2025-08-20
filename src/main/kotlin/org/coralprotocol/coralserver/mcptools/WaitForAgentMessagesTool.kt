package org.coralprotocol.coralserver.mcptools

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import nl.adaptivity.xmlutil.serialization.XML
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.ResolvedMessage
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger(name = "AgentMessages")

/**
 * Input model for the wait for agent messages tool.
 */
@Serializable
data class WaitForAgentMessagesInput(
    val agentIds: List<String>,
    val timeoutMs: Long = 30000
)

/**
 * Extension function to add the wait for agent messages tool to a server.
 */
fun CoralAgentIndividualMcp.addWaitForAgentMessagesTool() {
    addTool(
        name = "wait_for_agent_messages",
        description = "Wait for messages from specific agents in any thread you participate in. This will block until a message is received from any of the specified agents or until the timeout is reached.",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("agentIds") {
                    put("type", "array")
                    put("description", "List of agent IDs to wait for messages from")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
                putJsonObject("timeoutMs") {
                    put("type", "number")
                    put("description", "Timeout in milliseconds (default: 30000 ms). Must be between 0 and $maxWaitForMentionsTimeoutMs ms.")
                }
            },
            required = listOf("agentIds", "timeoutMs")
        )
    ) { request: CallToolRequest ->
        handleWaitForAgentMessages(request)
    }
}

/**
 * Handles the wait for agent messages tool request.
 */
private suspend fun CoralAgentIndividualMcp.handleWaitForAgentMessages(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<WaitForAgentMessagesInput>(request.arguments.toString())
        logger.info { "Waiting for messages from agents ${input.agentIds} for agent $connectedAgentId with timeout ${input.timeoutMs}ms" }

        if (input.agentIds.isEmpty()) {
            return CallToolResult(
                content = listOf(TextContent("At least one agent ID must be specified"))
            )
        }

        if (input.timeoutMs < 0) {
            return CallToolResult(
                content = listOf(TextContent("Timeout must be greater than 0"))
            )
        }
        if (input.timeoutMs > maxWaitForMentionsTimeoutMs) {
            return CallToolResult(
                content = listOf(TextContent("Timeout must not exceed the maximum of $maxWaitForMentionsTimeoutMs ms"))
            )
        }

        // Use the session to wait for messages from specific agents
        val messages = coralAgentGraphSession.waitForAgentMessages(
            agentId = connectedAgentId,
            fromAgentIds = input.agentIds,
            timeoutMs = input.timeoutMs
        )

        if (messages.isNotEmpty()) {
            messages.forEach { message ->
                logger.info {
                    "[${message.sender.id}] -> ${message.mentions}: ${message.content}"
                }
            }
            val formattedMessages = XML.encodeToString<List<org.coralprotocol.coralserver.models.ResolvedMessage>>(messages.map { message -> message.resolve() })
            return CallToolResult(
                content = listOf(TextContent(formattedMessages))
            )
        } else {
            return CallToolResult(
                content = listOf(TextContent("No messages received from the specified agents within the timeout period"))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error waiting for agent messages: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
