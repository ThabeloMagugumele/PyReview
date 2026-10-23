package com.pyreview.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.data.supabase.DepartmentMember
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

class MembersViewModel(private val container: AppContainer) : ViewModel() {
    var members by mutableStateOf<List<DepartmentMember>?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    val myUserId: String? get() = container.session.profile.value?.userId

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                members = container.session.departmentMembers()
            } catch (e: Exception) {
                members = emptyList()
                message = e.message ?: "Couldn't load the members."
            }
        }
    }

    fun setAdmin(member: DepartmentMember, makeAdmin: Boolean) {
        viewModelScope.launch {
            message = try {
                container.session.setMemberRole(member.userId, makeAdmin)
                reload()
                if (makeAdmin) "${member.fullName} is now an admin." else "${member.fullName} is now a member."
            } catch (e: Exception) {
                e.message ?: "Couldn't change that role."
            }
        }
    }
}

@Composable
fun MembersScreen(viewModel: MembersViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf<DepartmentMember?>(null) }

    ScreenScaffold(title = "Department members", onBack = onBack) { padding ->
        val members = viewModel.members
        if (members == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                NoticeCard(
                    "Admins look after your department's coding standards and invite code. New people join " +
                        "using the invite code in Profile.",
                )
                viewModel.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(members, key = { it.userId }) { member ->
                val isMe = member.userId == viewModel.myUserId
                OutlinedPanel {
                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (isMe) 12.dp else 0.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(member.fullName.ifBlank { "(no name)" } + if (isMe) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                                Text(member.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AssistChip(onClick = {}, enabled = false, label = { Text(if (member.isAdmin) "Admin" else "Member") })
                        }
                        if (!isMe) {
                            TextButton(onClick = { confirm = member }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(if (member.isAdmin) "Change to member" else "Make admin")
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { member ->
        val makeAdmin = !member.isAdmin
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (makeAdmin) "Make ${member.fullName} an admin?" else "Change ${member.fullName} to a member?") },
            text = {
                Text(
                    if (makeAdmin) "They'll be able to manage coding standards and the invite code."
                    else "They'll no longer be able to manage coding standards or the invite code.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = null; viewModel.setAdmin(member, makeAdmin) }) {
                    Text(if (makeAdmin) "Make admin" else "Change to member")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
