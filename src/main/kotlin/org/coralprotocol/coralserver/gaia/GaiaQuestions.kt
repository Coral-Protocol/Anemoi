package org.coralprotocol.coralserver.gaia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File

typealias VerifiedExistingFile = File
typealias GaiaQuestionId = String

@Serializable
data class GaiaQuestion(
    @SerialName("task_id")
    val taskId: GaiaQuestionId,
    @SerialName("Question")
    val question: String,
    @SerialName("Level")
    val level: Int,
    @SerialName("Final answer")
    val finalAnswer: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("Annotator Metadata")
    val annotatorMetadata: AnnotatorMetadata
) {
    // TODO: Change to /test/ or /validation/ based on the context
    @Transient
    val file: VerifiedExistingFile? =
        if (fileName.isNotEmpty()) File("Anemoi/data/gaia/2023/${GaiaConfig.gaiaQuestionSet.setId}/$fileName")
            .takeIf { it.exists() }
            ?: throw IllegalArgumentException("File does not exist: $fileName") else null
}

@Serializable
data class AnnotatorMetadata(
    @SerialName("Steps")
    val steps: String,
    @SerialName("Number of steps")
    val numberOfSteps: String,
    @SerialName("How long did this take?")
    val howLongDidThisTake: String,
    @SerialName("Tools")
    val tools: String,
    @SerialName("Number of tools")
    val numberOfTools: String
)

internal val jsonFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}


fun loadGaiaQuestions(metadataJsonl: File): List<GaiaQuestion> {
    return metadataJsonl.readLines().map { line ->
        jsonFormat.decodeFromString<GaiaQuestion>(line)
    }
}
