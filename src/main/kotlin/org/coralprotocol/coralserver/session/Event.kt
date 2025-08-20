package org.coralprotocol.coralserver.session

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.ResolvedMessage

@Serializable
sealed interface Event {
    @Serializable
    data class AgentRegistered(val agent: Agent) : Event
    @Serializable
    data class AgentReady(val agent: AgentName): Event
    @Serializable
    data class ThreadCreated(val id: String, val name: String, val creatorId: String, val participants: List<String>, val summary: String?): Event
    @Serializable
    data class MessageSent(val threadId: String, val message: ResolvedMessage): Event
}