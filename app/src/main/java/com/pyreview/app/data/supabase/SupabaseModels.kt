package com.pyreview.app.data.supabase

import com.pyreview.app.data.local.CodeSubmission
import com.pyreview.app.data.local.CodingStandard
import com.pyreview.app.data.local.Review
import com.pyreview.app.data.local.ReviewWithSubmission
import com.pyreview.app.data.review.Confidence
import com.pyreview.app.data.review.Finding
import com.pyreview.app.data.review.FindingsJson
import com.pyreview.app.data.review.Severity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    @SerialName("auth_provider") val authProvider: String = "email",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class OrganizationRow(val id: String, val name: String)

@Serializable
data class DepartmentRow(val id: String, val name: String, val organizations: OrganizationRow)

@Serializable
data class MembershipRow(val role: String, val departments: DepartmentRow)

@Serializable
data class StandardRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("department_id") val departmentId: String? = null,
    val title: String,
    val description: String = "",
    val version: String = "",
    val content: String,
    @SerialName("uploaded_by") val uploadedBy: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toEntity() = CodingStandard(
        standardId = id,
        organizationId = organizationId,
        departmentId = departmentId.orEmpty(),
        standardName = title,
        description = description,
        version = version,
        content = content,
        uploadedBy = uploadedBy.orEmpty(),
        createdAt = parseTime(createdAt),
    )
}

@Serializable
data class StandardWrite(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("department_id") val departmentId: String?,
    val title: String,
    val description: String,
    val version: String,
    val content: String,
    @SerialName("uploaded_by") val uploadedBy: String,
)

@Serializable
data class SubmissionInsert(
    @SerialName("department_id") val departmentId: String?,
    @SerialName("file_name") val fileName: String,
    @SerialName("submission_method") val submissionMethod: String,
    val code: String,
    @SerialName("reconstructed_code") val reconstructedCode: String?,
    @SerialName("image_count") val imageCount: Int,
    @SerialName("uncertain_line_count") val uncertainLineCount: Int,
)

@Serializable
data class SubmissionRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("department_id") val departmentId: String? = null,
    @SerialName("file_name") val fileName: String,
    @SerialName("submission_method") val submissionMethod: String,
    val code: String,
    @SerialName("reconstructed_code") val reconstructedCode: String? = null,
    @SerialName("image_count") val imageCount: Int = 0,
    @SerialName("uncertain_line_count") val uncertainLineCount: Int = 0,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FindingRow(
    val position: Int,
    val severity: String,
    val category: String,
    val title: String,
    @SerialName("line_start") val lineStart: Int = 0,
    @SerialName("line_end") val lineEnd: Int = 0,
    val explanation: String,
    @SerialName("learn_more_topic") val learnMoreTopic: String = "",
    val confidence: String = "HIGH",
)

@Serializable
data class ReviewRow(
    val id: String,
    @SerialName("submission_id") val submissionId: String,
    @SerialName("user_id") val userId: String,
    val status: String,
    val summary: String = "",
    @SerialName("standards_used") val standardsUsed: String = "",
    val model: String = "",
    @SerialName("requested_at") val requestedAt: String,
    @SerialName("completed_at") val completedAt: String,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("review_findings") val findings: List<FindingRow> = emptyList(),
    val submissions: SubmissionRow,
) {
    fun toEntity(): ReviewWithSubmission {
        val findings = findings.sortedBy { it.position }.map { f ->
            Finding(
                id = "f${f.position}",
                severity = runCatching { Severity.valueOf(f.severity) }.getOrDefault(Severity.WARNING),
                category = f.category,
                title = f.title,
                lineStart = f.lineStart,
                lineEnd = f.lineEnd,
                explanation = f.explanation,
                learnMoreTopic = f.learnMoreTopic,
                confidence = runCatching { Confidence.valueOf(f.confidence) }.getOrDefault(Confidence.MEDIUM),
            )
        }
        val s = submissions
        return ReviewWithSubmission(
            review = Review(
                reviewId = id,
                submissionId = submissionId,
                userId = userId,
                standardsUsed = standardsUsed,
                reviewStatus = status,
                summary = summary,
                findingsJson = FindingsJson.encode(findings),
                goodCount = findings.count { it.severity == Severity.GOOD },
                warningCount = findings.count { it.severity == Severity.WARNING },
                errorCount = findings.count { it.severity == Severity.ERROR },
                requestedAt = parseTime(requestedAt),
                completedAt = parseTime(completedAt),
                syncedToCloud = true,
                archived = archivedAt != null,
            ),
            submission = CodeSubmission(
                submissionId = s.id,
                userId = s.userId,
                organizationId = "",
                departmentId = s.departmentId.orEmpty(),
                fileName = s.fileName,
                submissionMethod = s.submissionMethod,
                code = s.code,
                reconstructedCode = s.reconstructedCode,
                imageCount = s.imageCount,
                uncertainLineCount = s.uncertainLineCount,
                status = s.status,
                submittedAt = parseTime(s.createdAt),
            ),
        )
    }

    companion object {
        const val SELECT = "id, submission_id, user_id, status, summary, standards_used, model, requested_at, " +
            "completed_at, archived_at, review_findings(position, severity, category, title, line_start, line_end, " +
            "explanation, learn_more_topic, confidence), submissions(id, user_id, department_id, file_name, " +
            "submission_method, code, reconstructed_code, image_count, uncertain_line_count, status, created_at)"
    }
}

@Serializable
data class ReviewIdResponse(@SerialName("review_id") val reviewId: String)

@Serializable
data class PhotoLine(val text: String, val uncertain: Boolean = false)

@Serializable
data class PhotoPage(val lines: List<PhotoLine> = emptyList())

@Serializable
data class PhotoPagesResponse(val pages: List<PhotoPage> = emptyList())

@Serializable
data class ErrorResponse(val error: String = "", val message: String? = null, val scope: String? = null)

@Serializable
data class DepartmentMember(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
) {
    val isAdmin: Boolean get() = role == "admin"
}

fun parseTime(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
