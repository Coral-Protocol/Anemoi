package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.collections.*
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.orchestrator.toPublic
import org.coralprotocol.coralserver.session.SessionManager


private val logger = KotlinLogging.logger {}

/**
 * Configures public routes (for other servers to interface with).
 */
fun Routing.publicRoutes(appConfig: AppConfigLoader, sessionManager: SessionManager) {
    get("/api/v1/registry") {
        val registry = appConfig.config.registry?.agents?.map { entry -> entry.value.toPublic(entry.key.toString()) } ?: listOf();
        call.respond(HttpStatusCode.OK, registry)
    }

    // TODO: this should probably be protected (only for debug maybe)
    get("/api/v1/sessions") {
        val sessions = sessionManager.getAllSessions()
        call.respond(HttpStatusCode.OK, sessions.map { it.id })
    }
}