package org.coralprotocol.coralserver.gaia

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.config.ApplicationConfig
import org.coralprotocol.coralserver.config.custom
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.orchestrator.AgentRegistry
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.ConfigEntry
import org.coralprotocol.coralserver.orchestrator.Orchestrator
import org.coralprotocol.coralserver.orchestrator.RegistryAgent
import org.coralprotocol.coralserver.orchestrator.runtime.Executable
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.*
import java.io.File
import java.io.IOException

val serverPort: UShort = 5555u
val reportMetadataFile = File(GaiaConfig.multiAgentSystemRootDir, "report_metadata.json")

val planningAgent = AgentType("planning")
val critiqueAgent = AgentType("critique")
val answerFindingAgent = AgentType("answer_finding")
val webAgent = AgentType("web")
val documentProcessingAgent= AgentType("document_processing")
val reasoningCodingAgent = AgentType("reasoning_coding")


/**
 * Helper function to create a registry agent for GAIA
 */
fun createRegistryAgent(
    agentPath: String,
    envList: List<EnvVar> = EnvVars.envVars,
    options: List<ConfigEntry> = EnvVars.configEntries
): RegistryAgent = RegistryAgent(
    Executable(
        listOf("bash", File(GaiaConfig.multiAgentSystemRootDir, "venv.sh").absolutePath, agentPath),
        envList
    ),
    optionsList = options
)

/**
 * Helper function to create an agent registry entry
 */
fun createAgentRegistryEntry(
    agentType: AgentType,
    agentName: String,
    absoluteBasePath: String = GaiaConfig.multiAgentSystemRootDir.absolutePath
): Pair<AgentType, RegistryAgent> =
    agentType to createRegistryAgent("$absoluteBasePath/agents/${agentName}_agent.py")

fun createServerWithRegisteredAgents(): CoralServer {
    val registry = AgentRegistry(
        mapOf(
            createAgentRegistryEntry(planningAgent, "planning"),
            createAgentRegistryEntry(critiqueAgent, "critique"),
            createAgentRegistryEntry(webAgent, "web"),
            createAgentRegistryEntry(documentProcessingAgent, "document_processing"),
            createAgentRegistryEntry(reasoningCodingAgent, "reasoning_coding"),
            createAgentRegistryEntry(answerFindingAgent, "answer_finding"),
        )
    )
    val appConfigLoader = AppConfigLoader.custom(
        AppConfig(
            registry = registry,
            applications = listOf(ApplicationConfig("gaia", "gaia", "gaia application", listOf("public")))
        )
    ).apply { stopWatch() }
    val orchestrator = Orchestrator(appConfigLoader)
    val coralServer = CoralServer(
        devmode = false,
        sessionManager = SessionManager(
            orchestrator,
            serverPort,
        ),
        appConfig = appConfigLoader
    )

    return coralServer
}

fun saveResultToFile(result: GaiaResult) {
    println("Saving result for question: ${result.question}, session: ${result.answerAttempt.sessionId}")
    println("Answer attempt (correct: ${result.isCorrect}): ${result.answerAttempt.answer}")
    println("Justification: ${result.answerAttempt.justification}")
    val questionDir = File(GaiaConfig.gaiaQuestionSet.resultsDir, result.question.taskId)
    val correctnessDir = File(questionDir, if (result.isCorrect) "correct" else "incorrect")
    val resultHash = result.answerAttempt.answer.hashCode().toString(16)
    val resultFile = File(correctnessDir, "$resultHash.json")
    if (!correctnessDir.exists()) {
        correctnessDir.mkdirs()
    }
    resultFile.writeText(Json.encodeToString(result))
}

fun loadAllGaiaResults(): Set<GaiaResult> {
    val resultsDir = GaiaConfig.gaiaQuestionSet.resultsDir
    if (!resultsDir.exists()) {
        return emptySet()
    }

    return resultsDir.walk().filter { it.isFile && it.extension == "json" }.map { file ->
        val content = file.readText()
        Json.decodeFromString<GaiaResult>(content)
    }.toSet()
}

fun saveReportMetadata(metadata: ReportMetadata) {
    try {
        reportMetadataFile.writeText(jsonWithDefaults.encodeToString(metadata))
        println("Report metadata saved to ${reportMetadataFile.absolutePath}")
    } catch (e: IOException) {
        println("Failed to save report metadata: ${e.message}")
    }
}

fun loadReportMetadata(): ReportMetadata? {
    return try {
        if (reportMetadataFile.exists()) {
            val content = reportMetadataFile.readText()
            Json.decodeFromString<ReportMetadata>(content)
        } else {
            null
        }
    } catch (e: Exception) {
        println("Failed to load report metadata: ${e.message}")
        null
    }
}

val jsonWithDefaults = Json {
    prettyPrint = true
    isLenient = true
    encodeDefaults = true
}

@Serializable
data class ReportMetadata(
    val models: List<String>,
    val notes: String,
    val questionSetName: String = GaiaConfig.gaiaQuestionSet.name,
    val maxPassesPerTask: Int = GaiaConfig.maxPassesPerTask,
)

@Serializable
data class Report(
    val reportMetadata: ReportMetadata,
    val results: List<VisualizableResult>,
)

private fun askStdin(prompt: String): String {
    print(prompt)
    return readln().trim()
}

private fun askStdinWithDefault(prompt: String, defaultValue: String? = null): String {
    val promptWithDefault = if (defaultValue != null) "$prompt [Press Enter to use: $defaultValue]" else prompt
    print(promptWithDefault)
    val input = readln().trim()
    return if (input.isEmpty() && defaultValue != null) defaultValue else input
}

private fun askStdinMultiline(prompt: String, defaultValue: String? = null): String {
    val promptWithDefault = if (defaultValue != null)
        "$prompt (type 'END' on a new line to finish, or just press Enter to use previous value)"
    else
        "$prompt (type 'END' on a new line to finish):"

    println(promptWithDefault)

    // If there's a default value and the user just presses Enter, return the default
    val firstLine = readln().trim()
    if (firstLine.isEmpty() && defaultValue != null) {
        return defaultValue
    }

    val lines = mutableListOf<String>()
    if (firstLine != "END") {
        lines.add(firstLine)
    }

    while (true) {
        val line = readln().trim()
        if (line == "END") {
            break
        }
        lines.add(line)
    }
    return lines.joinToString("\n")
}

suspend fun main(args: Array<String>) {
    val server = createServerWithRegisteredAgents()
    val questionSet: GaiaQuestionSet = GaiaConfig.gaiaQuestionSet
    val questions = loadGaiaQuestions(questionSet.metadataFile)

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down server...")
        server.stop()

        ProcessBuilder()
            .command("pkill", "python")
            .start()
    })

    GaiaApplication(server).apply {
        server.start()
        startAnswerServer(wait = false)

        var correctAnswersCount = 0
        var allAnswersCount = 0
        val questionAnswerPairs = mutableListOf<Pair<GaiaQuestion, GaiaAnswerAttempt>>()
        val results = mutableSetOf<GaiaResult>()

        // Load previous report metadata if available
        val previousMetadata = loadReportMetadata()
        val previousModels = previousMetadata?.models?.joinToString(", ")
        val previousNotes = previousMetadata?.notes

        // If previous metadata exists, inform the user
        if (previousMetadata != null) {
            println("Found previous report metadata:")
            println("Models: $previousModels")
            println("Notes: $previousNotes")
        }

        val reportMetadata = ReportMetadata(
            models = askStdinWithDefault("Models in use? (separated by commas)", previousModels).split(",")
                .map { it.trim() }.filter { it.isNotEmpty() },
            notes = askStdinMultiline("Any notes for the report?", previousNotes),
            questionSetName = GaiaConfig.gaiaQuestionSet.setId,
            maxPassesPerTask = GaiaConfig.maxPassesPerTask
        )
        println("Report metadata: $reportMetadata")
        saveReportMetadata(reportMetadata)

        val existingResults = loadAllGaiaResults().filter { it.threads != null }
        val questionsWithoutCorrectAnswers = questions.filter { question ->
            existingResults.none { result ->
                result.question.taskId == question.taskId && result.isCorrect
            }
        }
        val questionsWithCorrectAnswers = questions.filter { question ->
            existingResults.any { result ->
                result.question.taskId == question.taskId && result.isCorrect
            }
        }

        val questionsWithAnyAnswers = questions.filter { question ->
            existingResults.any { result ->
                result.question.taskId == question.taskId
            }
        }
        val questionsWithoutAnswers = questions.toSet().subtract(questionsWithAnyAnswers.toSet())

        fun saveReport() {
            val existingResults = loadAllGaiaResults().filter { it.threads != null }
            val questionsWithoutCorrectAnswers = questions.filter { question ->
                existingResults.none { result ->
                    result.question.taskId == question.taskId && result.isCorrect
                }
            }
            val questionsWithCorrectAnswers = questions.filter { question ->
                existingResults.any { result ->
                    result.question.taskId == question.taskId && result.isCorrect
                }
            }

            val questionsWithAnyAnswers = questions.filter { question ->
                existingResults.any { result ->
                    result.question.taskId == question.taskId
                }
            }
            val questionsWithoutAnswers = questions.toSet().subtract(questionsWithAnyAnswers.toSet())
            val percentOfAnsweredQuestionsCorrect = if (questionsWithAnyAnswers.isNotEmpty()) {
                questionsWithCorrectAnswers.size.toDouble() / questionsWithAnyAnswers.size * 100
            } else {
                0.0
            }
            val uniqueCorrectQuestions = questionsWithCorrectAnswers.distinctBy { it.taskId }.toSet()

            val distinctResultsAllCorrectIncluded =
                uniqueCorrectQuestions + questionsWithoutCorrectAnswers.distinctBy { it.taskId }
            val resultsForReport =
                distinctResultsAllCorrectIncluded.mapNotNull { question -> existingResults.find { it.question.taskId == question.taskId } }
                    .map { VisualizableResult(it) }

            println("Percent of questions with correct answers: $percentOfAnsweredQuestionsCorrect%")
            println("Total questions: ${questions.size}, Questions with correct answers: ${questionsWithCorrectAnswers.size}, Questions without correct answers: ${questionsWithoutCorrectAnswers.size}, Questions with any answers: ${questionsWithAnyAnswers.size}")
            println("Total correct answers: ${existingResults.count { it.isCorrect }}, Total incorrect answers: ${existingResults.count { !it.isCorrect }}")

            val resultsFile = File(
                "report-${reportMetadata.questionSetName}-${
                    reportMetadata.models.sorted().joinToString("-") { it.lowercase() }
                }-${System.currentTimeMillis()}.json"
            )
            resultsFile.writeText(
                jsonWithDefaults.encodeToString(
                    Report(
                        reportMetadata = reportMetadata,
                        results = resultsForReport
                    )
                )
            )
        }
        saveReport()

        val semaphore = Semaphore(4)
        val context = CoroutineScope(SupervisorJob())
        questionsWithoutAnswers.map { question ->
            context.async {
                semaphore.withPermit {
                    try {
                        val startTime = System.currentTimeMillis()
                        val answerDeferred = findAnswer(question)
                        println("Waiting for answer to question: ${question.question}")
                        val answerAttempt = answerDeferred.await()
                        println("${answerAttempt.questionId} took ${(System.currentTimeMillis() - startTime) / 1000}s")

                        val relevantSession = server.sessionManager.getSession(answerAttempt.sessionId)
                            ?: throw IllegalStateException("Session not found for answer attempt: ${answerAttempt.sessionId}")
                        val questionAnswerPair = question to answerAttempt
                        val result = GaiaResult(
                            question = question,
                            answerAttempt = answerAttempt,
                            relevantSession.getAllThreads().map { it.resolve() }
                        )
                        if (result.isCorrect) {
                            println("The answer attempt is correct! (or not given up if test set)")
                            correctAnswersCount++
                        } else {
                            println("The answer attempt is incorrect! Expected: ${question.finalAnswer}, got: ${answerAttempt.answer}")
                        }
                        saveResultToFile(result)
                        results.add(result)
                        questionAnswerPairs.add(questionAnswerPair)
                    } catch (e: Exception) {
                        println("Error while waiting for answer to question: ${question.question}")
                        val result =
                            GaiaResult(
                                question = question,
                                answerAttempt = GaiaAnswerAttempt(
                                    questionId = question.taskId,
                                    answer = "Error [nothing submitted]",
                                    justification = "Error while waiting for answer: ${e.message}",
                                    sessionId = "",
                                    certaintyPercentage = 0
                                ),
                                isOtherError = true
                            )

                        results.add(result)
                        saveResultToFile(result)
                        e.printStackTrace()
                    }

                    allAnswersCount++
                    println("So far, correct answers: $correctAnswersCount, all answers: $allAnswersCount, total questions: ${questions.size}")
                }
            }
        }.awaitAll()

        File("Anemoi/answers.json").writeText(Json.encodeToString(results))
        val json = Json.encodeToString(questionAnswerPairs)
        File("Anemoi/results+${System.currentTimeMillis()}.json").writeText(json)
        saveReport()
        server.stop()
    }
}
