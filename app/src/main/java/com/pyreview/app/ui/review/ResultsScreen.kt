package com.pyreview.app.ui.review

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.pyreview.app.ui.components.CodeRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.R
import com.pyreview.app.data.ReviewRepository
import com.pyreview.app.data.local.ReviewWithSubmission
import com.pyreview.app.data.review.Finding
import com.pyreview.app.data.review.FindingsJson
import com.pyreview.app.data.review.Severity
import com.pyreview.app.ui.auth.PrimaryButton
import com.pyreview.app.ui.components.CodeBlock
import com.pyreview.app.ui.components.DeleteReviewDialog
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.components.SectionLabel
import com.pyreview.app.ui.components.SeverityDot
import com.pyreview.app.ui.theme.LocalStatusColors
import com.pyreview.app.util.friendlyDateTime
import com.pyreview.app.util.pluralize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoadedReview(val item: ReviewWithSubmission, val findings: List<Finding>) {
    val codeLines: List<String> = item.submission.code.lines()
}

class ResultsViewModel(private val container: AppContainer, reviewId: String) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val review: StateFlow<LoadedReview?> = container.session.profile
        .flatMapLatest { profile ->
            if (profile == null) flowOf(null) else container.reviews.review(reviewId, profile.userId)
        }
        .map { item -> item?.let { LoadedReview(it, FindingsJson.decode(it.review.findingsJson)) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var visible by mutableStateOf(Severity.entries.toSet())
        private set

    var expanded by mutableStateOf<Set<String>>(emptySet())
        private set

    private var autoExpanded = false

    fun toggleFilter(severity: Severity) {
        visible = if (severity in visible) visible - severity else visible + severity
    }

    fun toggleExpanded(id: String) {
        expanded = if (id in expanded) expanded - id else expanded + id
    }

    var deleted by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)

    fun setArchived(archived: Boolean) {
        val current = review.value?.item?.review ?: return
        viewModelScope.launch {
            message = try {
                container.reviews.setArchived(current, archived)
                if (archived) "Review archived. Find it under History > Archived." else "Review moved back to History"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Couldn't update the review. Please try again."
            }
        }
    }

    fun delete() {
        val current = review.value?.item?.review ?: return
        viewModelScope.launch {
            try {
                container.reviews.delete(current)
                deleted = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Couldn't delete the review. Please try again."
            }
        }
    }

    fun expandFirst(findings: List<Finding>) {
        if (autoExpanded) return
        autoExpanded = true
        findings.firstOrNull { it.severity != Severity.GOOD }?.let { expanded = setOf(it.id) }
    }
}

@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onBack: () -> Unit,
    onResubmit: (code: String, fileName: String) -> Unit,
) {
    val loaded by viewModel.review.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.deleted) { if (viewModel.deleted) onBack() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.message = null
        }
    }

    ScreenScaffold(
        title = "Review",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            val current = loaded ?: return@ScreenScaffold
            val archived = current.item.review.archived
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Review options") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (archived) "Move back to History" else "Archive") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        viewModel.setArchived(!archived)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        confirmDelete = true
                    },
                )
            }
        },
        bottomBar = {
            loaded?.let { review ->
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent.createChooser(shareIntent(review), "Share review")) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Share") }
                    PrimaryButton("Resubmit when fixed", modifier = Modifier.weight(1.6f)) {
                        onResubmit(review.item.submission.code, review.item.submission.fileName)
                    }
                }
            }
        },
    ) { padding ->
        val review = loaded
        if (review == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        LaunchedEffect(review.item.review.reviewId) { viewModel.expandFirst(review.findings) }

        val status = LocalStatusColors.current
        val visible = viewModel.visible
        val shown = remember(review, visible) { review.findings.filter { it.severity in visible } }
        val toLookAt = remember(review) { review.findings.count { it.severity != Severity.GOOD } }
        val (highlights, markers) = remember(shown, status) {
            val located = shown.filter { it.lineStart > 0 }.sortedBy { it.severity.ordinal }
            val h = HashMap<Int, Color>()
            val m = HashMap<Int, Color>()
            located.forEach { f ->
                for (line in f.lineStart..f.lineEnd) {
                    if (f.severity != Severity.GOOD) h[line] = status.container(f.severity)
                    m[line] = status.accent(f.severity)
                }
            }
            h to m
        }
        val codeBackground = MaterialTheme.colorScheme.surfaceContainerLow

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item(contentType = "header") {
                Text("Review of ${review.item.submission.fileName}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${if (toLookAt == 0) "Nothing to fix" else pluralize(toLookAt, "thing") + " to look at"} · your code was not changed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    friendlyDateTime(review.item.review.requestedAt) + " · " +
                        (review.item.review.standardsUsed.ifBlank { null }?.let { "Checked against $it" } ?: "General Python practice"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Severity.entries.forEach { severity ->
                        val count = review.findings.count { it.severity == severity }
                        FilterChip(
                            selected = severity in viewModel.visible,
                            onClick = { viewModel.toggleFilter(severity) },
                            label = { Text("${severity.label} $count") },
                            leadingIcon = { SeverityDot(severity) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = status.container(severity)),
                        )
                    }
                }
                if (review.item.review.reviewStatus == ReviewRepository.STATUS_QUICK_CHECK) {
                    Spacer(Modifier.height(8.dp))
                    NoticeCard(
                        "These results are from PyReview's quick checks, not the AI, because the AI review " +
                            "wasn't available. Submit again later for a full review.",
                        accent = status.warning,
                        container = status.warningContainer,
                    )
                }
                if (review.item.review.summary.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    NoticeCard(review.item.review.summary)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (shown.isEmpty()) {
                item {
                    Text(
                        if (review.findings.isEmpty()) "No findings for this file." else "Turn a filter on to see those findings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(shown, key = { it.id }, contentType = { "finding" }) { finding ->
                FindingCard(
                    finding = finding,
                    codeLines = review.codeLines,
                    expanded = finding.id in viewModel.expanded,
                    onToggle = { viewModel.toggleExpanded(finding.id) },
                    onLearnMore = { topic ->
                        val url = "https://docs.python.org/3/search.html?q=" + Uri.encode(topic)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            item(contentType = "label") { SectionLabel("Your code") }
            itemsIndexed(review.codeLines, key = { i, _ -> "line-$i" }, contentType = { _, _ -> "code" }) { i, line ->
                CodeRow(
                    number = i + 1,
                    text = line,
                    background = highlights[i + 1] ?: codeBackground,
                    marker = markers[i + 1] ?: Color.Transparent,
                )
            }

            item(contentType = "footer") {
                Spacer(Modifier.height(12.dp))
                if (review.item.review.reviewStatus != ReviewRepository.STATUS_QUICK_CHECK) {
                    NoticeCard(
                        "This feedback comes from AI. It's usually right but it can make mistakes, so think each point " +
                            "through and ask a senior developer if something doesn't make sense.",
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val current = loaded
    if (confirmDelete && current != null) {
        DeleteReviewDialog(
            item = current.item,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FindingCard(
    finding: Finding,
    codeLines: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLearnMore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val accent = status.accent(finding.severity)
    OutlinedPanel(modifier.animateContentSize()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onToggle),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${finding.severity.label.uppercase()} · ${finding.lineLabel.uppercase()} · ${finding.categoryLabel.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(finding.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Hide details" else "Show details",
                    )
                }
                if (expanded) {
                    if (finding.lineStart > 0 && codeLines.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        val first = (finding.lineStart - 1).coerceAtLeast(1)
                        val last = (finding.lineEnd + 1).coerceAtMost(codeLines.size)
                        CodeBlock(
                            lines = codeLines.subList(first - 1, last),
                            firstLineNumber = first,
                            highlights = (finding.lineStart..finding.lineEnd).associateWith { status.container(finding.severity) },
                            markers = (finding.lineStart..finding.lineEnd).associateWith { accent },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(finding.explanation, style = MaterialTheme.typography.bodyMedium)
                    finding.confidenceNotice?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                        )
                    }
                    if (finding.learnMoreTopic.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { onLearnMore(finding.learnMoreTopic) }, shape = RoundedCornerShape(8.dp)) {
                            Text("Read about ${finding.learnMoreTopic}")
                        }
                    }
                }
            }
        }
    }
}

private fun shareIntent(review: LoadedReview): Intent {
    val text = buildString {
        appendLine("PyReview: ${review.item.submission.fileName}")
        appendLine(friendlyDateTime(review.item.review.requestedAt))
        appendLine()
        review.findings.forEach { f ->
            appendLine("[${f.severity.label}] ${f.lineLabel}: ${f.title}")
            appendLine(f.explanation)
            appendLine()
        }
        append("Feedback from AI, so double-check each point before acting on it.")
    }
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "PyReview: ${review.item.submission.fileName}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
}
