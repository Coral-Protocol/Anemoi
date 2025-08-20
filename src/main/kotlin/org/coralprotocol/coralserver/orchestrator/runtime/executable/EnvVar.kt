package org.coralprotocol.coralserver.orchestrator.runtime.executable

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.ConfigValue

@Serializable
data class EnvVar(
    val name: String? = null,
    val value: String? = null,
    val from: String? = null,

    val option: String? = null,
) {
    // TODO (alan): bake this validation into the type system
    //              EnvVar should be a sum type of 'name/from', 'option' & 'name/value'
    fun validate() {
        if (option != null && (from != null || value != null || name != null)) {
            throw IllegalArgumentException("'option' key is shorthand for 'name' & 'from', it must be used on its own")
        }
        if (name != null && (value == null && from == null)) {
            throw IllegalArgumentException("'value' or 'from' must be provided")
        }
        if (from != null && value != null) {
            throw IllegalArgumentException("'from' and 'value' are mutually exclusive")
        }
        if (name == null && value == null && from == null && option == null) {
            throw IllegalArgumentException("Invalid environment variable definition")
        }
    }

    fun resolve(options: Map<String, ConfigValue>): Pair<String, String?> {
        if (option != null) {
            val opt = options[option] ?: throw IllegalArgumentException("Undefined option '$option'")
            return Pair(option, opt.toString())
        }
        val name = name ?: throw IllegalArgumentException("name not provided")
        if(from != null) {
            val opt = options[from] ?: throw IllegalArgumentException("Undefined option '$from'")
            return Pair(from, opt.toString())
        }
        if(value != null) {
            return Pair(name, value)
        }
        throw IllegalArgumentException("Invalid environment variable definition")
    }
}