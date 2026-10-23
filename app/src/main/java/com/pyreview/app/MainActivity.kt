package com.pyreview.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pyreview.app.ui.PyReviewRoot
import com.pyreview.app.ui.theme.PyReviewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as PyReviewApp).container.handleAuthLink(intent)
        setContent {
            PyReviewTheme {
                PyReviewRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as PyReviewApp).container.handleAuthLink(intent)
    }
}
