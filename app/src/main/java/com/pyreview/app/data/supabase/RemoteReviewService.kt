package com.pyreview.app.data.supabase

import com.pyreview.app.data.RateLimitedException
import com.pyreview.app.data.ReviewRequest
import com.pyreview.app.data.ServiceException
import com.pyreview.app.data.ServiceNotConfiguredException
import com.pyreview.app.data.local.ReviewWithSubmission
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.ocr.CodeLine
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RemoteReviewService(
    private val supabase: SupabaseClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun review(profile: UserProfile, request: ReviewRequest): ReviewWithSubmission {
        val submission = call {
            supabase.from("submissions").insert(
                SubmissionInsert(
                    departmentId = profile.departmentId.ifEmpty { null },
                    fileName = request.fileName,
                    submissionMethod = request.method.name,
                    code = request.code,
                    reconstructedCode = request.reconstructedCode,
                    imageCount = request.imageCount,
                    uncertainLineCount = request.uncertainLineCount,
                ),
            ) { select() }.decodeSingle<SubmissionRow>()
        }
        val body = buildJsonObject { put("submission_id", submission.id) }.toString()
        val reply = try {
            invoke("review", body)
        } catch (e: ServiceException) {
            if (e is ServiceNotConfiguredException || e is RateLimitedException) {
                runCatching { supabase.from("submissions").delete { filter { eq("id", submission.id) } } }
            }
            throw e
        }
        val reviewId = json.decodeFromString(ReviewIdResponse.serializer(), reply).reviewId
        return fetchReview(reviewId) ?: throw ServiceException("Your review was saved but didn't load. Open History to see it.")
    }

    suspend fun fetchReview(reviewId: String): ReviewWithSubmission? = call {
        supabase.from("reviews")
            .select(Columns.raw(ReviewRow.SELECT)) { filter { eq("id", reviewId) } }
            .decodeSingleOrNull<ReviewRow>()
            ?.toEntity()
    }

    suspend fun deleteReview(submissionId: String) {
        call { supabase.from("submissions").delete { filter { eq("id", submissionId) } } }
    }

    suspend fun setArchived(reviewId: String, archived: Boolean) {
        call {
            supabase.postgrest.rpc("set_review_archived", buildJsonObject {
                put("review_id", reviewId)
                put("archived", archived)
            })
        }
    }

    suspend fun fetchHistory(limit: Long = 200): List<ReviewWithSubmission> = call {
        supabase.from("reviews")
            .select(Columns.raw(ReviewRow.SELECT)) {
                order("requested_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<ReviewRow>()
            .map { it.toEntity() }
    }

    suspend fun readPhotos(jpegBase64: List<String>): List<List<CodeLine>> =
        jpegBase64.chunked(PHOTOS_PER_REQUEST).flatMap { batch ->
            val body = buildJsonObject {
                putJsonArray("images") {
                    batch.forEach { photo ->
                        add(buildJsonObject {
                            put("image_base64", photo)
                            put("mime_type", "image/jpeg")
                        })
                    }
                }
            }.toString()
            val pages = json.decodeFromString(PhotoPagesResponse.serializer(), invoke("read-code", body)).pages
            if (pages.size != batch.size) throw ServiceException("Something went wrong reading the photos. Please try again.")
            pages.map { page -> page.lines.map { CodeLine(it.text, it.uncertain) } }
        }

    private suspend fun invoke(function: String, body: String): String {
        val token = supabase.auth.currentAccessTokenOrNull()
            ?: throw ServiceException("You've been signed out. Please sign in again.")
        return withContext(Dispatchers.IO) {
            val connection = (URL("$supabaseUrl/functions/v1/$function").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray()) }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status in 200..299) return@withContext text
                val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
                when {
                    status == 503 && error?.error == "not_configured" ->
                        throw ServiceNotConfiguredException(error.message ?: "The AI review isn't available right now.")
                    status == 429 && error?.error == "rate_limited" ->
                        throw RateLimitedException(error.message ?: "The AI is busy. Give it a minute and try again.", daily = error.scope == "day")
                    status == 401 -> throw ServiceException("You've been signed out. Please sign in again.")
                    else -> throw ServiceException(error?.error?.takeIf { it.isNotBlank() } ?: "Something went wrong on our side (error $status). Please try again.")
                }
            } catch (e: IOException) {
                throw ServiceException("Can't connect right now. Check your internet and try again.", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val PHOTOS_PER_REQUEST = 4
    }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: HttpRequestException) {
        throw ServiceException("Can't connect right now. Check your internet and try again.", e)
    } catch (e: RestException) {
        throw ServiceException(e.description?.takeIf { it.isNotBlank() } ?: e.error, e)
    }
}
