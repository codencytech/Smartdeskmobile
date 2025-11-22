package com.smartdesk.mirrormobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smartdesk.mirrormobile.network.ConnectionManager
import com.smartdesk.mirrormobile.network.ConnectionState
import com.smartdesk.mirrormobile.ui.screens.ConnectScreen
import com.smartdesk.mirrormobile.ui.screens.HomeScreen
import com.smartdesk.mirrormobile.ui.theme.SmartDeskDark
import com.smartdesk.mirrormobile.ui.theme.SmartDeskMirrorMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartDeskMirrorMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SmartDeskDark
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Connect) }
    val connectionManager = remember { ConnectionManager() }
    val connectionState by connectionManager.connectionState.collectAsState()

    // Auto-navigate to home when connected
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED && currentScreen == AppScreen.Connect) {
            currentScreen = AppScreen.Home
        }
    }

    when (currentScreen) {
        AppScreen.Connect -> {
            ConnectScreen(
                connectionManager = connectionManager,
                onBack = { /* Stay on connect screen */ },
                onConnected = { currentScreen = AppScreen.Home }
            )
        }
        AppScreen.Home -> {
            HomeScreen(
                connectionManager = connectionManager,
                onConnectPC = { currentScreen = AppScreen.Connect },
                onStartVoice = {
                    // TODO: Implement voice AI screen
                },
                onOpenSettings = {
                    // TODO: Implement settings screen
                }
            )
        }
    }
}

sealed class AppScreen {
    object Connect : AppScreen()
    object Home : AppScreen()
}