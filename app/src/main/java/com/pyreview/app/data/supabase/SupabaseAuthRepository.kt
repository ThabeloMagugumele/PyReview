package com.pyreview.app.data.supabase

import com.pyreview.app.data.auth.AuthException
import com.pyreview.app.data.auth.AuthRepository
import com.pyreview.app.data.auth.AuthUser
import com.pyreview.app.data.auth.ConfirmEmailException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseAuthRepository(
    private val supabase: SupabaseClient,
    private val scope: CoroutineScope,
    override val supportsGoogle: Boolean,
) : AuthRepository {


    override val currentUser: StateFlow<AuthUser?> = supabase.auth.sessionStatus
        .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.toAuthUser() }
        .stateIn(scope, SharingStarted.Eagerly, supabase.auth.currentUserOrNull()?.toAuthUser())

    override val ready: StateFlow<Boolean> = supabase.auth.sessionStatus
        .map { it !is SessionStatus.Initializing }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun signIn(email: String, password: String): AuthUser = friendly {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        signedInUser()
    }

    override suspend fun register(fullName: String, email: String, password: String, inviteCode: String?): AuthUser = friendly {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName)
                if (!inviteCode.isNullOrBlank()) put("invite_code", inviteCode.trim().uppercase())
            }
        }
        if (supabase.auth.currentSessionOrNull() == null) {
            throw ConfirmEmailException("Check your email and tap the link to confirm your account, then sign in.")
        }
        signedInUser()
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser = friendly {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
        signedInUser()
    }

    override suspend fun sendPasswordReset(email: String) = friendly {
        supabase.auth.resetPasswordForEmail(email, redirectUrl = REDIRECT_URL)
    }

    override suspend fun updatePassword(newPassword: String) = friendly {
        supabase.auth.updateUser { password = newPassword }
        Unit
    }

    override fun signOut() {
        scope.launch { runCatching { supabase.auth.signOut() } }
    }

    private fun signedInUser(): AuthUser =
        supabase.auth.currentUserOrNull()?.toAuthUser() ?: throw AuthException("Signing in didn't finish. Please try again.")

    private fun UserInfo.toAuthUser() = AuthUser(
        uid = id,
        email = email.orEmpty(),
        displayName = userMetadata?.get("full_name")?.jsonPrimitive?.content.orEmpty(),
        provider = if (appMetadata?.get("provider")?.jsonPrimitive?.content == "google") "GOOGLE" else "EMAIL",
    )

    private inline fun <T> friendly(block: () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (e: AuthWeakPasswordException) {
        throw AuthException("Pick a stronger password: at least 8 characters and nothing too common.")
    } catch (e: AuthRestException) {
        throw AuthException(messageFor(e.error, e.description ?: e.message))
    } catch (e: HttpRequestException) {
        throw AuthException("You need internet to sign in.")
    } catch (e: RestException) {
        throw AuthException(e.description ?: e.error)
    }

    private fun messageFor(code: String, fallback: String?): String = when (code) {
        "invalid_credentials", "invalid_grant" -> "The email or password is incorrect."
        "user_already_exists", "email_exists" -> "An account with this email already exists. Try signing in."
        "email_not_confirmed" -> "Confirm your email first: tap the link we sent you, then sign in."
        "over_email_send_rate_limit", "over_request_rate_limit" -> "Too many attempts. Please wait a few minutes and try again."
        "weak_password" -> "Pick a stronger password: at least 8 characters and nothing too common."
        "validation_failed" -> "Check the email address and try again."
        else -> fallback ?: "Something went wrong signing in. Please try again."
    }

    companion object {
        const val REDIRECT_URL = "pyreview://auth"
    }
}
