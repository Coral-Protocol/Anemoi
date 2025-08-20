package org.coralprotocol.coralserver.gaia

import org.coralprotocol.coralserver.orchestrator.ConfigEntry
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar

/**
 * Central registry of all environment variables used by agents
 */
object EnvVars {
    // Define all environment variables here
    data class EnvVarDef(
        val name: String,
        val description: String,
        val defaultValue: String? = null,
        val isApiKey: Boolean = false
    )

    // List of all environment variables
    val definitions = listOf(
        EnvVarDef("OPENAI_API_KEY", "OpenAI API Key", null, true),
        EnvVarDef("JINA_API_KEY", "Jina API key", null, true),
        EnvVarDef("JINA_PROXY_URL", "Jina proxy url." +
                " This is optional but prevents rate limits", null, true),
        EnvVarDef("GOOGLE_API_KEY", "Google API Key", null, true),
        EnvVarDef("FIRECRAWL_API_KEY", "Google API Key", null, true),
        EnvVarDef("CHUNKR_API_KEY", "Chunkr api key.", null, true),
        EnvVarDef("SEARCH_ENGINE_ID", "Search Engine ID", null, true),
        EnvVarDef("OPENROUTER_API_KEY", "OpenRouter API Key", null, true),
        EnvVarDef("TASK_INSTRUCTION", "The task to instruct the", "", false),
        EnvVarDef("TASK_ID", "The gaia question ID", "", false),
        EnvVarDef("AGENT_WORKING_DIRECTORY",
            "The working directory for the agent, where it can store files and results",
            null,
            false
        ),
        EnvVarDef("GOOGLE_SEARCH_CACHE_LOCATION",
            "Location of the Google search cache directory",
            null,
            false
        ),
        EnvVarDef("GOOGLE_SEARCH_CACHE_PLAIN_LOCATION",
            "Location of the Google search cache directory",
            null,
            false
        ),
    )

    // Map of environment variable names to their values
    val values: Map<String, String> by lazy {
        definitions.associate { it.name to getEnvVarValue(it.name) }
    }

    // Get value for a specific environment variable
    fun getValue(name: String): String = values[name] ?: ""

    // Helper function to get environment variable value
    private fun getEnvVarValue(name: String): String = System.getenv(name) ?: ""

    // Generate ConfigEntry list for registry options
    val configEntries: List<ConfigEntry> by lazy {
        definitions.map { ConfigEntry.Str(it.name, it.description, it.defaultValue) }
    }

    // Generate EnvVar list for registry environment
    val envVars: List<EnvVar> by lazy {
        definitions.map {
            EnvVar(it.name, it.name, if (it.isApiKey) getValue(it.name) else it.defaultValue ?: "", it.name)
        }
    }
}