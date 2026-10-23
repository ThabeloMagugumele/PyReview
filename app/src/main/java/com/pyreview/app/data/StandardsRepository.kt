package com.pyreview.app.data

import com.pyreview.app.data.cloud.CloudStore
import com.pyreview.app.data.local.CodingStandard
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.local.UserRole
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class StandardsRepository(private val cloud: CloudStore) {

    fun observe(profile: UserProfile): Flow<List<CodingStandard>> =
        cloud.observeStandards(profile.organizationId, profile.departmentId)

    suspend fun save(
        profile: UserProfile,
        existing: CodingStandard?,
        name: String,
        version: String,
        description: String,
        content: String,
        wholeOrganisation: Boolean,
    ) {
        check(profile.role == UserRole.STANDARDS_MANAGER) { "Only department admins can change coding standards." }
        cloud.saveStandard(
            isNew = existing == null,
            standard = CodingStandard(
                standardId = existing?.standardId ?: UUID.randomUUID().toString(),
                organizationId = profile.organizationId,
                departmentId = if (wholeOrganisation) "" else profile.departmentId,
                standardName = name.trim(),
                description = description.trim(),
                version = version.trim(),
                content = content.trim(),
                uploadedBy = profile.userId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(profile: UserProfile, standard: CodingStandard) {
        check(profile.role == UserRole.STANDARDS_MANAGER) { "Only department admins can change coding standards." }
        check(standard.organizationId == profile.organizationId) { "You can't change another organisation's standard." }
        cloud.deleteStandard(standard)
    }
}
