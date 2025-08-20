package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.session.AgentGraph
import org.coralprotocol.coralserver.session.AgentName
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.session.SessionManager
import org.coralprotocol.coralserver.session.CreateSessionRequest
import org.coralprotocol.coralserver.session.CreateSessionResponse
import org.coralprotocol.coralserver.session.GraphAgentRequest

private val logger = KotlinLogging.logger {}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

/**
 * Configures session-related routes.
 */
fun Routing.sessionRoutes(appConfig: AppConfigLoader, sessionManager: SessionManager, devMode: Boolean) {
    // Session creation endpoint

    post("/sessions") {
        try {
            val request = call.receive<CreateSessionRequest>()

            // Validate application and privacy key
            if (!devMode && !appConfig.isValidApplication(request.applicationId, request.privacyKey)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid application ID or privacy key")
                return@post
            }


            val agentGraph = request.agentGraph?.let { it ->
                val agents = it.agents;
                val registry = appConfig.config.registry ?: return@let null

                val unknownAgents =
                    it.links.map { set -> set.filter { agent -> !it.agents.containsKey(AgentName(agent)) } }.flatten()
                if (unknownAgents.isNotEmpty()) {
                    throw IllegalArgumentException("Unknown agent names in links: ${unknownAgents.joinToString()}")
                }

                AgentGraph(
                    tools = it.tools,
                    links = it.links,
                    agents = agents.mapValues { agent ->
                        when (val agentReq = agent.value) {
                            is GraphAgentRequest.Local -> {
                                val agentDef = registry.get(agentReq.agentType)

                                val missing = agentDef.options.filter { option ->
                                    option.value.required && !agentReq.options.containsKey(option.key)
                                }
                                if (missing.isNotEmpty()) {
                                    throw IllegalArgumentException("Agent '${agent.key}' Missing required options: ${missing.keys.joinToString()}")
                                }

                                val defaultOptions =
                                    agentDef.options.mapValues { option -> option.value.defaultAsValue }
                                        .filterNotNullValues()

                                val setOptions = agentReq.options.mapValues { option ->
                                    val realOption = agentDef.options[option.key]
                                        ?: throw IllegalArgumentException("Unknown option '${option.key}'")
                                    val value = ConfigValue.tryFromJson(option.value)
                                        ?: throw IllegalArgumentException("Agent '${agent.key}' given invalid type for option '${option.key} - expected ${realOption.type}'")
                                    if (value.type != realOption.type) {
                                        throw IllegalArgumentException("Agent '${agent.key}' given invalid type for option '${option.key}' - expected ${realOption.type}")
                                    }
                                    value
                                }

                                GraphAgent.Local(
                                    blocking = agentReq.blocking ?: true,
                                    agentType = agentReq.agentType,
                                    extraTools = agentReq.tools,
                                    systemPrompt = agentReq.systemPrompt,
                                    options = defaultOptions + setOptions
                                )
                            }

                            else -> TODO("(alan) remote agent option resolution")
                        }
                    }
                )
            }

            // TODO(alan): actually limit agent communicating using AgentGraph.links
            // Create a new session
            val session = when (request.sessionId != null && devMode) {
                true -> {
                    sessionManager.createSessionWithId(
                        request.sessionId,
                        request.applicationId,
                        request.privacyKey,
                        agentGraph
                    )
                }

                false -> {
                    sessionManager.createSession(request.applicationId, request.privacyKey, agentGraph)
                }
            }

            // Return the session details
            call.respond(
                CreateSessionResponse(
                    sessionId = session.id,
                    applicationId = session.applicationId,
                    privacyKey = session.privacyKey
                )
            )

            logger.info { "Created new session ${session.id} for application ${session.applicationId}" }
        } catch (e: Exception) {
            logger.error(e) { "Error creating session" }
            call.respond(HttpStatusCode.InternalServerError, "Error creating session: ${e.message}")
        }
    }
}