package com.smartdesk.mirrormobile.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdesk.mirrormobile.network.ConnectionManager
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenRemoteControl(
    connectionManager: ConnectionManager,
    onBack: () -> Unit
) {
    // -------------------------------------------
    // FORCE LANDSCAPE ONLY FOR THIS SCREEN
    // -------------------------------------------
    val activity = LocalContext.current as Activity

    LaunchedEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // -------------------------------------------
    // STATES
    // -------------------------------------------
    val screenFrame by connectionManager.screenFrame.collectAsState()
    var screenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var connectionError by remember { mutableStateOf(false) }
    var errorDetails by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    // -------------------------------------------
    // AUTO HIDE CONTROLS
    // -------------------------------------------
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // -------------------------------------------
    // CONVERT BASE64 TO BITMAP
    // -------------------------------------------
    LaunchedEffect(screenFrame) {
        if (screenFrame != null && screenFrame!!.startsWith("data:image")) {
            try {
                val base64Data = screenFrame!!.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                screenBitmap = bmp
                isLoading = false
            } catch (e: Exception) {
                connectionError = true
                errorDetails = "Decode error: ${e.message}"
            }
        }
    }

    // -------------------------------------------
    // AUTO REFRESH SCREEN
    // -------------------------------------------
    LaunchedEffect(Unit) {
        var err = 0
        while (true) {
            try {
                connectionManager.fetchScreen()
                err = 0
                connectionError = false
            } catch (e: Exception) {
                err++
                connectionError = true
                errorDetails = e.message ?: "Unknown error"
                if (err > 5) break
            }
            delay(500)
        }
    }

    // -------------------------------------------
    // UI
    // -------------------------------------------
    Scaffold(
        topBar = {
            if (showControls) {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Text(
                            text = if (isLoading) "Loading PC Screen..." else "PC Remote Control",
                            color = SmartDeskAccent,
                            fontSize = 18.sp
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                )
            }
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos ->
                            showControls = true

                            val relX = pos.x / size.width
                            val relY = pos.y / size.height

                            coroutineScope.launch {
                                connectionManager.executeCommand(
                                    "mouse_click",
                                    mapOf("button" to "left", "x" to "$relX", "y" to "$relY")
                                )

                                val t = System.currentTimeMillis()
                                if (t - lastTapTime < 300) {
                                    connectionManager.executeCommand(
                                        "mouse_double_click",
                                        mapOf("x" to "$relX", "y" to "$relY")
                                    )
                                }
                                lastTapTime = t
                            }
                        },
                        onLongPress = { pos ->
                            val relX = pos.x / size.width
                            val relY = pos.y / size.height
                            coroutineScope.launch {
                                connectionManager.executeCommand(
                                    "mouse_click",
                                    mapOf("button" to "right", "x" to "$relX", "y" to "$relY")
                                )
                            }
                        }
                    )
                }
        ) {

            // -------------------------------------------
            // SCREEN BITMAP
            // -------------------------------------------
            if (screenBitmap != null) {
                Image(
                    bitmap = screenBitmap!!.asImageBitmap(),
                    contentDescription = "PC Screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = SmartDeskAccent)
                    Text(
                        text = "Loading...",
                        color = SmartDeskAccent,
                        fontSize = 16.sp
                    )
                }
            }

            // -------------------------------------------
            // BOTTOM CONTROLS
            // -------------------------------------------
            if (showControls) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlActionButton(
                            icon = Icons.Default.TouchApp,
                            text = "Left Click"
                        ) {
                            coroutineScope.launch {
                                connectionManager.executeCommand("mouse_click", mapOf("button" to "left"))
                            }
                        }

                        ControlActionButton(
                            icon = Icons.Default.Computer,
                            text = "Right Click"
                        ) {
                            coroutineScope.launch {
                                connectionManager.executeCommand("mouse_click", mapOf("button" to "right"))
                            }
                        }

                        ControlActionButton(
                            icon = Icons.Default.TouchApp,
                            text = "Refresh"
                        ) {
                            coroutineScope.launch { connectionManager.fetchScreen() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = SmartDeskAccent,
            contentColor = Color.Black
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp))
            Text(text, fontSize = 12.sp)
        }
    }
}
