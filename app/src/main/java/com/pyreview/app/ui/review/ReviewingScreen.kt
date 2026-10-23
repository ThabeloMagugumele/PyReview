package com.pyreview.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.data.RateLimitedException
import com.pyreview.app.data.ServiceException
import com.pyreview.app.ui.auth.PrimaryButton
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ReviewingState {
    data object Running : ReviewingState

    data class Failed(val message: String, val offerQuickChecks: Boolean = false) : ReviewingState
    data class Done(val reviewId: String) : ReviewingState
}

class ReviewingViewModel(private val container: AppContainer) : ViewModel() {
    var state by mutableStateOf<ReviewingState>(ReviewingState.Running)
        private set

    init {
        start()
    }

    fun start() {
        state = ReviewingState.Running
        viewModelScope.launch {
            state = try {
                val profile = container.session.profile.filterNotNull().first()
                ReviewingState.Done(container.reviews.submit(profile, container.draft.toRequest()))
            } catch (e: RateLimitedException) {
                ReviewingState.Failed(e.message.orEmpty(), offerQuickChecks = true)
            } catch (e: ServiceException) {
                ReviewingState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                ReviewingState.Failed("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun runQuickChecks() {
        state = ReviewingState.Running
        viewModelScope.launch {
            state = try {
                val profile = container.session.profile.filterNotNull().first()
                ReviewingState.Done(container.reviews.submitQuickChecks(profile, container.draft.toRequest()))
            } catch (e: Exception) {
                ReviewingState.Failed("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

private val STAGES = listOf(
    "Checking syntax…",
    "Looking for bugs and logic problems…",
    "Checking for security issues…",
    "Comparing with your team's coding standards…",
    "Writing beginner-friendly explanations…",
)

@Composable
fun ReviewingScreen(viewModel: ReviewingViewModel, onDone: (String) -> Unit, onBack: () -> Unit) {
    val state = viewModel.state
    LaunchedEffect(state) {
        if (state is ReviewingState.Done) onDone(state.reviewId)
    }
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        while (state is ReviewingState.Running) {
            delay(3_000)
            stage = (stage + 1) % STAGES.size
        }
    }

    ScreenScaffold(title = "Review", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                ReviewingState.Running, is ReviewingState.Done -> {
                    CircularProgressIndicator(Modifier.size(48.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("STEP 3 OF 3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reviewing your code", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        STAGES[stage],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "This normally takes less than a minute. Your code won't be changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                is ReviewingState.Failed -> {
                    val colors = LocalStatusColors.current
                    Text("The review didn't finish", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    NoticeCard(state.message, accent = colors.error, container = colors.errorContainer)
                    Spacer(Modifier.height(24.dp))
                    if (state.offerQuickChecks) {
                        PrimaryButton("Run quick checks instead", onClick = viewModel::runQuickChecks)
                        Text(
                            "Quick checks run on your phone and pick up common mistakes, but they're not as thorough as the AI review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        )
                        TextButton(onClick = viewModel::start) { Text("Try the AI review again") }
                    } else {
                        PrimaryButton("Try again", onClick = viewModel::start)
                    }
                    TextButton(onClick = onBack) { Text("Back to my code") }
                }
            }
        }
    }
}
