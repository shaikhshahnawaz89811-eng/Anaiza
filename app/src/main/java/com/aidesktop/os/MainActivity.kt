package com.aidesktop.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aidesktop.os.ui.navigation.AppNavHost
import com.aidesktop.os.ui.theme.AiDesktopOsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity entry point. Everything — the Home screen, the in-app
 * desktop, every mini app window — is rendered here inside this Activity's
 * own window. There is no SYSTEM_ALERT_WINDOW overlay, no accessibility
 * service, and no background service running with elevated permissions.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiDesktopOsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
