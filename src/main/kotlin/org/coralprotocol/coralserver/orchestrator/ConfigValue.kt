package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

sealed interface ConfigValue {
    abstract val type: String

    data class Str(val value: String) : ConfigValue {
        override val type get(): String = "string"
        override fun toString(): String {
            return value
        }
    }

    data class Num(val value: Double) : ConfigValue {
        override val type get(): String = "number"
        override fun toString(): String {
            return value.toString()
        }
    }

    companion object {
        fun tryFromJson(value: JsonPrimitive): ConfigValue? {
            if (value.isString) {
                return Str(value.content)
            }
            return value.doubleOrNull?.let { Num(it) }
        }
    }
}