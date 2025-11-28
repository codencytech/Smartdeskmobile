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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
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

    // FULL IMMERSIVE MODE
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

    // Box size and image visible rect
    var boxWidth by remember { mutableStateOf(1f) }
    var boxHeight by remember { mutableStateOf(1f) }

    var imgLeft by remember { mutableStateOf(0f) }
    var imgTop by remember { mutableStateOf(0f) }
    var imgRight by remember { mutableStateOf(0f) }
    var imgBottom by remember { mutableStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    // Movement tuning & smoothing (A - accurate & smooth)
    val movementScale = 0.6f               // smaller scale for 1:1-like control
    val smoothingAlpha = 0.35f             // exponential smoothing factor (0..1)
    val deadzone = 0.6f                    // ignore very small moves (pixels)

    // Smoothing state
    var prevDx by remember { mutableStateOf(0f) }
    var prevDy by remember { mutableStateOf(0f) }

    // Pointer count watcher (so drag is single-finger only)
    var pointerCount by remember { mutableStateOf(0) }

    // Convert base64 → bitmap
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

    // Auto refresh stream
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
            // Only back button (transparent)
            Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.TopStart) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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

                    screenBitmap?.let { bmp ->
                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()
                        val boxRatio = boxWidth / boxHeight
                        val imgRatio = imgW / imgH

                        if (imgRatio > boxRatio) {
                            // image full width, letterbox top/bottom
                            val scaledHeight = boxWidth / imgRatio
                            imgLeft = 0f
                            imgRight = boxWidth
                            imgTop = (boxHeight - scaledHeight) / 2f
                            imgBottom = imgTop + scaledHeight
                        } else {
                            // image full height, pillarbox left/right
                            val scaledWidth = boxHeight * imgRatio
                            imgTop = 0f
                            imgBottom = boxHeight
                            imgLeft = (boxWidth - scaledWidth) / 2f
                            imgRight = imgLeft + scaledWidth
                        }
                    }
                }
                // pointer watcher to update pointerCount (for distinguishing multi-touch)
                .pointerInput(Unit) {
                    while (true) {
                        awaitPointerEventScope {
                            val event: PointerEvent = awaitPointerEvent()
                            pointerCount = event.changes.count { it.pressed }
                            // don't consume here; this is only a watcher
                        }
                    }
                }
                // two-finger scroll (transform gestures) -> only when pointerCount >= 2
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, _, _ ->
                        if (pointerCount >= 2 && (abs(pan.x) > 0f || abs(pan.y) > 0f)) {
                            coroutineScope.launch {
                                try {
                                    connectionManager.executeCommand(
                                        "mouse_scroll",
                                        mapOf("dx" to "${pan.x}", "dy" to "${pan.y}")
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                // single-finger drag (touchpad) -> only when pointerCount == 1
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { /* nothing */ },
                        onDrag = { change, dragAmount ->
                            // update pointerCount was done by watcher; ensure single-finger
                            if (pointerCount != 1) return@detectDragGestures

                            // ensure drag starts inside image area (Option 1: gestures restricted to image)
                            val p = change.position
                            if (p.x !in imgLeft..imgRight || p.y !in imgTop..imgBottom) return@detectDragGestures

                            // consume change so taps won't fire
                            change.consume()

                            val targetDx = dragAmount.x * movementScale
                            val targetDy = dragAmount.y * movementScale

                            // smoothing
                            val smoothDx = prevDx * (1 - smoothingAlpha) + targetDx * smoothingAlpha
                            val smoothDy = prevDy * (1 - smoothingAlpha) + targetDy * smoothingAlpha

                            // deadzone to avoid twitch
                            val sendDx = if (abs(smoothDx) < deadzone) 0f else smoothDx
                            val sendDy = if (abs(smoothDy) < deadzone) 0f else smoothDy

                            // update prev only with the smoothed values (so smoothing continues)
                            prevDx = smoothDx
                            prevDy = smoothDy

                            // send command if movement is meaningful
                            if (abs(sendDx) >= 0.01f || abs(sendDy) >= 0.01f) {
                                coroutineScope.launch {
                                    try {
                                        connectionManager.executeCommand(
                                            "mouse_move_relative",
                                            mapOf("dx" to "$sendDx", "dy" to "$sendDy")
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            // reset smoothing state slightly to avoid jump next drag
                            prevDx = 0f
                            prevDy = 0f
                        },
                        onDragCancel = {
                            prevDx = 0f
                            prevDy = 0f
                        }
                    )
                }
                // tap gestures -> only when NOT dragging (drag consumes events) and only inside image
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos: Offset ->
                            // ignore taps outside image
                            if (pos.x !in imgLeft..imgRight || pos.y !in imgTop..imgBottom) return@detectTapGestures
                            // ignore if currently multi-touch or dragging
                            if (pointerCount != 1) return@detectTapGestures

                            val relX = ((pos.x - imgLeft) / (imgRight - imgLeft)).coerceIn(0f, 1f)
                            val relY = ((pos.y - imgTop) / (imgBottom - imgTop)).coerceIn(0f, 1f)

                            coroutineScope.launch {
                                try {
                                    connectionManager.executeCommand("mouse_move", mapOf("x" to "$relX", "y" to "$relY"))
                                } catch (_: Exception) {
                                }
                            }
                        },
                        onDoubleTap = { pos: Offset ->
                            if (pos.x !in imgLeft..imgRight || pos.y !in imgTop..imgBottom) return@detectTapGestures
                            if (pointerCount != 1) return@detectTapGestures

                            val relX = ((pos.x - imgLeft) / (imgRight - imgLeft)).coerceIn(0f, 1f)
                            val relY = ((pos.y - imgTop) / (imgBottom - imgTop)).coerceIn(0f, 1f)

                            coroutineScope.launch {
                                try {
                                    connectionManager.executeCommand("mouse_move", mapOf("x" to "$relX", "y" to "$relY"))
                                    connectionManager.executeCommand("mouse_click", mapOf("button" to "left"))
                                } catch (_: Exception) {
                                }
                            }
                        },
                        onLongPress = { pos: Offset ->
                            if (pos.x !in imgLeft..imgRight || pos.y !in imgTop..imgBottom) return@detectTapGestures
                            if (pointerCount != 1) return@detectTapGestures

                            val relX = ((pos.x - imgLeft) / (imgRight - imgLeft)).coerceIn(0f, 1f)
                            val relY = ((pos.y - imgTop) / (imgBottom - imgTop)).coerceIn(0f, 1f)

                            coroutineScope.launch {
                                try {
                                    connectionManager.executeCommand("mouse_move", mapOf("x" to "$relX", "y" to "$relY"))
                                    connectionManager.executeCommand("mouse_click", mapOf("button" to "right"))
                                } catch (_: Exception) {
                                }
                            }
                        }
                    )
                }
        ) {

            if (screenBitmap != null) {
                Image(
                    bitmap = screenBitmap!!.asImageBitmap(),
                    contentDescription = "PC Screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
