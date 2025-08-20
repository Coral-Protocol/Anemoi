package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.session.CustomTool

/**
 * Represents an agent in the system.
 */
// TODO: make Agent a data class, when URI's are implemented
@Serializable
class Agent(
    val id: String,
    val description: String = "", // Description of the agent's responsibilities

    val mcpUrl: String?,

    val extraTools: Set<CustomTool> = setOf()
)