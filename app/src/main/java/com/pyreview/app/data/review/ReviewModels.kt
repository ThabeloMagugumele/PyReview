package com.pyreview.app.data.review

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class Severity(val label: String) {
    GOOD("Good"),
    WARNING("Warning"),
    ERROR("Error"),
}

@Serializable
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
}

@Serializable
data class Finding(
    val id: String = "",
    val severity: Severity,
    val category: String,
    val title: String,
    @SerialName("line_start") val lineStart: Int,
    @SerialName("line_end") val lineEnd: Int,
    val explanation: String,
    @SerialName("learn_more_topic") val learnMoreTopic: String = "",
    val confidence: Confidence = Confidence.HIGH,
) {
    val lineLabel: String
        get() = when {
            lineStart <= 0 -> "Whole file"
            lineEnd > lineStart -> "Lines $lineStart–$lineEnd"
            else -> "Line $lineStart"
        }

    val categoryLabel: String
        get() = CATEGORY_LABELS[category] ?: category.lowercase().replaceFirstChar { it.uppercase() }

    val confidenceNotice: String?
        get() = when (confidence) {
            Confidence.HIGH -> null
            Confidence.MEDIUM -> "PyReview is fairly sure about this one, but not 100%."
            Confidence.LOW -> "PyReview isn't sure about this one, so double-check it before you change anything."
        }

    companion object {
        val CATEGORY_LABELS = mapOf(
            "SYNTAX" to "Syntax",
            "BUG" to "Bug",
            "LOGIC" to "Logic",
            "SECURITY" to "Security",
            "QUALITY" to "Code quality",
            "BEST_PRACTICE" to "Best practice",
            "STANDARDS" to "Coding standard",
        )
    }
}

@Serializable
data class ReviewResult(
    val summary: String,
    val findings: List<Finding>,
    val quickChecks: Boolean = false,
) {
    fun tidied(lineCount: Int): ReviewResult = copy(
        findings = findings
            .map { f ->
                val start = f.lineStart.coerceIn(0, lineCount)
                val end = if (start == 0) 0 else f.lineEnd.coerceIn(start, lineCount)
                f.copy(lineStart = start, lineEnd = end)
            }
            .sortedWith(compareBy({ -it.severity.ordinal }, { if (it.lineStart == 0) Int.MAX_VALUE else it.lineStart }))
            .mapIndexed { i, f -> f.copy(id = "f${i + 1}") },
    )
}

object FindingsJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(findings: List<Finding>): String = json.encodeToString(ListSerializer(Finding.serializer()), findings)

    fun decode(text: String): List<Finding> =
        runCatching { json.decodeFromString(ListSerializer(Finding.serializer()), text) }.getOrDefault(emptyList())
}
