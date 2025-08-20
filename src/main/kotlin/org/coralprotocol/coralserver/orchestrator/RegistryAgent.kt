package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.toMapOnDuplicate

@Serializable
data class RegistryAgent(
    val runtime: AgentRuntime,
    @SerialName("options")
    private val optionsList: List<ConfigEntry>
) {
    @Transient
    val options = optionsList.map { it.name to it }
        .toMapOnDuplicate { throw IllegalArgumentException("Duplicate options ${it.joinToString(",")}") }
}

@Serializable
data class PublicRegistryAgent(
    val id: String,
    val options: Map<String, ConfigEntry>
)

fun RegistryAgent.toPublic(id: String): PublicRegistryAgent = PublicRegistryAgent(
    id = id,
    options = options
)