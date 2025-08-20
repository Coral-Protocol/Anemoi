package org.coralprotocol.coralserver.gaia

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.models.ResolvedThread

@Serializable
data class GaiaResult(
    val question: GaiaQuestion,
    val answerAttempt: GaiaAnswerAttempt,
    val threads: List<ResolvedThread>? = null,
    val isTimeout: Boolean = false,
    val isOtherError: Boolean = false,
) {
    val isCorrect: Boolean = checkCorrectness()

    private fun checkCorrectness(): Boolean = when {
        question.finalAnswer == "?" -> { // Test set has unknown answers
            println("Total confidence: ${answerAttempt.certaintyPercentage}%")
            !answerAttempt.answer.contains("give up", ignoreCase = true)
        }
        else -> questionScorer(answerAttempt.answer, question.finalAnswer)
    }

    private fun isFloat(element: String): Boolean {
        return try {
            element.toDouble()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun normalizeNumberStr(numberStr: String): Double {
        // Replace common units and commas to allow conversion to float
        val cleaned = numberStr.replace("$", "").replace("%", "").replace(",", "")
        return try {
            cleaned.toDouble()
        } catch (e: NumberFormatException) {
            println("String $numberStr cannot be normalized to number str.")
            Double.POSITIVE_INFINITY
        }
    }

    private fun splitString(s: String, charList: List<String> = listOf(",", ";")): List<String> {
        val pattern = "[${charList.joinToString("")}]".toRegex()
        return s.split(pattern)
    }

    private fun normalizeStr(inputStr: String, removePunct: Boolean = true): String {
        // Remove all white spaces. Required e.g for seagull vs. sea gull
        val noSpaces = inputStr.replace(Regex("\\s"), "")

        // Remove punctuation, if specified
        return if (removePunct) {
            // Remove all punctuation characters
            noSpaces.lowercase().replace(Regex("[^\\w]"), "")
        } else {
            noSpaces.lowercase()
        }
    }

    private fun questionScorer(modelAnswer: String?, groundTruth: String): Boolean {
        val actualModelAnswer = modelAnswer ?: "None"

        // If gt is a number
        if (isFloat(groundTruth)) {
            println("Evaluating $actualModelAnswer as a number.")
            val normalizedAnswer = normalizeNumberStr(actualModelAnswer)
            return normalizedAnswer == groundTruth.toDouble()
        }
        // If gt is a list (contains comma or semicolon)
        else if (groundTruth.contains(",") || groundTruth.contains(";")) {
            println("Evaluating $actualModelAnswer as a comma separated list.")

            val gtElems = splitString(groundTruth)
            val maElems = splitString(actualModelAnswer)

            // Check length is the same
            if (gtElems.size != maElems.size) {
                println("Warning: Answer lists have different lengths, returning false.")
                return false
            }

            // Compare each element as float or str
            val comparisons = gtElems.zip(maElems).map { (gtElem, maElem) ->
                if (isFloat(gtElem)) {
                    val normalizedMaElem = normalizeNumberStr(maElem)
                    normalizedMaElem == gtElem.toDouble()
                } else {
                    // We do not remove punct since comparisons can include punct
                    normalizeStr(maElem, removePunct = false) ==
                            normalizeStr(gtElem, removePunct = false)
                }
            }
            return comparisons.all { it }
        }
        // If gt is a string
        else {
            println("Evaluating $actualModelAnswer as a string.")
            return normalizeStr(actualModelAnswer) == normalizeStr(groundTruth)
        }
    }
}