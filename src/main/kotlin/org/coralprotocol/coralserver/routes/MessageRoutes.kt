package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures message-related routes.
 * 
 * @param servers A concurrent map to store server instances by transport session ID
 */
fun Routing.messageRoutes(servers: ConcurrentMap<String, Server>, sessionManager: SessionManager) {
    // Message endpoint with application, privacy key, and session parameters
    post("/{applicationId}/{privacyKey}/{coralSessionId}/message") {
        logger.debug { "Received Message" }

        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val coralSessionId = call.parameters["coralSessionId"]
        val transportSessionId = call.request.queryParameters["sessionId"]

        if (applicationId == null || privacyKey == null || coralSessionId == null || transportSessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
            return@post
        }

        // Get the session
        val session = sessionManager.getSession(coralSessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        // Validate that the application and privacy key match the session
        if (session.applicationId != applicationId || session.privacyKey != privacyKey) {
            call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return@post
        }

        // Get the transport
        val transport = servers[transportSessionId]?.transport as? SseServerTransport
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }

        // Handle the message
        try {
            transport.handlePostMessage(call)
        } catch (e: NoSuchElementException) {
            logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
            call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
        }
    }

    // DevMode message endpoint - no validation
    post("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/message") {
        logger.debug { "Received DevMode Message" }

        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"]
        val transportSessionId = call.request.queryParameters["sessionId"]

        if (applicationId == null || privacyKey == null || sessionId == null || transportSessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
            return@post
        }

        // Get the session. It should exist even in dev mode as it was created in the sse endpoint
        val session = sessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        // Get the transport
        val transport = servers[transportSessionId]?.transport as? SseServerTransport
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }

        // Handle the message
        try {
            transport.handlePostMessage(call)
        } catch (e: NoSuchElementException) {
            logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
            call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
        }
    }
}