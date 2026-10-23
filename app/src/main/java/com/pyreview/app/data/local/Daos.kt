package com.pyreview.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE userId = :userId")
    fun observe(userId: String): Flow<UserProfile?>

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun get(userId: String): UserProfile?

    @Upsert
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: CodeSubmission)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review)

    @Transaction
    @Query("SELECT * FROM reviews WHERE userId = :userId ORDER BY requestedAt DESC")
    fun observeHistory(userId: String): Flow<List<ReviewWithSubmission>>

    @Transaction
    @Query("SELECT * FROM reviews WHERE reviewId = :reviewId AND userId = :userId")
    fun observe(reviewId: String, userId: String): Flow<ReviewWithSubmission?>

    @Query("UPDATE reviews SET archived = :archived WHERE reviewId = :reviewId AND userId = :userId")
    suspend fun setArchived(reviewId: String, userId: String, archived: Boolean)

    @Query("DELETE FROM code_submissions WHERE submissionId = :submissionId AND userId = :userId")
    suspend fun deleteSubmission(submissionId: String, userId: String)

    @Query(
        """
        DELETE FROM code_submissions WHERE userId = :userId AND submissionId IN (
            SELECT submissionId FROM reviews WHERE userId = :userId AND syncedToCloud = 1 AND reviewId NOT IN (:keep)
        )
        """,
    )
    suspend fun deleteSyncedExcept(userId: String, keep: List<String>)

    @Query("UPDATE reviews SET syncedToCloud = 1 WHERE reviewId = :reviewId")
    suspend fun markSynced(reviewId: String)

    @Transaction
    @Query("SELECT * FROM reviews WHERE userId = :userId AND syncedToCloud = 0")
    suspend fun unsynced(userId: String): List<ReviewWithSubmission>
}
