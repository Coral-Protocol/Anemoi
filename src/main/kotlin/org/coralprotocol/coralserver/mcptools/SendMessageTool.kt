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
 * Extension function to add the send message tool to a server.
 */
fun CoralAgentIndividualMcp.addSendMessageTool() {
    addTool(
        name = "send_message",
        description = "Send a message to a thread",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Content of the message")
                }
                putJsonObject("mentions") {
                    put("type", "array")
                    put("description", "List of agent IDs to mention in the message. You *must* mention an agent for them to be made aware of the message. @mentions are not supported yet, agents must be mentioned by ID with this parameter")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("threadId", "content", "mentions")
        )
    ) { request ->
        handleSendMessage(request)
    }
}

/**
 * Handles the send message tool request.
 */
private suspend fun CoralAgentIndividualMcp.handleSendMessage(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<SendMessageInput>(request.arguments.toString())
        val mentionsSelf = input.mentions.contains(this.connectedAgentId)
        if( mentionsSelf) {
            logger.warn { "${this.connectedAgentId} mentioned themselves in a message, which is not necessary and may reflect confusion. Failing." }
            return CallToolResult(
                content = listOf(TextContent("You (${this.connectedAgentId}) mentioned yourself in the message, which is not necessary and may reflect confusion. Try again with updated mentions."))
            )
        }

        val message = coralAgentGraphSession.sendMessage(
            threadId = input.threadId,
            senderId = this.connectedAgentId,
            content = input.content,
            mentions = input.mentions
        )

        logger.info { message }

        return CallToolResult(
            content = listOf(
                TextContent(
                    """
                    Message sent successfully:
                    Mentions: ${message.mentions.joinToString(", ")} (if you need to notify an agent, you must mention them in the 'mentions' parameter)
                    """.trimIndent()
                )
            )
        )
    } catch (e: Exception) {
        val errorMessage = "Error sending message: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
