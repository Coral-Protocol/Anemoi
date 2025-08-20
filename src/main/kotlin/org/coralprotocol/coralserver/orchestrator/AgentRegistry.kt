package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable
value class AgentType(private val type: String) {
    override fun toString(): String = type
}

@Serializable(with = AgentRegistrySerializer::class)
data class AgentRegistry(
    val agents: Map<AgentType, RegistryAgent> = mapOf(),
) {
    fun get(agentType: AgentType): RegistryAgent {
        return agents[agentType] ?: throw IllegalArgumentException("AgentDefinition $agentType not found")
    }
}

class AgentRegistrySerializer : KSerializer<AgentRegistry> {
    @OptIn(InternalSerializationApi::class)
    private val delegateSerializer = MapSerializer<AgentType, RegistryAgent>(AgentType::class.serializer(), RegistryAgent::class.serializer())

    // Serial names of descriptors should be unique, this is why we advise including app package in the name.
    override val descriptor = SerialDescriptor("org.coralprotocol.coralserver.AgentRegistry", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: AgentRegistry) {
        encoder.encodeSerializableValue(delegateSerializer, value.agents)
    }

    override fun deserialize(decoder: Decoder): AgentRegistry {
        val agents = decoder.decodeSerializableValue(delegateSerializer)
        return AgentRegistry(agents)
    }
}