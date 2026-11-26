package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.auth.TikTokOAuthCallbackBus
import com.example.firebase.configureAppCheck
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.PlanetStreamingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)
        TikTokOAuthCallbackBus.emitIfTikTokCallback(intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            PlanetStreamingTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TikTokOAuthCallbackBus.emitIfTikTokCallback(intent)
    }
}
