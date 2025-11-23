package com.smartdesk.mirrormobile.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class ConnectionManager {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _systemMetrics = MutableStateFlow(SystemMetrics())
    val systemMetrics: StateFlow<SystemMetrics> = _systemMetrics.asStateFlow()

    private val _screenFrame = MutableStateFlow<String?>(null)
    val screenFrame: StateFlow<String?> = _screenFrame.asStateFlow()

    private var currentConnectionCode: String? = null
    private var currentBaseUrl: String = "http://192.168.1.100:8000"


    private fun createApiService(baseUrl: String): PcAgentApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SmartDesk-Mobile/1.0")
                    .header("Accept", "text/plain, application/json")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create()) // Add this FIRST
            .addConverterFactory(GsonConverterFactory.create())   // Then add JSON converter
            .build()
            .create(PcAgentApiService::class.java)
    }

    fun updateBaseUrl(ipAddress: String) {
        // Ensure the URL has http:// prefix
        val cleanIp = ipAddress.trim()
        this.currentBaseUrl = if (cleanIp.startsWith("http://")) {
            cleanIp
        } else {
            "http://$cleanIp:8000"
        }
        Log.d("ConnectionManager", "Updated base URL to: $currentBaseUrl")
    }

    suspend fun connectWithCode(code: String, deviceName: String = "Android Device"): Boolean {
        _connectionState.value = ConnectionState.CONNECTING
        Log.d("ConnectionManager", "Attempting connection with code: $code to: $currentBaseUrl")

        return try {
            val apiService = createApiService(currentBaseUrl)

            // Step 1: Test basic connection first
            Log.d("ConnectionManager", "Testing basic connection...")
            try {
                val testResponse = apiService.testConnection()
                Log.d("ConnectionManager", "Basic connection test: $testResponse")
            } catch (e: Exception) {
                Log.e("ConnectionManager", "Basic connection test failed: ${e.message}")
                // Continue anyway, as some endpoints might work
            }

            // Step 2: Send connection request
            Log.d("ConnectionManager", "Sending connection request...")
            val requestResponse = apiService.requestConnection(
                ConnectionRequest(code, deviceName)
            )

            Log.d("ConnectionManager", "Request response: $requestResponse")

            if (!requestResponse.success) {
                Log.e("ConnectionManager", "Request failed: ${requestResponse.message}")
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            // Step 3: Wait for approval (polling with timeout)
            var approved = false
            var attempts = 0
            val maxAttempts = 45 // 45 seconds timeout

            Log.d("ConnectionManager", "Waiting for PC approval...")

            while (attempts < maxAttempts && _connectionState.value == ConnectionState.CONNECTING) {
                try {
                    val statusResponse = apiService.getConnectionStatus(code)
                    Log.d("ConnectionManager", "Status check $attempts: $statusResponse")

                    if (statusResponse.active) {
                        approved = true
                        Log.d("ConnectionManager", "✅ Connection approved by PC!")
                        break
                    }

                    // Check if we should continue waiting
                    if (statusResponse.message.contains("rejected", ignoreCase = true)) {
                        Log.e("ConnectionManager", "Connection rejected by PC")
                        _connectionState.value = ConnectionState.ERROR
                        return false
                    }

                    kotlinx.coroutines.delay(1000) // Wait 1 second between checks
                    attempts++
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "Status check $attempts failed: ${e.message}")
                    // Continue polling even if one check fails
                    kotlinx.coroutines.delay(1000)
                    attempts++
                }
            }

            if (approved) {
                currentConnectionCode = code
                _connectionState.value = ConnectionState.CONNECTED
                Log.d("ConnectionManager", "🎉 Connection established successfully!")
                true
            } else {
                Log.e("ConnectionManager", "⏰ Connection timeout - PC didn't approve in time")
                _connectionState.value = ConnectionState.ERROR
                false
            }

        } catch (e: Exception) {
            Log.e("ConnectionManager", "❌ Connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            false
        }
    }

    suspend fun testConnection(ipAddress: String): Boolean {
        return try {
            val testUrl = if (ipAddress.startsWith("http://")) {
                ipAddress
            } else {
                "http://$ipAddress:8000"
            }

            val apiService = createApiService(testUrl)
            val response = apiService.testConnection()
            Log.d("ConnectionManager", "Connection test successful: $response")
            true
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Connection test failed for $ipAddress: ${e.message}")
            false
        }
    }

    suspend fun disconnectFromPc() {
        currentConnectionCode = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _screenFrame.value = null
        _systemMetrics.value = SystemMetrics()
        Log.d("ConnectionManager", "Disconnected from PC")
    }

    suspend fun fetchScreen() {
        if (_connectionState.value != ConnectionState.CONNECTED || currentConnectionCode == null) {
            Log.d("ConnectionManager", "Not connected, skipping screen fetch")
            return
        }

        try {
            val apiService = createApiService(currentBaseUrl)
            Log.d("ConnectionManager", "Fetching screen from: $currentBaseUrl")

            val response = apiService.getScreen(currentConnectionCode!!)

            Log.d("ConnectionManager", "✅ Screen response received successfully")
            Log.d("ConnectionManager", "Response length: ${response.length}")
            Log.d("ConnectionManager", "Response preview: ${response.take(100)}...")

            // Validate the response
            if (response.isNotEmpty() && response.startsWith("data:image")) {
                _screenFrame.value = response
                Log.d("ConnectionManager", "🎉 Screen frame updated successfully!")
            } else {
                Log.e("ConnectionManager", "❌ Invalid screen data format")
                Log.e("ConnectionManager", "Expected data:image, got: ${response.take(50)}")
            }
        } catch (e: Exception) {
            Log.e("ConnectionManager", "❌ Failed to fetch screen: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun fetchSystemMetrics() {
        if (_connectionState.value != ConnectionState.CONNECTED || currentConnectionCode == null) return

        try {
            val apiService = createApiService(currentBaseUrl)
            val response = apiService.getSystemMetrics(currentConnectionCode!!)
            _systemMetrics.value = SystemMetrics(
                cpu = response.cpu,
                ram = response.ram,
                net = response.net
            )
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Failed to fetch metrics: ${e.message}")
        }
    }

    suspend fun executeCommand(commandType: String, data: Map<String, String> = emptyMap()): String {
        if (_connectionState.value != ConnectionState.CONNECTED || currentConnectionCode == null) {
            return "Not connected to PC"
        }

        return try {
            val apiService = createApiService(currentBaseUrl)
            val response = apiService.executeCommand(
                currentConnectionCode!!,
                CommandRequest(commandType, data)
            )
            if (response.success) {
                response.message ?: "Command executed successfully"
            } else {
                response.error ?: "Command failed"
            }
        } catch (e: Exception) {
            "Network error: ${e.message}"
        }
    }

    suspend fun sendMouseClick(button: String = "left"): String {
        return executeCommand("mouse_click", mapOf("button" to button))
    }

    suspend fun sendKeyboardShortcut(shortcut: String): String {
        return executeCommand("keyboard_shortcut", mapOf("shortcut" to shortcut))
    }

    suspend fun sendSystemCommand(command: String): String {
        return executeCommand("system_command", mapOf("command" to command))
    }
}

data class SystemMetrics(
    val cpu: String = "--",
    val ram: String = "--",
    val net: String = "--"
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}