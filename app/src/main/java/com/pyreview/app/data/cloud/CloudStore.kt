package com.pyreview.app.data.cloud

import com.pyreview.app.data.local.CodingStandard
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.supabase.DepartmentMember
import kotlinx.coroutines.flow.Flow

interface CloudStore {
    suspend fun fetchProfile(userId: String): UserProfile?
    suspend fun saveProfile(profile: UserProfile)

    suspend fun joinDepartment(inviteCode: String)

    suspend fun departmentInviteCode(): String?

    suspend fun regenerateInviteCode(): String

    suspend fun departmentMembers(): List<DepartmentMember>

    suspend fun setMemberRole(userId: String, role: String)

    fun observeStandards(organizationId: String, departmentId: String): Flow<List<CodingStandard>>
    suspend fun applicableStandards(organizationId: String, departmentId: String): List<CodingStandard>
    suspend fun saveStandard(standard: CodingStandard, isNew: Boolean)
    suspend fun deleteStandard(standard: CodingStandard)
}
