package org.coralprotocol.coralserver.config

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.AgentRegistry
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.RegistryAgent

// TODO: Applications are a work in progress. This is safe to ignore for now.

/**
 * Main application configuration.
 */
@Serializable
data class AppConfig(
    val applications: List<ApplicationConfig> = emptyList(),
    val applicationSource: ApplicationSourceConfig? = null,
    val registry: AgentRegistry? = null,
)

/**
 * Configuration for an application.
 */
@Serializable
data class ApplicationConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val privacyKeys: List<String> = emptyList()
)

/**
 * Configuration for application source (for future use).
 */
@Serializable
data class ApplicationSourceConfig(
    val type: String,
    val url: String? = null,
    val refreshIntervalSeconds: Int = 3600
)