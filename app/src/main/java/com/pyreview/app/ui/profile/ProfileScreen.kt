package com.pyreview.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import com.pyreview.app.ui.components.JoinDepartmentDialog
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.local.UserRole
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.components.SectionLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileStats(val reviews: Int, val cleanReviews: Int, val errorsLatest: Int?)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    val profile = container.session.profile

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<ProfileStats> = profile
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else container.reviews.history(p.userId) }
        .map { history ->
            ProfileStats(
                reviews = history.size,
                cleanReviews = history.count { it.review.errorCount == 0 },
                errorsLatest = history.firstOrNull()?.review?.errorCount,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats(0, 0, null))

    var message by mutableStateOf<String?>(null)

    var inviteCode by mutableStateOf<String?>(null)
        private set

    var joining by mutableStateOf(false)
        private set

    var joinError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { container.session.refresh() }
            loadInviteCode()
        }
    }

    private suspend fun loadInviteCode() {
        inviteCode = runCatching { container.session.departmentInviteCode() }.getOrNull()
    }

    fun needsInviteCode(p: UserProfile) = container.session.needsInviteCode(p)

    fun joinDepartment(code: String, onJoined: () -> Unit) {
        if (joining) return
        joining = true
        joinError = null
        viewModelScope.launch {
            try {
                container.session.joinDepartment(code)
                message = "You've joined your department."
                onJoined()
            } catch (e: Exception) {
                joinError = e.message ?: "Couldn't join that department."
            } finally {
                joining = false
            }
        }
    }

    fun clearJoinError() {
        joinError = null
    }

    fun regenerateInviteCode() {
        viewModelScope.launch {
            message = try {
                inviteCode = container.session.regenerateInviteCode()
                "New invite code created. The old code no longer works."
            } catch (e: Exception) {
                "Couldn't create a new code: ${e.message}"
            }
        }
    }

    fun canManageMembers(p: UserProfile) = p.role == UserRole.STANDARDS_MANAGER && p.departmentId.isNotEmpty()

    fun updateName(p: UserProfile, name: String) = save { p.copy(fullName = name.trim()) }

    fun signOut() {
        container.draft.startOver()
        container.session.signOut()
    }

    private fun save(change: () -> UserProfile) {
        viewModelScope.launch {
            message = try {
                container.session.updateProfile(change())
                null
            } catch (e: Exception) {
                "Couldn't save your profile: ${e.message}"
            }
        }
    }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onOpenStandards: () -> Unit, onOpenMembers: () -> Unit) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    var joiningDepartment by remember { mutableStateOf(false) }
    var confirmNewCode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ScreenScaffold { padding ->
        val p = profile ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials(p.fullName), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.fullName, style = MaterialTheme.typography.headlineSmall)
                    Text(p.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editingName = true }) { Text("Edit") }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Reviews", stats.reviews.toString(), Modifier.weight(1f))
                StatTile("Without errors", stats.cleanReviews.toString(), Modifier.weight(1f))
                StatTile("Errors last time", stats.errorsLatest?.toString() ?: "–", Modifier.weight(1f))
            }

            SectionLabel("Organisation")
            OutlinedPanel {
                Column {
                    InfoRow("Organisation", p.organizationName.ifBlank { "Not set" })
                    HorizontalDivider()
                    InfoRow("Department", p.departmentName.ifBlank { "Not set" })
                    if (viewModel.needsInviteCode(p)) {
                        HorizontalDivider()
                        TextButton(onClick = { viewModel.clearJoinError(); joiningDepartment = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text("Join your department with an invite code")
                        }
                    }
                    viewModel.inviteCode?.let { code ->
                        HorizontalDivider()
                        InviteCodeRow(
                            code = code,
                            onCopy = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("PyReview invite code", code))
                                viewModel.message = "Invite code copied."
                            },
                            onShare = {
                                val text = "Join ${p.departmentName} on PyReview! Sign up and use invite code $code"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, "Share invite code"))
                            },
                            onNewCode = { confirmNewCode = true },
                        )
                    }
                    if (viewModel.canManageMembers(p)) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onOpenMembers).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Manage department members", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
            if (viewModel.needsInviteCode(p)) {
                Text(
                    "Until you join a department, reviews check general Python practice instead of your team's standards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    "Need to move to another department? Ask your admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionLabel("Coding standards")
            OutlinedPanel {
                Column {
                    InfoRow("Role", p.role.label)
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenStandards).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (p.role == UserRole.STANDARDS_MANAGER) "Manage coding standards" else "View my team's coding standards",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            SectionLabel("Account")
            OutlinedPanel {
                Column {
                    InfoRow("Signed in with", if (p.authProvider == "GOOGLE") "Google" else "Email and password")
                    HorizontalDivider()
                    InfoRow("Reviews by", "Gemini")
                }
            }
            viewModel.message?.let {
                Spacer(Modifier.height(12.dp))
                NoticeCard(it)
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Sign out") }
            Text(
                "Your reviews stay saved, so they'll be here when you sign back in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (joiningDepartment) {
            JoinDepartmentDialog(
                busy = viewModel.joining,
                error = viewModel.joinError,
                onJoin = { code -> viewModel.joinDepartment(code) { joiningDepartment = false } },
                onDismiss = { joiningDepartment = false },
            )
        }
        if (confirmNewCode) {
            AlertDialog(
                onDismissRequest = { confirmNewCode = false },
                title = { Text("Create a new invite code?") },
                text = { Text("The current code will stop working. Anyone already in the department stays in.") },
                confirmButton = {
                    TextButton(onClick = { confirmNewCode = false; viewModel.regenerateInviteCode() }) { Text("Create new code") }
                },
                dismissButton = { TextButton(onClick = { confirmNewCode = false }) { Text("Cancel") } },
            )
        }
        if (editingName) {
            NameDialog(
                name = p.fullName,
                onDismiss = { editingName = false },
                onSave = { name ->
                    viewModel.updateName(p, name)
                    editingName = false
                },
            )
        }
    }
}

@Composable
private fun InviteCodeRow(code: String, onCopy: () -> Unit, onShare: () -> Unit, onNewCode: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text("Invite code for new members", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(code, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        Row {
            TextButton(onClick = onCopy) { Text("Copy") }
            TextButton(onClick = onShare) { Text("Share") }
            TextButton(onClick = onNewCode) { Text("New code") }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    OutlinedPanel(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NameDialog(name: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = { OutlinedTextField(value, { value = it }, label = { Text("Full name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
