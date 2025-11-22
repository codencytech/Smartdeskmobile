package com.smartdesk.mirrormobile.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdesk.mirrormobile.network.ConnectionManager
import com.smartdesk.mirrormobile.network.ConnectionState
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent2
import com.smartdesk.mirrormobile.ui.theme.SmartDeskCard
import com.smartdesk.mirrormobile.ui.theme.SmartDeskDark
import com.smartdesk.mirrormobile.ui.theme.SmartDeskMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectionManager: ConnectionManager,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    var connectionCode by remember { mutableStateOf("") }
    var pcIpAddress by remember { mutableStateOf("192.168.1.100") } // Default IP
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val connectionState by connectionManager.connectionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Navigate to home when connected
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            onConnected()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Connect to PC",
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
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Connection Illustration
            Icon(
                Icons.Default.QrCode,
                contentDescription = "QR Code",
                tint = SmartDeskAccent,
                modifier = Modifier.size(80.dp)
            )

            Text(
                text = "Enter Connection Details",
                color = SmartDeskAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // PC IP Address Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PC IP Address",
                    color = SmartDeskMuted,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = pcIpAddress,
                    onValueChange = { pcIpAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.100") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SmartDeskMuted,
                        unfocusedTextColor = SmartDeskMuted,
                        focusedBorderColor = SmartDeskAccent,
                        unfocusedBorderColor = SmartDeskMuted,
                        cursorColor = SmartDeskAccent,
                        focusedLabelColor = SmartDeskAccent,
                        unfocusedLabelColor = SmartDeskMuted,
                        focusedPlaceholderColor = SmartDeskMuted.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = SmartDeskMuted.copy(alpha = 0.7f)
                    )
                )
                Text(
                    text = "Find PC IP: Run 'ipconfig' in Command Prompt",
                    color = SmartDeskMuted.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Connection Code Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "6-Digit Connection Code",
                    color = SmartDeskMuted,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = connectionCode,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            connectionCode = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SmartDeskMuted,
                        unfocusedTextColor = SmartDeskMuted,
                        focusedBorderColor = SmartDeskAccent,
                        unfocusedBorderColor = SmartDeskMuted,
                        cursorColor = SmartDeskAccent,
                        focusedLabelColor = SmartDeskAccent,
                        unfocusedLabelColor = SmartDeskMuted,
                        focusedPlaceholderColor = SmartDeskMuted.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = SmartDeskMuted.copy(alpha = 0.7f)
                    )
                )
                Text(
                    text = "Find this code on your PC screen",
                    color = SmartDeskMuted.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Status Display
            when (connectionState) {
                ConnectionState.CONNECTING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = SmartDeskAccent)
                        Text(
                            text = "Waiting for PC approval...",
                            color = SmartDeskMuted,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Check your PC for connection request",
                            color = SmartDeskMuted.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                ConnectionState.ERROR -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "❌ Connection Failed",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = errorMessage ?: "Check IP address and code",
                            color = SmartDeskMuted,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Make sure:\n• PC app is running\n• Correct IP address\n• Codes match\n• Same WiFi network",
                            color = SmartDeskMuted.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    // Show connection instructions when idle
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How to connect:",
                            color = SmartDeskAccent,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "1. Find PC IP (ipconfig)\n2. Enter 6-digit code from PC\n3. Click Connect\n4. Accept on PC",
                            color = SmartDeskMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Connect Button
            Button(
                onClick = {
                    if (connectionCode.length != 6) {
                        errorMessage = "Please enter a 6-digit code"
                        return@Button
                    }

                    if (pcIpAddress.isBlank()) {
                        errorMessage = "Please enter PC IP address"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    coroutineScope.launch {
                        try {
                            // Test connection first
                            Log.d("ConnectScreen", "Testing connection to: $pcIpAddress")
                            val connectionTest = connectionManager.testConnection(pcIpAddress)

                            if (!connectionTest) {
                                errorMessage = "Cannot connect to PC at $pcIpAddress\n\nMake sure:\n• PC app is running\n• Correct IP address\n• Same WiFi network\n• Port 8000 is open"
                                isLoading = false
                                return@launch
                            }

                            // Update base URL with provided IP
                            connectionManager.updateBaseUrl(pcIpAddress)

                            // Attempt connection
                            Log.d("ConnectScreen", "Starting connection with code: $connectionCode")
                            val success = connectionManager.connectWithCode(connectionCode)

                            if (!success) {
                                errorMessage = "Connection failed.\n\nPossible issues:\n• Wrong connection code\n• PC rejected connection\n• Network firewall blocking\n\nCheck PC for connection request"
                            }
                        } catch (e: Exception) {
                            Log.e("ConnectScreen", "Connection error: ${e.message}", e)
                            errorMessage = "Network error: ${e.message}\n\nCheck:\n• PC IP address\n• WiFi connection\n• Firewall settings"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = connectionCode.length == 6 && pcIpAddress.isNotBlank() &&
                        !isLoading && connectionState != ConnectionState.CONNECTING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SmartDeskAccent,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTING -> "CONNECTING..."
                        ConnectionState.CONNECTED -> "CONNECTED"
                        else -> "CONNECT TO PC"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Quick Help Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartDeskCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connection Help",
                        color = SmartDeskAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Find PC IP: Run 'ipconfig' in Command Prompt\n• Look for 'IPv4 Address' under your WiFi\n• Make sure PC and phone are on same WiFi",
                        color = SmartDeskMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}