package com.smartdesk.mirrormobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdesk.mirrormobile.network.ConnectionManager
import com.smartdesk.mirrormobile.network.ConnectionState
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent
import com.smartdesk.mirrormobile.ui.theme.SmartDeskAccent2
import com.smartdesk.mirrormobile.ui.theme.SmartDeskCard
import com.smartdesk.mirrormobile.ui.theme.SmartDeskDark
import com.smartdesk.mirrormobile.ui.theme.SmartDeskGlass
import com.smartdesk.mirrormobile.ui.theme.SmartDeskMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionManager: ConnectionManager,
    onConnectPC: () -> Unit,
    onStartFullScreenRemoteControl: () -> Unit,
    onStartVoice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val connectionState by connectionManager.connectionState.collectAsState()
    val systemMetrics by connectionManager.systemMetrics.collectAsState()
    val screenBitmap by connectionManager.screenBitmap.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Start/stop streaming based on connection state (ConnectionManager owns the stream)
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            connectionManager.startScreenStream()
        } else {
            connectionManager.stopScreenStream()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SmartDesk Mirror",
                        color = SmartDeskAccent2,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SmartDeskDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SmartDeskCard,
                modifier = Modifier.height(70.dp)
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Computer, contentDescription = "PC Control") },
                    label = { Text("PC Control") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.QrCode, contentDescription = "Connect") },
                    label = { Text("Connect") },
                    selected = false,
                    onClick = onConnectPC
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = onOpenSettings
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SmartDeskDark)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Card (keep your existing UI)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartDeskCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status Indicator row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val indicatorColor = when (connectionState) {
                                ConnectionState.CONNECTED -> SmartDeskAccent
                                ConnectionState.CONNECTING -> Color.Yellow
                                ConnectionState.ERROR -> Color.Red
                                else -> SmartDeskMuted
                            }
                            Box(modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(indicatorColor))
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "Connected to PC"
                                    ConnectionState.CONNECTING -> "Connecting..."
                                    ConnectionState.ERROR -> "Connection Failed"
                                    else -> "Disconnected"
                                },
                                color = SmartDeskMuted,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        // Connect/Disconnect Button
                        Button(
                            onClick = {
                                if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                                    onConnectPC()
                                } else {
                                    coroutineScope.launch {
                                        connectionManager.disconnectFromPc()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (connectionState) {
                                    ConnectionState.CONNECTED -> Color.Red
                                    ConnectionState.CONNECTING -> Color.Yellow
                                    else -> SmartDeskAccent
                                },
                                contentColor = when (connectionState) {
                                    ConnectionState.CONNECTED -> Color.White
                                    ConnectionState.CONNECTING -> Color.Black
                                    else -> Color.Black
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "DISCONNECT PC"
                                    ConnectionState.CONNECTING -> "CONNECTING..."
                                    else -> "CONNECT TO PC"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Screen Preview Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clickable(
                            enabled = connectionState == ConnectionState.CONNECTED && screenBitmap != null,
                            onClick = onStartFullScreenRemoteControl
                        ),
                    colors = CardDefaults.cardColors(containerColor = SmartDeskCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SmartDeskGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        if (connectionState == ConnectionState.CONNECTED) {
                            if (screenBitmap != null) {
                                Image(
                                    bitmap = screenBitmap!!.asImageBitmap(),
                                    contentDescription = "PC Screen Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Text(
                                        text = "Tap to open full screen control",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = SmartDeskAccent, modifier = Modifier.size(36.dp))
                                    Text(text = "Loading Screen...", color = SmartDeskMuted, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Computer, contentDescription = "PC Icon", tint = SmartDeskMuted, modifier = Modifier.size(48.dp))
                                Text(text = "Connect to PC to see screen", color = SmartDeskMuted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Text(
                    text = "Quick Actions",
                    color = SmartDeskAccent,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        text = "Open Chrome",
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onClick = {
                            coroutineScope.launch {
                                val result = connectionManager.executeCommand("open_app", mapOf("app_name" to "chrome"))
                                // TODO: Show result in snackbar or toast
                                println("Command result: $result")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        text = "Task Manager",
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onClick = {
                            coroutineScope.launch {
                                val result = connectionManager.executeCommand("open_app", mapOf("app_name" to "task manager"))
                                // TODO: Show result in snackbar or toast
                                println("Command result: $result")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Second row of quick actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        text = "File Explorer",
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onClick = {
                            coroutineScope.launch {
                                val result = connectionManager.executeCommand("open_app", mapOf("app_name" to "file explorer"))
                                // TODO: Show result in snackbar or toast
                                println("Command result: $result")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        text = "Notepad",
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onClick = {
                            coroutineScope.launch {
                                val result = connectionManager.executeCommand("open_app", mapOf("app_name" to "notepad"))
                                // TODO: Show result in snackbar or toast
                                println("Command result: $result")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Remote Control Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        text = "Remote Control",
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onClick = onStartFullScreenRemoteControl,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        text = "Voice AI",
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onClick = onStartVoice,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // System Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartDeskCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "System Status",
                            color = SmartDeskAccent,
                            fontSize = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SystemMetric("CPU", systemMetrics.cpu, connectionState == ConnectionState.CONNECTED)
                            SystemMetric("RAM", systemMetrics.ram, connectionState == ConnectionState.CONNECTED)
                            SystemMetric("NET", systemMetrics.net, connectionState == ConnectionState.CONNECTED)
                        }

                        // Additional connection info when connected
                        if (connectionState == ConnectionState.CONNECTED) {
                            Divider(color = SmartDeskGlass)
                            Text(
                                text = "PC Control Active",
                                color = SmartDeskAccent2,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "You can now control your PC remotely",
                                color = SmartDeskMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Connection Help Card
            item {
                if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SmartDeskCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "How to Connect",
                                color = SmartDeskAccent,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "1. Make sure PC app is running\n2. Tap 'Connect' button\n3. Enter 6-digit code from PC\n4. Wait for PC approval",
                                color = SmartDeskMuted,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = SmartDeskGlass,
            contentColor = SmartDeskMuted,
            disabledContainerColor = SmartDeskGlass.copy(alpha = 0.3f),
            disabledContentColor = SmartDeskMuted.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
fun SystemMetric(label: String, value: String, isConnected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            color = if (isConnected) SmartDeskAccent else SmartDeskMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = SmartDeskMuted,
            fontSize = 12.sp
        )
    }
}