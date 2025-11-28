package com.smartdesk.mirrormobile.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartdesk.mirrormobile.network.ConnectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenRemoteControl(
    connectionManager: ConnectionManager,
    onBack: () -> Unit
) {
    val activity = LocalContext.current as Activity

    // FULL REAL IMMERSIVE MODE
    LaunchedEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    val screenFrame by connectionManager.screenFrame.collectAsState()
    var screenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var connectionError by remember { mutableStateOf(false) }
    var errorDetails by remember { mutableStateOf("") }

    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var boxWidth by remember { mutableStateOf(1f) }
    var boxHeight by remember { mutableStateOf(1f) }

    // Image actual visible area
    var imgLeft by remember { mutableStateOf(0f) }
    var imgTop by remember { mutableStateOf(0f) }
    var imgRight by remember { mutableStateOf(0f) }
    var imgBottom by remember { mutableStateOf(0f) }

    val movementScale = 2f

    // Base64 decode
    LaunchedEffect(screenFrame) {
        if (screenFrame != null && screenFrame!!.startsWith("data:image")) {
            try {
                val pureBase64 = screenFrame!!.substringAfter("base64,")
                val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
                screenBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                isLoading = false
            } catch (e: Exception) {
                connectionError = true
                errorDetails = e.message ?: "Error"
            }
        }
    }

    // Auto refresh
    LaunchedEffect(Unit) {
        var errors = 0
        while (true) {
            try {
                connectionManager.fetchScreen()
                connectionError = false
                errors = 0
            } catch (e: Exception) {
                errors++
                connectionError = true
                errorDetails = e.message ?: "Unknown"
                if (errors > 5) break
            }
            delay(500)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            // ONLY BACK BUTTON, NO TITLE
            Box(
                modifier = Modifier
                    .padding(10.dp),
                contentAlignment = Alignment.TopStart
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .onSizeChanged { size ->
                    boxWidth = size.width.toFloat()
                    boxHeight = size.height.toFloat()

                    // Calculate visible image area for ContentScale.Fit
                    screenBitmap?.let { bmp ->
                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()
                        val boxRatio = boxWidth / boxHeight
                        val imgRatio = imgW / imgH

                        if (imgRatio > boxRatio) {
                            // Image fills width, vertical bars
                            val scaledHeight = boxWidth / imgRatio
                            imgLeft = 0f
                            imgRight = boxWidth
                            imgTop = (boxHeight - scaledHeight) / 2f
                            imgBottom = imgTop + scaledHeight
                        } else {
                            // Image fills height, horizontal bars
                            val scaledWidth = boxHeight * imgRatio
                            imgTop = 0f
                            imgBottom = boxHeight
                            imgLeft = (boxWidth - scaledWidth) / 2f
                            imgRight = imgLeft + scaledWidth
                        }
                    }
                }
                // SCROLL (two fingers)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, _, _ ->
                        if (abs(pan.x) > 0 || abs(pan.y) > 0) {
                            coroutineScope.launch {
                                connectionManager.executeCommand(
                                    "mouse_scroll",
                                    mapOf("dx" to "${pan.x}", "dy" to "${pan.y}")
                                )
                            }
                        }
                    }
                }
                // DRAG MOVE
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, drag ->
                            change.consume()

                            // Only respond if drag is inside video
                            val p = change.position
                            if (p.x !in imgLeft..imgRight || p.y !in imgTop..imgBottom) return@detectDragGestures

                            coroutineScope.launch {
                                connectionManager.executeCommand(
                                    "mouse_move_relative",
                                    mapOf(
                                        "dx" to "${drag.x * movementScale}",
                                        "dy" to "${drag.y * movementScale}"
                                    )
                                )
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
                // TAP / DOUBLE TAP / LONG PRESS -> ONLY inside video
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos ->
                            if (pos.x !in imgLeft..imgRight || pos.y !in imgTop..imgBottom) return@detectTapGestures

                            val relX = ((pos.x - imgLeft) / (imgRight - imgLeft)).coerceIn(0f, 1f)
                            val relY = ((pos.y - imgTop) / (imgBottom - imgTop)).coerceIn(0f, 1f)

                            coroutineScope.launch {
                                connectionManager.executeCommand("mouse_move", mapOf("x" to "$relX", "y" to "$relY"))
                            }
                        },
                        onDoubleTap = { pos ->
                            if (pos.x !in imgLeft..imgRight || pos.y !in imgTop..imgBottom) return@detectTapGestures

                            val relX = ((pos.x - imgLeft) / (imgRight - imgLeft)).coerceIn(0f, 1f)
                            val relY = ((pos.y - imgTop) / (imgBottom - imgTop)).coerceIn(0f, 1f)

                            coroutineScope.launch {
                                connectionManager.executeCommand("mouse_move", mapOf("x" to "$relX", "y" to "$relY"))
                                connectionManager.executeCommand("mouse_click", mapOf("button" to "left"))
                            }
                        },
                        onLongPress = { pos ->
                            if (pos.x !in imgLeft..imgRight || pos.y !in imgTop..imgBottom) return@detectTapGestures

                            val relX = ((pos.x - imgLeft) / (imgRight - imgLeft)).coerceIn(0f, 1f)
                            val relY = ((pos.y - imgTop) / (imgBottom - imgTop)).coerceIn(0f, 1f)

                            coroutineScope.launch {
                                connectionManager.executeCommand("mouse_move", mapOf("x" to "$relX", "y" to "$relY"))
                                connectionManager.executeCommand("mouse_click", mapOf("button" to "right"))
                            }
                        }
                    )
                }
        ) {

            if (screenBitmap != null) {
                Image(
                    bitmap = screenBitmap!!.asImageBitmap(),
                    contentDescription = "",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
