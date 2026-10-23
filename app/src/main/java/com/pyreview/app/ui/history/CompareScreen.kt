package com.pyreview.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.data.review.Finding
import com.pyreview.app.data.review.FindingsJson
import com.pyreview.app.data.review.ReviewComparison
import com.pyreview.app.data.review.Severity
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OutlinedPanel
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.components.SectionLabel
import com.pyreview.app.ui.components.SeverityDot
import com.pyreview.app.ui.review.LoadedReview
import com.pyreview.app.ui.theme.LocalStatusColors
import com.pyreview.app.util.friendlyDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class CompareViewModel(container: AppContainer, earlierId: String, laterId: String) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val pair: StateFlow<Pair<LoadedReview, LoadedReview>?> = container.session.profile
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(null)
            } else {
                combine(
                    container.reviews.review(earlierId, profile.userId),
                    container.reviews.review(laterId, profile.userId),
                ) { a, b ->
                    if (a == null || b == null) null
                    else LoadedReview(a, FindingsJson.decode(a.review.findingsJson)) to LoadedReview(b, FindingsJson.decode(b.review.findingsJson))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun CompareScreen(viewModel: CompareViewModel, onBack: () -> Unit) {
    val pair by viewModel.pair.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Compare reviews", onBack = onBack) { padding ->
        val (earlier, later) = pair ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val comparison = ReviewComparison.of(earlier.findings, later.findings)
        val status = LocalStatusColors.current

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewHeader("Earlier", earlier, Modifier.weight(1f))
                ReviewHeader("Later", later, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            val improved = comparison.errorChange < 0 || (comparison.errorChange == 0 && comparison.warningChange < 0)
            NoticeCard(
                comparison.verdict,
                accent = if (improved) status.good else status.warning,
                container = if (improved) status.goodContainer else MaterialTheme.colorScheme.surfaceVariant,
            )

            SectionLabel("Findings by severity")
            OutlinedPanel {
                Column(Modifier.padding(12.dp)) {
                    Severity.entries.reversed().forEachIndexed { i, severity ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        val before = earlier.findings.count { it.severity == severity }
                        val after = later.findings.count { it.severity == severity }
                        CountRow(severity, before, after)
                    }
                }
            }

            FindingGroup("Fixed since the earlier review", comparison.fixed, status.good)
            FindingGroup("Still there", comparison.stillThere, status.warning)
            FindingGroup("New in the later review", comparison.new, status.error)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReviewHeader(label: String, review: LoadedReview, modifier: Modifier) {
    OutlinedPanel(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(review.item.submission.fileName, style = MaterialTheme.typography.titleSmall)
            Text(friendlyDateTime(review.item.review.requestedAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CountRow(severity: Severity, before: Int, after: Int) {
    val status = LocalStatusColors.current
    val change = after - before
    val better = if (severity == Severity.GOOD) change > 0 else change < 0
    val changeColor = when {
        change == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        better -> status.good
        else -> status.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeverityDot(severity)
        Spacer(Modifier.width(8.dp))
        Text(severity.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$before → $after", style = MaterialTheme.typography.bodyMedium)
        Text(
            when {
                change > 0 -> "+$change"
                change < 0 -> "$change"
                else -> "±0"
            },
            style = MaterialTheme.typography.labelLarge,
            color = changeColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun FindingGroup(title: String, findings: List<Finding>, accent: Color) {
    if (findings.isEmpty()) return
    SectionLabel("$title (${findings.size})")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        findings.forEach { f ->
            OutlinedPanel {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeverityDot(f.severity)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.bodyMedium)
                        Text("${f.categoryLabel} · ${f.lineLabel}", style = MaterialTheme.typography.bodySmall, color = accent)
                    }
                }
            }
        }
    }
}
