package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.uri
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.sseRoutes(servers: ConcurrentMap<String, Server>, sessionManager: SessionManager) {
    sse("/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        handleSseConnection(
            "coral://" + call.request.host() + ":" + call.request.port() + call.request.uri,
            call.parameters,
            this,
            servers,
            sessionManager = sessionManager,
            isDevMode = false
        )
    }

    sse("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        handleSseConnection(
            "coral://" + call.request.host() + ":" + call.request.port() + call.request.uri,
            call.parameters,
            this,
            servers,
            sessionManager = sessionManager,
            isDevMode = true
        )
    }
}

/**
 * Centralizes SSE connection handling for both production and development modes.
 * Dev mode skips validation and allows on-demand session creation for testing,
 * while production enforces security checks and requires pre-created sessions.
 */
private suspend fun handleSseConnection(
    uri: String,
    parameters: Parameters,
    sseProducer: ServerSSESession,
    servers: ConcurrentMap<String, Server>,
    sessionManager: SessionManager,
    isDevMode: Boolean
): Boolean {
    val applicationId = parameters["applicationId"]
    val privacyKey = parameters["privacyKey"]
    val sessionId = parameters["coralSessionId"]
    val agentId = parameters["agentId"]
    val agentDescription: String = parameters["agentDescription"] ?: agentId ?: "no description"
    val maxWaitForMentionsTimeout = parameters["maxWaitForMentionsTimeout"]?.toLongOrNull() ?: ((60000) * 3)

    if (agentId == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing agentId parameter")
        return false
    }

    if (applicationId == null || privacyKey == null || sessionId == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
        return false
    }

    val session = if (isDevMode) {
        val waitForAgents = sseProducer.call.request.queryParameters["waitForAgents"]?.toIntOrNull() ?: 0
        val createdSession = sessionManager.getOrCreateSession(sessionId, applicationId, privacyKey, null)

        if (waitForAgents > 0) {
            createdSession.devRequiredAgentStartCount = waitForAgents
            logger.info { "DevMode: Setting waitForAgents=$waitForAgents for session $sessionId" }
        }

        createdSession
    } else {
        val existingSession = sessionManager.getSession(sessionId)
        if (existingSession == null) {
            sseProducer.call.respond(HttpStatusCode.NotFound, "Session not found")
            return false
        }

        if (existingSession.applicationId != applicationId || existingSession.privacyKey != privacyKey) {
            sseProducer.call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return false
        }

        existingSession
    }
    val currentCount = session.getRegisteredAgentsCount()
    // Register the agent
    val agent = session.registerAgent(agentId, uri, agentDescription, force = isDevMode || true)
    if (agent == null) {
        logger.info {"Agent ID $agentId already registered"}
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Agent ID already exists")
        return false
    }

    logger.info { "DevMode: Current agent count for session ${session.id} (object id: ${session}) (from sessionmanager: ${sessionManager}): $currentCount, waiting for: ${session.devRequiredAgentStartCount}" }
    val newCount = session.getRegisteredAgentsCount()
    logger.info { "DevMode: New agent count for session ${session.id} (object id: ${session})after registering: $newCount" }

    val routePrefix = if (isDevMode) "/devmode" else ""
    val endpoint = "$routePrefix/$applicationId/$privacyKey/$sessionId/message"
    val transport = SseServerTransport(endpoint, sseProducer)

    val individualServer =
        CoralAgentIndividualMcp(uri, transport, session, agentId, maxWaitForMentionsTimeout, extraTools = agent.extraTools)
    session.coralAgentConnections.add(individualServer)

    val transportSessionId = transport.sessionId
    servers[transportSessionId] = individualServer

    val success = session.waitForGroup(agentId, 60000)
    if (success) {
        logger.info { "Agent $agentId successfully waited for group" }
    } else {
        logger.warn { "Agent $agentId failed waiting for group, proceeding anyway.." }
    }

    if (isDevMode) {
        logger.info { "DevMode: Connected to session $sessionId with application $applicationId (waitForAgents=${session.devRequiredAgentStartCount})" }

        if (session.devRequiredAgentStartCount > 0) {
            if (newCount < session.devRequiredAgentStartCount) {

                val success = session.waitForAgentCount(session.devRequiredAgentStartCount, 60000)
                if (success) {
                    logger.info { "DevMode: Successfully waited for ${session.devRequiredAgentStartCount} agents to connect" }
                } else {
                    logger.warn { "DevMode: Timeout waiting for ${session.devRequiredAgentStartCount} agents to connect, proceeding anyway" }
                }
            } else {
                logger.info { "DevMode: Required agent count already reached" }
            }
        }
    }

    individualServer.connect(transport)
    return true
}
