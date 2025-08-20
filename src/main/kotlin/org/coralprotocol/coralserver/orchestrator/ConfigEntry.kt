package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ConfigEntry {
    abstract val type: String
    val name: String
    val description: String?

    @Serializable
    @SerialName("string")
    data class Str(override val name: String, override val description: String? = null, val default: String? = null) :
        ConfigEntry {
        override val type get(): String = "string"
    }

    @Serializable
    @SerialName("number")
    data class Number(
        override val name: String,
        override val description: String? = null,
        val default: Double? = null
    ) : ConfigEntry {
        override val type get(): String = "number"
    }

    val required: Boolean get() = when (val o = this) {
        is Str -> o.default == null
        is Number -> o.default == null
    }

    val defaultAsValue: ConfigValue? get() =
        when (val o = this) {
            is Str -> o.default?.let { ConfigValue.Str(it) }
            is Number -> o.default?.let { ConfigValue.Num(it) }
        }

}