package org.coralprotocol.coralserver.debug

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcptools.CreateThreadInput
import org.coralprotocol.coralserver.mcptools.SendMessageInput
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.ResolvedThread
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.session.SessionManager


private val logger = KotlinLogging.logger {}

@Serializable
sealed interface SocketEvent {
    @Serializable
    @SerialName("DebugAgentRegistered")
    data class DebugAgentRegistered(val id: String) : SocketEvent

    @Serializable
    @SerialName("ThreadList")
    data class ThreadList(val threads: List<ResolvedThread>) : SocketEvent

    @Serializable
    @SerialName("AgentList")
    data class AgentList(val agents: List<Agent>) : SocketEvent
}

fun Routing.debugRoutes(sessionManager: SessionManager) {
    webSocket("/debug/{applicationId}/{privacyKey}/{coralSessionId}/") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        // TODO (alan): proper appId/privacyKey based lookups when session manager is updated
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")

        val timeout = call.parameters["timeout"]?.toLongOrNull() ?: 1000

        val session = sessionManager.waitForSession(sessionId, timeout);
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@webSocket
        }

        val debugId = session.registerDebugAgent()
        sendSerialized<SocketEvent>(SocketEvent.DebugAgentRegistered(id = debugId.id))

        sendSerialized<SocketEvent>(SocketEvent.ThreadList(session.getAllThreads().map { it.resolve() }))
        sendSerialized<SocketEvent>(SocketEvent.AgentList(session.getAllAgents(false)))

        session.events.collect { evt ->
            logger.debug { "Received evt: $evt" }
            sendSerialized(evt)
        }
    }

    webSocket("/debug/{applicationId}/{privacyKey}/{coralSessionId}/{agentId}/logs") {
        val applicationId = call.parameters["applicationId"] ?: throw IllegalArgumentException("Missing applicationId")
        val privacyKey = call.parameters["privacyKey"] ?: throw IllegalArgumentException("Missing privacyKey")
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")
        val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("Missing agentId")

        val bus = sessionManager.orchestrator.getBus(sessionId, agentId) ?: run {
            call.respond(HttpStatusCode.NotFound, "Agent not found")
            return@webSocket;
        };

        bus.events.collect { evt ->
            logger.debug { "Received evt: $evt" }
            sendSerialized(evt)
        }
    }


    post("/debug/{applicationId}/{privacyKey}/{coralSessionId}/{debugAgentId}/thread/") {
        // TODO (alan): proper appId/privacyKey based lookups when session manager is updated
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")
        val debugAgentId = call.parameters["debugAgentId"] ?: throw IllegalArgumentException("Missing debugAgentId")

        val session = sessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        try {
            val request = call.receive<CreateThreadInput>()
            val thread = session.createThread(
                name = request.threadName,
                creatorId = debugAgentId,
                participantIds = request.participantIds
            )

            call.respond(thread.resolve())
        } catch (e: Exception) {
            logger.error(e) { "Error while creating thread" }
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }
    post("/debug/{applicationId}/{privacyKey}/{coralSessionId}/{debugAgentId}/thread/sendMessage/") {
        // TODO (alan): proper appId/privacyKey based lookups when session manager is updated
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")
        val debugAgentId = call.parameters["debugAgentId"] ?: throw IllegalArgumentException("Missing debugAgentId")

        val session = sessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        try {
            val request = call.receive<SendMessageInput>()
            val message = session.sendMessage(
                threadId = request.threadId,
                senderId = debugAgentId,
                content = request.content,
                mentions = request.mentions
            )

            call.respond(message.resolve())
        } catch (e: Exception) {
            logger.error(e) { "Error while sending message" }
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }

}