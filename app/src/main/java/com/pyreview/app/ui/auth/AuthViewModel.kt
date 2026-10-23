package com.pyreview.app.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.data.auth.AuthException
import com.pyreview.app.data.auth.ConfirmEmailException
import kotlinx.coroutines.launch

data class AuthUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    var state by mutableStateOf(AuthUiState())
        private set

    val supportsGoogle: Boolean get() = container.auth.supportsGoogle

    fun clearMessages() {
        state = state.copy(error = null, info = null)
    }

    fun signIn(email: String, password: String) {
        val problem = when {
            !isEmail(email) -> "Enter a valid email address."
            password.isEmpty() -> "Enter your password."
            else -> null
        }
        if (problem != null) {
            state = AuthUiState(error = problem)
            return
        }
        run { container.auth.signIn(email.trim(), password) }
    }

    fun register(
        fullName: String,
        email: String,
        password: String,
        confirm: String,
        inviteCode: String,
    ) {
        val problem = when {
            fullName.isBlank() -> "Enter your full name."
            !isEmail(email) -> "Enter a valid email address."
            password.length < 8 -> "Use at least 8 characters for your password."
            password != confirm -> "The passwords don't match."
            inviteCode.isNotBlank() && !INVITE_CODE.matches(inviteCode.trim()) ->
                "That invite code doesn't look right. It should be 6 to 12 letters and numbers, or leave it empty."
            else -> null
        }
        if (problem != null) {
            state = AuthUiState(error = problem)
            return
        }
        run {
            container.auth.register(fullName.trim(), email.trim(), password, inviteCode.trim().ifEmpty { null })
        }
    }

    fun signInWithGoogle(idToken: String) = run { container.auth.signInWithGoogle(idToken) }

    fun updatePassword(password: String, confirm: String, onDone: () -> Unit) {
        val problem = when {
            password.length < 8 -> "Use at least 8 characters for your password."
            password != confirm -> "The passwords don't match."
            else -> null
        }
        if (problem != null) {
            state = AuthUiState(error = problem)
            return
        }
        run {
            container.auth.updatePassword(password)
            onDone()
        }
    }

    fun showError(message: String) {
        state = AuthUiState(error = message)
    }

    fun sendPasswordReset(email: String) {
        if (!isEmail(email)) {
            state = AuthUiState(error = "Type your email address above first, then tap \"Forgot password?\".")
            return
        }
        run {
            container.auth.sendPasswordReset(email.trim())
            state = AuthUiState(info = "We've sent you an email with a link to reset your password.")
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (state.busy) return
        state = AuthUiState(busy = true)
        viewModelScope.launch {
            state = try {
                block()
                state.copy(busy = false)
            } catch (e: ConfirmEmailException) {
                AuthUiState(info = e.message)
            } catch (e: AuthException) {
                AuthUiState(error = e.message)
            } catch (e: Exception) {
                AuthUiState(error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun isEmail(text: String) = Patterns.EMAIL_ADDRESS.matcher(text.trim()).matches()

    private companion object {
        val INVITE_CODE = Regex("(?i)^[A-Z0-9]{6,12}$")
    }
}
