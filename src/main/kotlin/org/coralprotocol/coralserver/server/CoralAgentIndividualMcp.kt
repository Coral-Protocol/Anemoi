package org.coralprotocol.coralserver.server

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.mcpresources.addMessageResource
import org.coralprotocol.coralserver.session.CoralAgentGraphSession
import org.coralprotocol.coralserver.mcptools.addThreadTools
import org.coralprotocol.coralserver.session.CustomTool
import org.coralprotocol.coralserver.session.addExtraTool

/**
 * Represents a persistent connection to a Coral agent.
 * Each agent instance has a unique MCP server instance assigned to it.
 *
 * CoralSession
 *
 * This [CoralAgentIndividualMcp] should persist even if the agent reconnects via a different transport.
 */
class CoralAgentIndividualMcp(
    val connectedUri: String,
    /**
     * The latest transport used by the agent to connect to the server. It might change if the agent reconnects.
     */
    var latestTransport: SseServerTransport,
    /**
     * The session this agent is part of.
     */
    val coralAgentGraphSession: CoralAgentGraphSession,
    /**
     * The ID of the agent associated with this connection.
     */
    val connectedAgentId: String,
    val maxWaitForMentionsTimeoutMs: Long = 120000,
    val extraTools: Set<CustomTool> = setOf(),
) : Server(
    Implementation(
        name = "Coral Server",
        version = "0.1.0"
    ),
    ServerOptions(
        capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
            tools = ServerCapabilities.Tools(listChanged = true),
        )
    ),
) {
    init {
        addThreadTools()
        addMessageResource()
        extraTools.forEach {
            addExtraTool(coralAgentGraphSession.id, connectedAgentId, it)
        }
    }

    suspend fun closeTransport() {
        latestTransport.close()
    }
}

