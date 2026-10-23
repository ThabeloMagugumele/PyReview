package com.pyreview.app.data.supabase

import com.pyreview.app.data.ServiceException
import com.pyreview.app.data.cloud.CloudStore
import com.pyreview.app.data.local.CodingStandard
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.local.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.ExperimentalCoroutinesApi

class SupabaseCloudStore(private val supabase: SupabaseClient) : CloudStore {

    private val standardsVersion = MutableStateFlow(0)

    override suspend fun fetchProfile(userId: String): UserProfile? = call {
        val profile = supabase.from("profiles")
            .select(Columns.raw("id, full_name, email, auth_provider, created_at, updated_at")) {
                filter { eq("id", userId) }
            }
            .decodeSingleOrNull<ProfileRow>() ?: return@call null
        val membership = supabase.from("memberships")
            .select(Columns.raw("role, departments(id, name, organizations(id, name))")) {
                filter { eq("user_id", userId) }
            }
            .decodeSingleOrNull<MembershipRow>()
        UserProfile(
            userId = profile.id,
            fullName = profile.fullName,
            email = profile.email.orEmpty(),
            authProvider = if (profile.authProvider == "google") "GOOGLE" else "EMAIL",
            organizationId = membership?.departments?.organizations?.id.orEmpty(),
            organizationName = membership?.departments?.organizations?.name.orEmpty(),
            departmentId = membership?.departments?.id.orEmpty(),
            departmentName = membership?.departments?.name.orEmpty(),
            userRole = if (membership?.role == "admin") UserRole.STANDARDS_MANAGER.name else UserRole.DEVELOPER.name,
            createdAt = parseTime(profile.createdAt),
            updatedAt = parseTime(profile.updatedAt),
        )
    }

    override suspend fun saveProfile(profile: UserProfile) = call {
        supabase.from("profiles").update({ set("full_name", profile.fullName) }) {
            filter { eq("id", profile.userId) }
        }
        Unit
    }

    override suspend fun joinDepartment(inviteCode: String) = call {
        supabase.postgrest.rpc("join_department", buildJsonObject { put("invite_code", inviteCode.trim()) })
        standardsVersion.value += 1
    }

    override suspend fun departmentInviteCode(): String? = call {
        val raw = supabase.postgrest.rpc("my_department_invite_code").data.trim()
        raw.removeSurrounding("\"").takeUnless { it.isEmpty() || it == "null" }
    }

    override suspend fun regenerateInviteCode(): String = call {
        supabase.postgrest.rpc("regenerate_department_invite_code").data.trim().removeSurrounding("\"")
    }

    override suspend fun departmentMembers(): List<DepartmentMember> = call {
        supabase.postgrest.rpc("department_members").decodeList<DepartmentMember>()
    }

    override suspend fun setMemberRole(userId: String, role: String) = call {
        supabase.postgrest.rpc("set_member_role", buildJsonObject {
            put("member_id", userId)
            put("new_role", role)
        })
        Unit
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStandards(organizationId: String, departmentId: String): Flow<List<CodingStandard>> =
        standardsVersion.flatMapLatest { flow { emit(applicableStandards(organizationId, departmentId)) } }

    override suspend fun applicableStandards(organizationId: String, departmentId: String): List<CodingStandard> = call {
        if (organizationId.isEmpty()) return@call emptyList()
        supabase.from("coding_standards")
            .select(Columns.raw("id, organization_id, department_id, title, description, version, content, uploaded_by, created_at"))
            .decodeList<StandardRow>()
            .map { it.toEntity() }
            .sortedWith(compareBy({ it.departmentId.isNotEmpty() }, { it.standardName.lowercase() }))
    }

    override suspend fun saveStandard(standard: CodingStandard, isNew: Boolean) = call {
        val row = StandardWrite(
            id = standard.standardId,
            organizationId = standard.organizationId,
            departmentId = standard.departmentId.ifEmpty { null },
            title = standard.standardName,
            description = standard.description,
            version = standard.version,
            content = standard.content,
            uploadedBy = standard.uploadedBy,
        )
        if (isNew) {
            supabase.from("coding_standards").insert(row)
        } else {
            supabase.from("coding_standards").update({
                set("title", row.title)
                set("description", row.description)
                set("version", row.version)
                set("content", row.content)
                set("department_id", row.departmentId)
            }) { filter { eq("id", row.id) } }
        }
        standardsVersion.value += 1
    }

    override suspend fun deleteStandard(standard: CodingStandard) = call {
        supabase.from("coding_standards").delete { filter { eq("id", standard.standardId) } }
        standardsVersion.value += 1
    }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: HttpRequestException) {
        throw ServiceException("Can't connect right now. Check your internet and try again.", e)
    } catch (e: RestException) {
        throw ServiceException(e.description?.takeIf { it.isNotBlank() } ?: e.error, e)
    }
}
