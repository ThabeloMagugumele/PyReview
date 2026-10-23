package com.pyreview.app.data

import com.pyreview.app.data.auth.AuthRepository
import com.pyreview.app.data.auth.AuthUser
import com.pyreview.app.data.cloud.CloudStore
import com.pyreview.app.data.local.UserDao
import com.pyreview.app.data.local.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRepository(
    private val auth: AuthRepository,
    private val users: UserDao,
    private val cloud: CloudStore,
    scope: CoroutineScope,
) {
    private val lock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: StateFlow<UserProfile?> = auth.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(null) else users.observe(user.uid) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            auth.currentUser.collect { user -> if (user != null) runCatching { ensureProfile(user) } }
        }
    }

    suspend fun ensureProfile(user: AuthUser) = lock.withLock {
        runCatching { cloud.fetchProfile(user.uid) }.getOrNull()?.let { users.upsert(it) }
    }

    suspend fun refresh() {
        auth.currentUser.value?.let { ensureProfile(it) }
    }

    suspend fun joinDepartment(inviteCode: String) {
        cloud.joinDepartment(inviteCode)
        refresh()
    }

    suspend fun departmentInviteCode(): String? = cloud.departmentInviteCode()

    suspend fun regenerateInviteCode(): String = cloud.regenerateInviteCode()

    suspend fun departmentMembers() = cloud.departmentMembers()

    suspend fun setMemberRole(userId: String, makeAdmin: Boolean) =
        cloud.setMemberRole(userId, if (makeAdmin) "admin" else "member")

    fun needsInviteCode(profile: UserProfile): Boolean = profile.departmentId.isEmpty()

    suspend fun updateProfile(profile: UserProfile) {
        val updated = profile.copy(updatedAt = System.currentTimeMillis())
        cloud.saveProfile(updated)
        users.upsert(updated)
    }

    fun signOut() = auth.signOut()
}
