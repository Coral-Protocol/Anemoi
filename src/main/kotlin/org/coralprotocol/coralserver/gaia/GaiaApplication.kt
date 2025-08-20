package org.coralprotocol.coralserver.gaia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.AgentGraphRequest
import org.coralprotocol.coralserver.session.AgentName
import org.coralprotocol.coralserver.session.CreateSessionRequest
import org.coralprotocol.coralserver.session.CreateSessionResponse
import org.coralprotocol.coralserver.session.GraphAgentRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Application that runs the whole GAIA benchmark.
 *
 * A server is started for the answering agent to send answers to via HTTP.
 * (It is expected to do this through tool calls)
 */
class GaiaApplication(val server: CoralServer) {

    val client = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
    }

    // Channels for receiving Gaia answers
    val answerChannels: ConcurrentMap<GaiaQuestionId, Flow<GaiaAnswerAttempt>> = ConcurrentHashMap()
    private val embeddedAnswerServer =
        embeddedServer(CIO, host = server.host, port = 12081, watchPaths = listOf("classes")) {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                post("answers") {
                    val answerAttempt = call.receive<GaiaAnswerAttempt>()
                    val questionId = answerAttempt.questionId

                    answerAttempt.session = server.sessionManager.getSession(answerAttempt.sessionId)
                        ?: throw IllegalStateException("Session not found for answer attempt: ${answerAttempt.sessionId}")
                    // end the session if it exists
                    server.sessionManager.getSession(questionId)?.coralAgentConnections?.forEach {
                        it.closeTransport()
                        println("Closed transport for session: $questionId for agent: ${it.connectedAgentId}")
                    }
                    // Store the answer attempt in the channel
                    val channel = answerChannels.computeIfAbsent(questionId) {
                        MutableSharedFlow(extraBufferCapacity = 1)
                    }

                    (channel as MutableSharedFlow<GaiaAnswerAttempt>).emit(answerAttempt)

                    // Respond with OK status
                    call.respond(HttpStatusCode.Companion.OK)
                }
            }
        }
    private var isStarted = false

    fun startAnswerServer(wait: Boolean = true) {
        isStarted = true
        embeddedAnswerServer.start(wait = wait)
    }

    val waitingForAnswerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    suspend fun findAnswer(question: GaiaQuestion): Deferred<GaiaAnswerAttempt> {
        if (!isStarted) {
            throw IllegalStateException("Server is not started. Call start() before finding an answer.")
        }

        val address = "http://localhost:${server.port}"
        val sessionPostResponse = client.post("$address/sessions") {
            contentType(ContentType.Application.Json)
            setBody(
                creationSessionRequest(question)
            )
        }

        // Ensure there is a flow
        val answerChannel = answerChannels.computeIfAbsent(question.taskId) {
            MutableSharedFlow(extraBufferCapacity = 1)
        }

        if (sessionPostResponse.status != HttpStatusCode.Companion.OK) {
            throw IllegalStateException("Failed to create session: ${sessionPostResponse.status}")
        }
        val responseBody = sessionPostResponse.body<CreateSessionResponse>()
        val completableDeferred = CompletableDeferred<GaiaAnswerAttempt>()


        waitingForAnswerScope.launch {
            launch {
                delay(40 * 60 * 1000)
                try {
                    completableDeferred.complete(createIncompleteAnswer(question, responseBody.sessionId))
                    endSession(responseBody)
                } catch (e: Exception) {
                    println("Failed to end session after timeout: ${e.message}")
                }
            }
            answerChannel.collect { answerAttempt ->
                if (answerAttempt.questionId != question.taskId) {
                    throw IllegalStateException("Received answer for a different question: ${answerAttempt.questionId} != ${question.taskId}")
                }
                completableDeferred.complete(answerAttempt)
                endSession(responseBody)
            }
        }

        return completableDeferred
    }

    /**
     * Create a complete-as-possible attempt for a question.  Called when waiting for an answer times out
     */
    private fun createIncompleteAnswer(question: GaiaQuestion, sessionId: String): GaiaAnswerAttempt {
        return GaiaAnswerAttempt(
            questionId = question.taskId,
            answer = "[Timeout [nothing submitted]]",
            justification = "Timeout while waiting for answer",
            certaintyPercentage = 0,
            sessionId = sessionId
        )
    }

    /**
     * Helper function to create common options map for agent configuration
     */
    private fun createCommonOptionsMap(
        question: GaiaQuestion,
        customValues: Map<String, String> = emptyMap()
    ): Map<String, JsonPrimitive> {
        val questionWithFile = if (question.file != null) {
            buildString {
                append("${question.question}\n relevant file: ${question.file.absolutePath}\n")
                append("Note that even if you don't have tools to work with files, others in your team will. Make sure you're all working around this file.")
            }
        } else {
            question.question
        }

        // Start with the base environment variables
        val optionsMap = EnvVars.definitions
            .filter { it.isApiKey }
            .associate { it.name to JsonPrimitive(customValues[it.name] ?: EnvVars.getValue(it.name)) }
            .toMutableMap()

        // Add task-specific variables
        optionsMap["TASK_INSTRUCTION"] = JsonPrimitive(questionWithFile)
        optionsMap["TASK_ID"] = JsonPrimitive(question.taskId)
        optionsMap["AGENT_WORKING_DIRECTORY"] =
            JsonPrimitive(GaiaConfig.getOrCreateSessionWorkingDirectory(question.taskId).absolutePath)
        optionsMap["GOOGLE_SEARCH_CACHE_LOCATION"] = JsonPrimitive(
            GaiaConfig.multiAgentSystemRootDir.resolve("google_search_cache.json").absolutePath
        )
        optionsMap["GOOGLE_SEARCH_CACHE_PLAIN_LOCATION"] = JsonPrimitive(
            GaiaConfig.multiAgentSystemRootDir.resolve("google_search_cache_plain.json").absolutePath
        )

        return optionsMap
    }

    /**
     * Helper function to create agent instance reference
     */
    private fun getAgentInstanceReference(
        commonOptions: Map<String, JsonPrimitive>,
        name: String,
        agentType: AgentType,
        blocking: Boolean = true
    ): Pair<AgentName, GraphAgentRequest.Local> =
        AgentName(name) to GraphAgentRequest.Local(
            agentType,
            blocking = blocking,
            options = commonOptions
        )

    private fun creationSessionRequest(question: GaiaQuestion): CreateSessionRequest {
        val commonOptions = createCommonOptionsMap(question)

        // Create agent instances using the helper method
        return CreateSessionRequest(
            "gaia", "gaia-1", "public", AgentGraphRequest(
                agents = hashMapOf(
                    getAgentInstanceReference(commonOptions, "planning", planningAgent),
                    getAgentInstanceReference(commonOptions, "critique", critiqueAgent),
                    getAgentInstanceReference(commonOptions, "web", webAgent),
                    getAgentInstanceReference(commonOptions, "document_processing", documentProcessingAgent),
                    getAgentInstanceReference(commonOptions, "reasoning_coding", reasoningCodingAgent),
                    getAgentInstanceReference(commonOptions, "answer_finding", answerFindingAgent),
                ),
                links = setOf(
                    setOf(
                        "planning", "critique", "web", "document_processing", "reasoning_coding", "answer_finding"
                    )
                )
            )
        )
    }
}

private suspend fun GaiaApplication.endSession(responseBody: CreateSessionResponse) {
    println("Ending session with ID: ${responseBody.sessionId}")
    delay(100) // Ensure the log has time to be printed before errors from agent termination show
    // Wait for the answer attempt to be emitted
    server.sessionManager.getSession(responseBody.sessionId)?.let { session ->
        server.sessionManager.orchestrator.destroy(session.id)
    } ?: throw IllegalStateException("Session not found: ${responseBody.sessionId}")

}