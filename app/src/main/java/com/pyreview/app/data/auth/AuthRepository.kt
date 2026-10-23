package com.pyreview.app.data.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val provider: String,
)

open class AuthException(message: String) : Exception(message)

class ConfirmEmailException(message: String) : AuthException(message)

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    val ready: StateFlow<Boolean>

    val supportsGoogle: Boolean

    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun register(fullName: String, email: String, password: String, inviteCode: String?): AuthUser

    suspend fun signInWithGoogle(idToken: String): AuthUser
    suspend fun sendPasswordReset(email: String)
    suspend fun updatePassword(newPassword: String)
    fun signOut()
}
