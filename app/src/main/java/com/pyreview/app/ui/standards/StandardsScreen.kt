package com.pyreview.app.ui.standards

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.R
import com.pyreview.app.data.local.CodingStandard
import com.pyreview.app.data.local.UserProfile
import com.pyreview.app.data.local.UserRole
import com.pyreview.app.ui.auth.PrimaryButton
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.theme.CodeTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StandardsViewModel(private val container: AppContainer) : ViewModel() {
    val profile = container.session.profile

    var message by mutableStateOf<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val standards: StateFlow<List<CodingStandard>?> = profile
        .flatMapLatest { p ->
            if (p == null) flowOf(emptyList())
            else container.standards.observe(p).catch {
                message = "Couldn't load coding standards: ${it.message}"
                emit(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(p: UserProfile, existing: CodingStandard?, name: String, version: String, description: String, content: String, wholeOrg: Boolean) {
        viewModelScope.launch {
            message = try {
                container.standards.save(p, existing, name, version, description, content, wholeOrg)
                null
            } catch (e: Exception) {
                "Couldn't save the standard: ${e.message}"
            }
        }
    }

    fun delete(p: UserProfile, standard: CodingStandard) {
        viewModelScope.launch {
            message = try {
                container.standards.delete(p, standard)
                null
            } catch (e: Exception) {
                "Couldn't delete the standard: ${e.message}"
            }
        }
    }
}

@Composable
fun StandardsScreen(viewModel: StandardsViewModel, onBack: () -> Unit) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val standards by viewModel.standards.collectAsStateWithLifecycle()
    val p = profile ?: return
    val isManager = p.role == UserRole.STANDARDS_MANAGER

    var editing by remember { mutableStateOf<CodingStandard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<CodingStandard?>(null) }
    var viewing by remember { mutableStateOf<CodingStandard?>(null) }

    ScreenScaffold(
        title = "Coding standards",
        onBack = onBack,
        floatingActionButton = {
            if (isManager) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add standard") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val dept = p.departmentName.ifBlank { null }
                NoticeCard(
                    "Your reviews follow the standards below: ${p.organizationName}'s organisation-wide standards" +
                        (dept?.let { " and the $it department's own" } ?: "") +
                        ". Other departments' standards don't affect your reviews.",
                )
                viewModel.message?.let {
                    Spacer(Modifier.height(8.dp))
                    NoticeCard(it, accent = MaterialTheme.colorScheme.error)
                }
            }
            val list = standards
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        if (isManager) "No standards yet. Add your team's coding standard so reviews follow it."
                        else "No coding standards added yet, so reviews check general Python practice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.standardId }) { standard ->
                OutlinedPanel(Modifier.clickable { viewing = standard }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_description), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                standard.standardName + if (standard.version.isNotBlank()) " v${standard.version}" else "",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            AssistChip(
                                onClick = { viewing = standard },
                                label = { Text(if (standard.isOrganisationWide) "Organisation" else p.departmentName.ifBlank { "Department" }) },
                            )
                        }
                        if (standard.description.isNotBlank()) {
                            Text(standard.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(standard.content, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (isManager) {
                            Row {
                                TextButton(onClick = { editing = standard }) { Text("Edit") }
                                TextButton(onClick = { confirmDelete = standard }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        StandardEditor(
            existing = editing,
            departmentName = p.departmentName,
            onDismiss = { creating = false; editing = null },
            onSave = { name, version, description, content, wholeOrg ->
                viewModel.save(p, editing, name, version, description, content, wholeOrg)
                creating = false
                editing = null
            },
        )
    }
    confirmDelete?.let { standard ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${standard.standardName}?") },
            text = { Text("New reviews won't use this standard anymore. Old reviews stay the same.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(p, standard); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    viewing?.let { standard ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(standard.standardName) },
            text = { Text(standard.content, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun StandardEditor(
    existing: CodingStandard?,
    departmentName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, version: String, description: String, content: String, wholeOrg: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.standardName.orEmpty()) }
    var version by remember { mutableStateOf(existing?.version ?: "1.0") }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var wholeOrg by remember { mutableStateOf(existing?.isOrganisationWide ?: departmentName.isBlank()) }
    var importError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readText(context, uri) }
                    .onSuccess { text ->
                        content = text
                        importError = null
                    }
                    .onFailure { importError = it.message }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (existing == null) "Add coding standard" else "Edit coding standard", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("Python Style Guide") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(version, { version = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Short description (optional)") }, modifier = Modifier.fillMaxWidth())

                Text("Applies to", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = wholeOrg, onClick = { wholeOrg = true })
                    Text("The whole organisation")
                }
                if (departmentName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !wholeOrg, onClick = { wholeOrg = false })
                        Text("Only the $departmentName department")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Standard", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*")) }) { Text("Import .txt / .md") }
                }
                importError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Paste the rules, e.g.\n- Function names use snake_case\n- Every function has a docstring") },
                    textStyle = CodeTextStyle,
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Save", enabled = name.isNotBlank() && content.isNotBlank()) {
                    onSave(name, version, description, content, wholeOrg)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
            }
        }
    }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open that file.")
    require(bytes.size <= 500_000) { "That file is too large. Keep standards under 500 KB." }
    bytes.toString(Charsets.UTF_8)
}
