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
import com.smartdesk.mirrormobile.ui.screens.FullScreenRemoteControl
import com.smartdesk.mirrormobile.ui.screens.HomeScreen
import com.smartdesk.mirrormobile.ui.screens.QrScannerScreen
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
    //--------- SCREEN STATE ----------
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Connect) }

    // For scanned QR values
    var scannedIpAddress by remember { mutableStateOf("") }
    var scannedConnectionCode by remember { mutableStateOf("") }

    //--------- SINGLE GLOBAL CONNECTION MANAGER ----------
    // This survives navigation & keeps the stream alive globally.
    val connectionManager = remember { ConnectionManager() }

    // Observe connection state
    val connectionState by connectionManager.connectionState.collectAsState()

    //--------- AUTO NAVIGATION TO HOME WHEN CONNECTED ----------
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED &&
            (currentScreen == AppScreen.Connect || currentScreen == AppScreen.QrScanner)
        ) {
            currentScreen = AppScreen.Home
        }
    }

    //--------- SCREEN ROUTES ----------
    when (currentScreen) {

        AppScreen.Connect -> {
            ConnectScreen(
                connectionManager = connectionManager,
                onBack = { /* Already on connect */ },
                onConnected = {
                    currentScreen = AppScreen.Home
                },
                onOpenQrScanner = {
                    currentScreen = AppScreen.QrScanner
                },
                prefillIp = scannedIpAddress,
                prefillCode = scannedConnectionCode
            )
        }

        AppScreen.Home -> {
            HomeScreen(
                connectionManager = connectionManager,
                onConnectPC = {
                    scannedIpAddress = ""
                    scannedConnectionCode = ""
                    currentScreen = AppScreen.Connect
                },
                onStartFullScreenRemoteControl = {
                    currentScreen = AppScreen.FullScreenRemoteControl
                },
                onStartVoice = {
                    // TODO voice AI
                },
                onOpenSettings = {
                    // TODO settings
                }
            )
        }

        AppScreen.QrScanner -> {
            QrScannerScreen(
                onBack = { currentScreen = AppScreen.Connect },
                onQrScanned = { ip, code ->
                    scannedIpAddress = ip
                    scannedConnectionCode = code
                    currentScreen = AppScreen.Connect
                }
            )
        }

        AppScreen.FullScreenRemoteControl -> {
            FullScreenRemoteControl(
                connectionManager = connectionManager,
                onBack = {
                    currentScreen = AppScreen.Home
                }
            )
        }
    }
}

//--------- SCREEN ENUM ----------
sealed class AppScreen {
    object Connect : AppScreen()
    object Home : AppScreen()
    object QrScanner : AppScreen()
    object FullScreenRemoteControl : AppScreen()
}
