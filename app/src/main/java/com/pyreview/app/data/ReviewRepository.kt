package com.pyreview.app.data

import com.pyreview.app.data.local.CodeSubmission
import com.pyreview.app.data.local.CodingStandard
import com.pyreview.app.data.local.Review
import com.pyreview.app.data.local.ReviewDao
import com.pyreview.app.data.local.ReviewWithSubmission
import com.pyreview.app.data.local.SubmissionMethod
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.review.QuickChecks
import com.pyreview.app.data.review.FindingsJson
import com.pyreview.app.data.review.ReviewResult
import com.pyreview.app.data.review.Severity
import com.pyreview.app.data.supabase.RemoteReviewService
import com.pyreview.app.util.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

data class ReviewRequest(
    val fileName: String,
    val code: String,
    val method: SubmissionMethod,
    val reconstructedCode: String? = null,
    val imageCount: Int = 0,
    val uncertainLineCount: Int = 0,
)

class ReviewRepository(
    private val reviews: ReviewDao,
    private val remote: RemoteReviewService,
    private val standards: suspend (UserProfile) -> List<CodingStandard>,
    private val network: NetworkMonitor,
) {
    private val inFlight = Mutex()

    fun history(userId: String): Flow<List<ReviewWithSubmission>> = reviews.observeHistory(userId)

    fun review(reviewId: String, userId: String): Flow<ReviewWithSubmission?> = reviews.observe(reviewId, userId)

    suspend fun submit(profile: UserProfile, request: ReviewRequest): String {
        if (!inFlight.tryLock()) throw ServiceException("Hang on, a review is already in progress.")
        try {
            if (!network.isOnline.value) {
                throw ServiceException("No internet connection. You need to be online to review code, but your old reviews are still in History.")
            }
            return try {
                val item = remote.review(profile, request)
                reviews.insertSubmission(item.submission)
                reviews.insertReview(item.review)
                item.review.reviewId
            } catch (e: ServiceNotConfiguredException) {
                runQuickChecks(profile, request)
            }
        } finally {
            inFlight.unlock()
        }
    }

    suspend fun submitQuickChecks(profile: UserProfile, request: ReviewRequest): String = runQuickChecks(profile, request)

    private suspend fun runQuickChecks(profile: UserProfile, request: ReviewRequest): String {
        val applicable = runCatching { standards(profile) }.getOrDefault(emptyList())
        val result = QuickChecks.review(request.code, applicable)
        return storeLocal(profile, request, result, applicable).review.reviewId
    }

    suspend fun delete(review: Review) {
        if (review.syncedToCloud) {
            requireOnline("delete")
            remote.deleteReview(review.submissionId)
        }
        reviews.deleteSubmission(review.submissionId, review.userId)
    }

    suspend fun setArchived(review: Review, archived: Boolean) {
        if (review.syncedToCloud) {
            requireOnline(if (archived) "archive" else "restore")
            remote.setArchived(review.reviewId, archived)
        }
        reviews.setArchived(review.reviewId, review.userId, archived)
    }

    private fun requireOnline(action: String) {
        if (!network.isOnline.value) {
            throw ServiceException("No internet connection. Connect to $action this review.")
        }
    }

    suspend fun refreshFromCloud(userId: String) {
        if (!network.isOnline.value) return
        val items = remote.fetchHistory(HISTORY_LIMIT)
        items.forEach { item ->
            reviews.insertSubmission(item.submission)
            reviews.insertReview(item.review)
        }
        if (items.size < HISTORY_LIMIT) reviews.deleteSyncedExcept(userId, items.map { it.review.reviewId })
    }

    private suspend fun storeLocal(
        profile: UserProfile,
        request: ReviewRequest,
        result: ReviewResult,
        standards: List<CodingStandard>,
    ): ReviewWithSubmission {
        val requestedAt = System.currentTimeMillis()
        val submission = CodeSubmission(
            submissionId = UUID.randomUUID().toString(),
            userId = profile.userId,
            organizationId = profile.organizationId,
            departmentId = profile.departmentId,
            fileName = request.fileName,
            submissionMethod = request.method.name,
            code = request.code,
            reconstructedCode = request.reconstructedCode,
            imageCount = request.imageCount,
            uncertainLineCount = request.uncertainLineCount,
            status = "REVIEWED",
            submittedAt = requestedAt,
        )
        val review = Review(
            reviewId = UUID.randomUUID().toString(),
            submissionId = submission.submissionId,
            userId = profile.userId,
            standardsUsed = standards.joinToString(", ") { s ->
                if (s.version.isBlank()) s.standardName else "${s.standardName} v${s.version}"
            },
            reviewStatus = if (result.quickChecks) STATUS_QUICK_CHECK else "COMPLETED",
            summary = result.summary,
            findingsJson = FindingsJson.encode(result.findings),
            goodCount = result.findings.count { it.severity == Severity.GOOD },
            warningCount = result.findings.count { it.severity == Severity.WARNING },
            errorCount = result.findings.count { it.severity == Severity.ERROR },
            requestedAt = requestedAt,
            completedAt = requestedAt + 1_000,
            syncedToCloud = false,
        )
        reviews.insertSubmission(submission)
        reviews.insertReview(review)
        return ReviewWithSubmission(review, submission)
    }

    companion object {
        const val STATUS_QUICK_CHECK = "QUICK_CHECK"
        private const val HISTORY_LIMIT = 200L
    }
}
