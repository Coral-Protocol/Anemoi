package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import com.chrynan.uri.core.pathSegments

fun getCoralSystemEnvs(
    params: RuntimeParams,
    coralConnectionUrl: Uri,
    orchestrationRuntime: String
): Map<String, String> {
    // Confirm last segment is "sse" to ensure it's a valid Coral connection URL
    if (coralConnectionUrl.pathSegments.isEmpty() || coralConnectionUrl.pathSegments.last() != "sse") {
        throw IllegalArgumentException("Coral connection URL must end with '/sse'")
    }
    val sessionId = coralConnectionUrl.pathSegments.dropLast(1).lastOrNull()
        ?: throw IllegalArgumentException("Coral connection URL must contain a session ID in the path")
    return listOfNotNull(
        "CORAL_CONNECTION_URL" to coralConnectionUrl.toUriString().value,
        "CORAL_AGENT_ID" to params.agentName,
        "CORAL_ORCHESTRATION_RUNTIME" to orchestrationRuntime,
        "CORAL_SESSION_ID" to sessionId,
        "CORAL_SSE_URL" to with(coralConnectionUrl) {
            "${scheme}://$host:$port$path"
        },
        params.systemPrompt?.let { "CORAL_PROMPT_SYSTEM" to it }
    ).toMap()
}