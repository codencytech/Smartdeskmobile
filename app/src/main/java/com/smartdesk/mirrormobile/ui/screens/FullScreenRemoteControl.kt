// File: FullScreenRemoteControl.kt
package com.smartdesk.mirrormobile.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val coroutineScope = rememberCoroutineScope()
    val screenFrame by connectionManager.screenFrame.collectAsState()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var connectionError by remember { mutableStateOf(false) }
    var errorDetails by remember { mutableStateOf("") }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Convert base64 data URL to bitmap when screenFrame changes
    LaunchedEffect(screenFrame) {
        if (screenFrame != null && screenFrame!!.isNotEmpty() && screenFrame!!.startsWith("data:image")) {
            try {
                isLoading = true
                Log.d("FullScreenRemote", "Converting base64 to bitmap...")

                // Extract base64 data from data URL
                val base64Data = screenFrame!!.substringAfter("base64,")

                // Decode base64 to byte array
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)

                // Create bitmap from byte array
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                if (bitmap != null) {
                    currentBitmap = bitmap
                    isLoading = false
                    connectionError = false
                    Log.d("FullScreenRemote", "✅ Bitmap created successfully: ${bitmap.width}x${bitmap.height}")
                } else {
                    errorDetails = "Failed to decode bitmap from base64 data"
                    Log.e("FullScreenRemote", "❌ Failed to decode bitmap")
                }
            } catch (e: Exception) {
                errorDetails = "Base64 conversion error: ${e.message}"
                Log.e("FullScreenRemote", "❌ Error converting base64: ${e.message}")
                connectionError = true
            }
        }
    }

    // Auto-refresh screen with error handling
    LaunchedEffect(Unit) {
        var errorCount = 0
        while (true) {
            try {
                connectionManager.fetchScreen()
                errorCount = 0 // Reset error count on success
            } catch (e: Exception) {
                errorCount++
                connectionError = true
                errorDetails = "Fetch error: ${e.message}"
                Log.e("FullScreenRemote", "Screen fetch error: ${e.message}")
                if (errorCount > 5) {
                    break
                }
            }
            delay(500) // Refresh every 500ms
        }
    }

    Scaffold(
        topBar = {
            if (showControls) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when {
                                connectionError -> "Connection Issue"
                                isLoading -> "Loading PC Screen..."
                                else -> "PC Remote Control"
                            },
                            color = when {
                                connectionError -> Color.Red
                                isLoading -> Color.Yellow
                                else -> SmartDeskAccent
                            },
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showControls = !showControls }) {
                            Icon(Icons.Default.TouchApp, contentDescription = "Toggle Controls")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (!showControls) {
                FloatingActionButton(
                    onClick = { showControls = true },
                    containerColor = SmartDeskAccent,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = "Show Controls")
                }
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
                        onTap = { offset ->
                            showControls = true
                            val relativeX = offset.x / size.width
                            val relativeY = offset.y / size.height

                            coroutineScope.launch {
                                connectionManager.executeCommand(
                                    "mouse_click",
                                    mapOf(
                                        "button" to "left",
                                        "x" to relativeX.toString(),
                                        "y" to relativeY.toString()
                                    )
                                )

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastTapTime < 300) {
                                    connectionManager.executeCommand(
                                        "mouse_double_click",
                                        mapOf(
                                            "x" to relativeX.toString(),
                                            "y" to relativeY.toString()
                                        )
                                    )
                                }
                                lastTapTime = currentTime
                            }
                        },
                        onLongPress = { offset ->
                            val relativeX = offset.x / size.width
                            val relativeY = offset.y / size.height

                            coroutineScope.launch {
                                connectionManager.executeCommand(
                                    "mouse_click",
                                    mapOf(
                                        "button" to "right",
                                        "x" to relativeX.toString(),
                                        "y" to relativeY.toString()
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
            // Screen Preview - Using bitmap directly
            if (currentBitmap != null && !isLoading) {
                androidx.compose.foundation.Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = "PC Screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Log.d("FullScreenRemote", "🖼️ Displaying bitmap: ${currentBitmap!!.width}x${currentBitmap!!.height}")
            } else {
                // Loading or error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when {
                            connectionError -> {
                                // Connection error
                                Icon(
                                    Icons.Default.Computer,
                                    contentDescription = "Connection Error",
                                    tint = Color.Red,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Screen Stream Error",
                                    color = Color.Red,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Check PC connection and try again",
                                    color = SmartDeskAccent,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                if (errorDetails.isNotEmpty()) {
                                    Text(
                                        text = errorDetails,
                                        color = SmartDeskAccent,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            screenFrame != null && screenFrame!!.isNotEmpty() -> {
                                // Processing data
                                CircularProgressIndicator(color = SmartDeskAccent)
                                Text(
                                    text = "Processing PC Screen...",
                                    color = SmartDeskAccent,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Data received, converting to image...",
                                    color = SmartDeskAccent,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            else -> {
                                // Loading state
                                CircularProgressIndicator(color = SmartDeskAccent)
                                Text(
                                    text = "Waiting for PC Screen...",
                                    color = SmartDeskAccent,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Bottom controls bar (when visible)
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
                            text = "Left Click",
                            onClick = {
                                coroutineScope.launch {
                                    connectionManager.executeCommand(
                                        "mouse_click",
                                        mapOf("button" to "left")
                                    )
                                }
                            }
                        )

                        ControlActionButton(
                            icon = Icons.Default.Computer,
                            text = "Right Click",
                            onClick = {
                                coroutineScope.launch {
                                    connectionManager.executeCommand(
                                        "mouse_click",
                                        mapOf("button" to "right")
                                    )
                                }
                            }
                        )

                        ControlActionButton(
                            icon = Icons.Default.TouchApp,
                            text = "Refresh",
                            onClick = {
                                coroutineScope.launch {
                                    connectionManager.fetchScreen()
                                }
                            }
                        )
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}