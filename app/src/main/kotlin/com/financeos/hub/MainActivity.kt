package com.financeos.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.navigation.FosNavHost
import com.financeos.hub.ui.theme.FosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDeepRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepRoute = intent.getStringExtra(NotificationHelper.EXTRA_ROUTE)
        setContent {
            FosTheme {
                FosNavHost(initialDeepRoute = pendingDeepRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Deep-link from notification while app is already running is handled
        // by FosNavHost observing the activity's intent extras via LocalContext.
        // For simplicity we re-set the content with the new route.
        intent.getStringExtra(NotificationHelper.EXTRA_ROUTE)?.let { route ->
            pendingDeepRoute = route
            setContent {
                FosTheme {
                    FosNavHost(initialDeepRoute = route)
                }
            }
        }
    }
}
