package com.pyreview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import com.pyreview.app.ui.auth.NewPasswordScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pyreview.app.R
import com.pyreview.app.appContainer
import com.pyreview.app.appViewModel
import com.pyreview.app.ui.auth.AuthViewModel
import com.pyreview.app.ui.auth.LoginScreen
import com.pyreview.app.ui.auth.RegisterScreen
import com.pyreview.app.ui.confirm.ConfirmScreen
import com.pyreview.app.ui.history.CompareScreen
import com.pyreview.app.ui.history.CompareViewModel
import com.pyreview.app.ui.history.HistoryScreen
import com.pyreview.app.ui.history.HistoryViewModel
import com.pyreview.app.ui.profile.MembersScreen
import com.pyreview.app.ui.profile.MembersViewModel
import com.pyreview.app.ui.profile.ProfileScreen
import com.pyreview.app.ui.profile.ProfileViewModel
import com.pyreview.app.ui.review.ResultsScreen
import com.pyreview.app.ui.review.ResultsViewModel
import com.pyreview.app.ui.review.ReviewingScreen
import com.pyreview.app.ui.review.ReviewingViewModel
import com.pyreview.app.ui.standards.StandardsScreen
import com.pyreview.app.ui.standards.StandardsViewModel
import com.pyreview.app.ui.submit.SubmitScreen
import com.pyreview.app.ui.submit.SubmitViewModel

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SUBMIT = "submit"
    const val HISTORY = "history"
    const val ARCHIVED = "history/archived"
    const val PROFILE = "profile"
    const val CONFIRM = "confirm"
    const val REVIEWING = "reviewing"
    const val REVIEW = "review/{reviewId}"
    const val COMPARE = "compare/{earlier}/{later}"
    const val STANDARDS = "standards"
    const val MEMBERS = "members"

    fun review(id: String) = "review/$id"
    fun compare(earlier: String, later: String) = "compare/$earlier/$later"
}

@Composable
fun PyReviewRoot() {
    val container = appContainer()
    val ready by container.auth.ready.collectAsStateWithLifecycle()
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val recovering by container.passwordRecovery.collectAsStateWithLifecycle()
    when {
        !ready -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        recovering && user != null -> NewPasswordScreen(appViewModel { AuthViewModel(it) }) {
            container.passwordRecovery.value = false
        }
        user == null -> AuthNavigation()
        else -> MainNavigation()
    }
}

@Composable
private fun AuthNavigation() {
    val nav = rememberNavController()
    val viewModel = appViewModel { AuthViewModel(it) }
    NavHost(nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(viewModel, onCreateAccount = { viewModel.clearMessages(); nav.navigate(Routes.REGISTER) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(viewModel, onBackToSignIn = { viewModel.clearMessages(); nav.popBackStack() })
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val container = appContainer()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val tabs = listOf(
        Tab(Routes.SUBMIT, "Submit") { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
        Tab(Routes.HISTORY, "History") { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
        Tab(Routes.PROFILE, "Profile") { Icon(rememberVectorPainter(Icons.Default.Person), contentDescription = null) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.SUBMIT,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.SUBMIT) {
                SubmitScreen(
                    viewModel = appViewModel { SubmitViewModel(it) },
                    onPhotosRead = { nav.navigate(Routes.CONFIRM) },
                    onSubmit = { nav.navigate(Routes.REVIEWING) },
                )
            }
            composable(Routes.CONFIRM) {
                ConfirmScreen(
                    onBack = { nav.popBackStack() },
                    onConfirm = { nav.navigate(Routes.REVIEWING) },
                )
            }
            composable(Routes.REVIEWING) {
                ReviewingScreen(
                    viewModel = appViewModel { ReviewingViewModel(it) },
                    onDone = { id ->
                        container.draft.startOver()
                        nav.navigate(Routes.review(id)) { popUpTo(Routes.SUBMIT) }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.REVIEW) { entry ->
                val id = entry.arguments?.getString("reviewId").orEmpty()
                ResultsScreen(
                    viewModel = appViewModel(key = "review-$id") { ResultsViewModel(it, id) },
                    onBack = { nav.popBackStack() },
                    onResubmit = { code, fileName ->
                        container.draft.startOver(code, fileName)
                        nav.switchTab(Routes.SUBMIT)
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = appViewModel { HistoryViewModel(it) },
                    onOpen = { nav.navigate(Routes.review(it)) },
                    onCompare = { earlier, later -> nav.navigate(Routes.compare(earlier, later)) },
                    onOpenArchived = { nav.navigate(Routes.ARCHIVED) },
                )
            }
            composable(Routes.ARCHIVED) {
                HistoryScreen(
                    viewModel = appViewModel(key = "history-archived") { HistoryViewModel(it, archivedOnly = true) },
                    onOpen = { nav.navigate(Routes.review(it)) },
                    onCompare = { _, _ -> },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.COMPARE) { entry ->
                val earlier = entry.arguments?.getString("earlier").orEmpty()
                val later = entry.arguments?.getString("later").orEmpty()
                CompareScreen(
                    viewModel = appViewModel(key = "compare-$earlier-$later") { CompareViewModel(it, earlier, later) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    viewModel = appViewModel { ProfileViewModel(it) },
                    onOpenStandards = { nav.navigate(Routes.STANDARDS) },
                    onOpenMembers = { nav.navigate(Routes.MEMBERS) },
                )
            }
            composable(Routes.MEMBERS) {
                MembersScreen(viewModel = appViewModel { MembersViewModel(it) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.STANDARDS) {
                StandardsScreen(viewModel = appViewModel { StandardsViewModel(it) }, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
