package com.pyreview.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pyreview.app.R
import com.pyreview.app.data.auth.AuthException
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: AuthViewModel, onCreateAccount: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val state = viewModel.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AuthScaffold {
        AppMark()
        Spacer(Modifier.height(24.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sign in to get your Python code reviewed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        EmailField(email) { email = it; viewModel.clearMessages() }
        Spacer(Modifier.height(12.dp))
        PasswordField("Password", password, ImeAction.Done) { password = it; viewModel.clearMessages() }
        TextButton(onClick = { viewModel.sendPasswordReset(email) }, enabled = !state.busy) {
            Text("Forgot password?")
        }

        Messages(state)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Sign in", busy = state.busy) { viewModel.signIn(email, password) }

        Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "or continue with",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = {
                if (!viewModel.supportsGoogle) {
                    viewModel.showError("Google sign-in is coming soon. Please use your email and password for now.")
                } else {
                    scope.launch {
                        try {
                            GoogleSignIn.requestIdToken(context)?.let(viewModel::signInWithGoogle)
                        } catch (e: AuthException) {
                            viewModel.showError(e.message.orEmpty())
                        }
                    }
                }
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("G", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google")
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCreateAccount, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("New here? Create an account")
        }
    }
}

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onBackToSignIn: () -> Unit) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    val state = viewModel.state

    AuthScaffold {
        AppMark()
        Spacer(Modifier.height(24.dp))
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Got an invite code from your department? Add it so your reviews follow your team's coding standards.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        LabeledField("Full name", fullName, KeyboardType.Text) { fullName = it; viewModel.clearMessages() }
        Spacer(Modifier.height(12.dp))
        EmailField(email) { email = it; viewModel.clearMessages() }
        Spacer(Modifier.height(12.dp))
        PasswordField("Password (at least 8 characters)", password, ImeAction.Next) { password = it; viewModel.clearMessages() }
        Spacer(Modifier.height(12.dp))
        PasswordField("Confirm password", confirm, ImeAction.Next) { confirm = it; viewModel.clearMessages() }
        Spacer(Modifier.height(12.dp))
        LabeledField("Department invite code (optional)", inviteCode, KeyboardType.Ascii, placeholder = "e.g. 7F3A9C2B") {
            inviteCode = it.uppercase().filter(Char::isLetterOrDigit).take(12)
            viewModel.clearMessages()
        }
        Text(
            "Don't have one? Leave it empty. You can add it later in Profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        Messages(state)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Create account", busy = state.busy) {
            viewModel.register(fullName, email, password, confirm, inviteCode)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBackToSignIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
fun NewPasswordScreen(viewModel: AuthViewModel, onDone: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val state = viewModel.state

    AuthScaffold {
        AppMark()
        Spacer(Modifier.height(24.dp))
        Text("Choose a new password", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Set a new password to finish resetting your account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PasswordField("New password (at least 8 characters)", password, ImeAction.Next) { password = it; viewModel.clearMessages() }
        Spacer(Modifier.height(12.dp))
        PasswordField("Confirm new password", confirm, ImeAction.Done) { confirm = it; viewModel.clearMessages() }
        Spacer(Modifier.height(16.dp))
        Messages(state)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Save new password", busy = state.busy) { viewModel.updatePassword(password, confirm, onDone) }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Not now") }
    }
}

@Composable
private fun AuthScaffold(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            content = content,
        )
    }
}

@Composable
private fun AppMark() {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_code), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) =
    LabeledField("Email", value, KeyboardType.Email, placeholder = "you@company.com", onChange = onChange)

@Composable
private fun LabeledField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(label: String, value: String, imeAction: ImeAction, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Messages(state: AuthUiState) {
    val colors = LocalStatusColors.current
    state.error?.let { NoticeCard(it, accent = colors.error, container = colors.errorContainer) }
    state.info?.let { NoticeCard(it, accent = colors.good, container = colors.goodContainer) }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, busy: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}
