package com.pyreview.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.R
import com.pyreview.app.data.local.Review
import com.pyreview.app.data.local.ReviewWithSubmission
import com.pyreview.app.ui.auth.PrimaryButton
import com.pyreview.app.ui.components.DeleteReviewDialog
import com.pyreview.app.ui.components.OfflineBanner
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.components.SectionLabel
import com.pyreview.app.ui.theme.CodeTextStyle
import com.pyreview.app.ui.theme.LocalStatusColors
import com.pyreview.app.util.friendlyDay
import com.pyreview.app.util.isThisWeek
import com.pyreview.app.util.pluralize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryMessage {
    data class Archived(val review: Review, val archived: Boolean) : HistoryMessage
    data class Info(val text: String) : HistoryMessage
}

class HistoryViewModel(private val container: AppContainer, val archivedOnly: Boolean = false) : ViewModel() {
    val isOnline = container.network.isOnline

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<ReviewWithSubmission>?> = container.session.profile
        .flatMapLatest { profile -> if (profile == null) flowOf(emptyList()) else container.reviews.history(profile.userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messageQueue = Channel<HistoryMessage>(Channel.BUFFERED)
    val messages: Flow<HistoryMessage> = messageQueue.receiveAsFlow()

    var query by mutableStateOf("")
    var comparing by mutableStateOf(false)
        private set
    var selected by mutableStateOf<List<String>>(emptyList())
        private set

    fun refresh() {
        val userId = container.session.profile.value?.userId ?: return
        viewModelScope.launch { runCatching { container.reviews.refreshFromCloud(userId) } }
    }

    fun toggleCompare() {
        comparing = !comparing
        selected = emptyList()
    }

    fun toggleSelected(reviewId: String) {
        selected = when {
            reviewId in selected -> selected - reviewId
            selected.size == 2 -> listOf(selected[1], reviewId)
            else -> selected + reviewId
        }
    }

    fun setArchived(review: Review, archived: Boolean, offerUndo: Boolean = true) {
        viewModelScope.launch {
            try {
                container.reviews.setArchived(review, archived)
                if (offerUndo) messageQueue.send(HistoryMessage.Archived(review, archived))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messageQueue.send(HistoryMessage.Info(e.message ?: "Couldn't update the review. Please try again."))
            }
        }
    }

    fun delete(review: Review) {
        viewModelScope.launch {
            try {
                container.reviews.delete(review)
                selected = selected - review.reviewId
                messageQueue.send(HistoryMessage.Info("Review deleted"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messageQueue.send(HistoryMessage.Info(e.message ?: "Couldn't delete the review. Please try again."))
            }
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpen: (String) -> Unit,
    onCompare: (String, String) -> Unit,
    onOpenArchived: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val all by viewModel.history.collectAsStateWithLifecycle()
    val online by viewModel.isOnline.collectAsStateWithLifecycle()
    LaunchedEffect(online) { if (online) viewModel.refresh() }

    val archivedOnly = viewModel.archivedOnly
    val history = all?.filter { it.review.archived == archivedOnly }
    val archivedCount = all.orEmpty().count { it.review.archived }
    val items = history.orEmpty().filter {
        viewModel.query.isBlank() || it.submission.fileName.contains(viewModel.query.trim(), ignoreCase = true)
    }

    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<ReviewWithSubmission?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is HistoryMessage.Archived -> {
                    val result = snackbar.showSnackbar(
                        if (message.archived) "Review archived" else "Review moved back to History",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.setArchived(message.review, !message.archived, offerUndo = false)
                    }
                }
                is HistoryMessage.Info -> snackbar.showSnackbar(message.text)
            }
        }
    }

    ScreenScaffold(
        title = if (archivedOnly) "Archived" else null,
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            if (!archivedOnly && history.orEmpty().size >= 2) {
                Column(
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (viewModel.comparing) {
                        PrimaryButton("Compare selected (${viewModel.selected.size}/2)", enabled = viewModel.selected.size == 2) {
                            val chosen = history.orEmpty().filter { it.review.reviewId in viewModel.selected }
                                .sortedBy { it.review.requestedAt }
                            onCompare(chosen[0].review.reviewId, chosen[1].review.reviewId)
                        }
                        TextButton(onClick = viewModel::toggleCompare, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Cancel")
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::toggleCompare,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_compare), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Compare two reviews")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (archivedOnly) {
                    Text(
                        "Archived reviews are still saved and work offline. Tap ⋮ on a review to move it back to History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Past submissions", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(12.dp))
                if (!online) {
                    OfflineBanner("No internet right now, but all your saved reviews are still here.")
                    Spacer(Modifier.height(12.dp))
                }
                if (history.orEmpty().isNotEmpty()) {
                    OutlinedTextField(
                        value = viewModel.query,
                        onValueChange = { viewModel.query = it },
                        placeholder = { Text("Search files") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (viewModel.comparing) {
                    Spacer(Modifier.height(8.dp))
                    Text("Pick two reviews to compare.", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!archivedOnly && archivedCount > 0 && !viewModel.comparing) {
                item(key = "archived-row") { ArchivedRow(archivedCount, onOpenArchived) }
            }

            when {
                history == null -> Unit
                history.isEmpty() -> item {
                    Text(
                        when {
                            archivedOnly -> "Nothing archived yet. Archive reviews from History to clean up the list without deleting them."
                            archivedCount > 0 -> "Nothing here right now. Your archived reviews are in Archived above."
                            else -> "No reviews yet. Submit some code and your reviews will show up here, even offline."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                else -> {
                    val (recent, earlier) = items.partition { isThisWeek(it.review.requestedAt) }
                    val row: @Composable (ReviewWithSubmission) -> Unit = { item ->
                        HistoryRow(
                            item = item,
                            viewModel = viewModel,
                            onOpen = onOpen,
                            onArchive = { viewModel.setArchived(item.review, !item.review.archived) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                    if (recent.isNotEmpty()) item { SectionLabel("This week") }
                    items(recent, key = { it.review.reviewId }) { row(it) }
                    if (earlier.isNotEmpty()) item { SectionLabel("Earlier") }
                    items(earlier, key = { it.review.reviewId }) { row(it) }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteReviewDialog(
            item = item,
            onConfirm = {
                viewModel.delete(item.review)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ArchivedRow(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_archive), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text("Archived", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HistoryRow(
    item: ReviewWithSubmission,
    viewModel: HistoryViewModel,
    onOpen: (String) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = LocalStatusColors.current
    val review = item.review
    val (accent, container) = when {
        review.errorCount > 0 -> status.error to status.errorContainer
        review.warningCount > 0 -> status.warning to status.warningContainer
        else -> status.good to status.goodContainer
    }
    val summary = buildList {
        if (review.errorCount > 0) add(pluralize(review.errorCount, "error"))
        if (review.warningCount > 0) add(pluralize(review.warningCount, "warning"))
    }.joinToString(" · ").ifEmpty { "No issues" }
    val selected = review.reviewId in viewModel.selected
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedPanel(
        Modifier.combinedClickable(
            onClick = { if (viewModel.comparing) viewModel.toggleSelected(review.reviewId) else onOpen(review.reviewId) },
            onLongClick = { if (!viewModel.comparing) menuOpen = true },
            onLongClickLabel = "Review options",
        ),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.comparing) {
                Checkbox(checked = selected, onCheckedChange = { viewModel.toggleSelected(review.reviewId) })
            }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(container),
                contentAlignment = Alignment.Center,
            ) {
                Text(".PY", style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.submission.fileName, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = accent)
                if (!review.syncedToCloud) {
                    Text("Quick checks · on this phone only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(friendlyDay(review.requestedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (viewModel.comparing) {
                Spacer(Modifier.width(12.dp))
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options for ${item.submission.fileName}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (review.archived) "Move back to History" else "Archive") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onArchive()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}
