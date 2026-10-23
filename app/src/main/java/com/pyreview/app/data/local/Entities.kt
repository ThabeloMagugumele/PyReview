package com.pyreview.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class UserRole(val label: String) {
    DEVELOPER("Developer"),
    STANDARDS_MANAGER("Standards manager"),
}

enum class SubmissionMethod(val label: String) {
    TEXT("Typed or pasted"),
    FILE(".py file"),
    IMAGES("Photos"),
}

@Entity(tableName = "users")
data class UserProfile(
    @PrimaryKey val userId: String,
    val fullName: String,
    val email: String,
    val authProvider: String,
    val organizationId: String,
    val organizationName: String,
    val departmentId: String,
    val departmentName: String,
    val userRole: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val role: UserRole get() = runCatching { UserRole.valueOf(userRole) }.getOrDefault(UserRole.DEVELOPER)
}

data class CodingStandard(
    val standardId: String,
    val organizationId: String,
    val departmentId: String,
    val standardName: String,
    val description: String,
    val version: String,
    val content: String,
    val uploadedBy: String,
    val createdAt: Long,
) {
    val isOrganisationWide: Boolean get() = departmentId.isEmpty()
}

@Entity(tableName = "code_submissions", indices = [Index("userId")])
data class CodeSubmission(
    @PrimaryKey val submissionId: String,
    val userId: String,
    val organizationId: String,
    val departmentId: String,
    val fileName: String,
    val submissionMethod: String,
    val code: String,
    val reconstructedCode: String?,
    val imageCount: Int,
    val uncertainLineCount: Int,
    val status: String,
    val submittedAt: Long,
) {
    val method: SubmissionMethod get() = runCatching { SubmissionMethod.valueOf(submissionMethod) }.getOrDefault(SubmissionMethod.TEXT)
}

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = CodeSubmission::class,
            parentColumns = ["submissionId"],
            childColumns = ["submissionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("submissionId"), Index("userId")],
)
data class Review(
    @PrimaryKey val reviewId: String,
    val submissionId: String,
    val userId: String,
    val standardsUsed: String,
    val reviewStatus: String,
    val summary: String,
    val findingsJson: String,
    val goodCount: Int,
    val warningCount: Int,
    val errorCount: Int,
    val requestedAt: Long,
    val completedAt: Long,
    val syncedToCloud: Boolean,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

data class ReviewWithSubmission(
    @Embedded val review: Review,
    @Relation(parentColumn = "submissionId", entityColumn = "submissionId")
    val submission: CodeSubmission,
)
