package com.pyreview.app

import android.content.Context
import android.content.Intent
import com.pyreview.app.data.DraftStore
import com.pyreview.app.data.ReviewRepository
import com.pyreview.app.data.SessionRepository
import com.pyreview.app.data.StandardsRepository
import com.pyreview.app.data.auth.AuthRepository
import com.pyreview.app.data.cloud.CloudStore
import com.pyreview.app.data.local.AppDatabase
import com.pyreview.app.data.ocr.CodeRecognitionService
import com.pyreview.app.data.supabase.RemoteReviewService
import com.pyreview.app.data.supabase.SupabaseAuthRepository
import com.pyreview.app.data.supabase.SupabaseCloudStore
import com.pyreview.app.util.NetworkMonitor
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.seconds

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
        requestTimeout = 30.seconds
        install(Auth) {
            scheme = "pyreview"
            host = "auth"
        }
        install(Postgrest)
    }

    val database = AppDatabase.build(appContext)
    val network = NetworkMonitor(appContext)

    val auth: AuthRepository =
        SupabaseAuthRepository(supabase, scope, supportsGoogle = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank())

    private val cloud: CloudStore = SupabaseCloudStore(supabase)
    private val remote = RemoteReviewService(supabase, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    val session = SessionRepository(auth, database.userDao(), cloud, scope)
    val standards = StandardsRepository(cloud)
    val reviews = ReviewRepository(
        database.reviewDao(),
        remote,
        standards = { p -> cloud.applicableStandards(p.organizationId, p.departmentId) },
        network,
    )
    val recognition = CodeRecognitionService(appContext, remote, network)
    val draft = DraftStore()

    val passwordRecovery = MutableStateFlow(false)

    fun handleAuthLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "pyreview") return
        if ("type=recovery" in data.toString()) passwordRecovery.value = true
        supabase.handleDeeplinks(intent)
    }
}
