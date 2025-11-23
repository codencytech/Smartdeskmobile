// File: QrScannerScreen.kt
package com.smartdesk.mirrormobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent2
import com.smartdesk.mirrormobile.ui.theme.SmartDeskDark
import com.smartdesk.mirrormobile.ui.theme.SmartDeskMuted
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onQrScanned: (ipAddress: String, connectionCode: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scanResult by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    // Request camera permission if not granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handle scan result
    LaunchedEffect(scanResult) {
        scanResult?.let { result ->
            if (!isProcessing) {
                isProcessing = true

                // Parse QR code data (expected format: "ip:port:code" or "ip:code")
                val parts = result.split(":")
                when (parts.size) {
                    2 -> {
                        // Format: ip:code
                        val ip = parts[0]
                        val code = parts[1]
                        onQrScanned(ip, code)
                    }
                    3 -> {
                        // Format: ip:port:code
                        val ip = parts[0]
                        val port = parts[1]
                        val code = parts[2]
                        onQrScanned("$ip:$port", code)
                    }
                    else -> {
                        // Try to extract IP and code from any format
                        val ipRegex = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""".toRegex()
                        val codeRegex = """(\d{6})""".toRegex()

                        val ipMatch = ipRegex.find(result)
                        val codeMatch = codeRegex.find(result)

                        if (ipMatch != null && codeMatch != null) {
                            onQrScanned(ipMatch.value, codeMatch.value)
                        } else {
                            // Show error and go back after delay
                            delay(2000)
                            onBack()
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Scan QR Code",
                        color = SmartDeskAccent2,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SmartDeskDark
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SmartDeskDark)
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasCameraPermission) {
                // Permission denied state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = "Camera Permission",
                        tint = SmartDeskMuted,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Camera Permission Required",
                        color = SmartDeskAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SmartDesk Mirror needs camera access to scan QR codes from your PC.",
                        color = SmartDeskMuted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SmartDeskAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "GRANT CAMERA ACCESS",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isProcessing) {
                // Processing scan result
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = SmartDeskAccent,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Processing QR Code...",
                        color = SmartDeskAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting to PC...",
                        color = SmartDeskMuted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Camera preview with QR scanning
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Point camera at QR code on PC",
                        color = SmartDeskMuted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )

                    // Camera preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CameraPreview(
                            onQrCodeScanned = { qrData ->
                                if (!isProcessing) {
                                    scanResult = qrData
                                }
                            }
                        )

                        // QR frame overlay
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .align(Alignment.Center)
                        ) {
                            // Simple border for QR frame
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                drawRect(
                                    color = SmartDeskAccent,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                                )
                            }
                        }
                    }

                    // Instructions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "QR Code Location:",
                                color = SmartDeskAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Look for the QR code in the PC app\n• Make sure the code is clearly visible\n• Hold steady for automatic scan",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Set up preview use case
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Set up image analysis for QR code scanning
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val barcodeScanner = BarcodeScanning.getClient()

                imageAnalysis.setAnalyzer(
                    Executors.newSingleThreadExecutor()
                ) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    when (barcode.valueType) {
                                        Barcode.TYPE_TEXT -> {
                                            barcode.rawValue?.let { qrData ->
                                                onQrCodeScanned(qrData)
                                            }
                                        }
                                        Barcode.TYPE_URL -> {
                                            barcode.url?.url?.let { url ->
                                                onQrCodeScanned(url.toString())
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                // Bind use cases to camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}