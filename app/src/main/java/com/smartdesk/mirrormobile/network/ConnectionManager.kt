package com.smartdesk.mirrormobile.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val _screenBitmap = MutableStateFlow<Bitmap?>(null)
    val screenBitmap: StateFlow<Bitmap?> = _screenBitmap.asStateFlow()

    private var currentConnectionCode: String? = null
    private var currentBaseUrl: String = "http://192.168.1.100:8000"

    // Internal scope for long-running tasks that must survive Compose lifecycles
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenJob: Job? = null

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
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PcAgentApiService::class.java)
    }

    fun updateBaseUrl(ipAddress: String) {
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

            try {
                val testResponse = apiService.testConnection()
                Log.d("ConnectionManager", "Basic connection test: $testResponse")
            } catch (e: Exception) {
                Log.e("ConnectionManager", "Basic connection test failed: ${e.message}")
            }

            val requestResponse = apiService.requestConnection(
                ConnectionRequest(code, deviceName)
            )

            Log.d("ConnectionManager", "Request response: $requestResponse")

            if (!requestResponse.success) {
                Log.e("ConnectionManager", "Request failed: ${requestResponse.message}")
                _connectionState.value = ConnectionState.ERROR
                return false
            }

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

                    if (statusResponse.message.contains("rejected", ignoreCase = true)) {
                        Log.e("ConnectionManager", "Connection rejected by PC")
                        _connectionState.value = ConnectionState.ERROR
                        return false
                    }

                    kotlinx.coroutines.delay(1000)
                    attempts++
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "Status check $attempts failed: ${e.message}")
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
        _screenBitmap.value = null
        _systemMetrics.value = SystemMetrics()
        stopScreenStream()
        Log.d("ConnectionManager", "Disconnected from PC")
    }

    /**
     * Manually fetch a single screen frame (suspend). Safe for on-demand refresh.
     */
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

            if (response.isNotEmpty() && response.startsWith("data:image")) {
                _screenFrame.value = response
                Log.d("ConnectionManager", "🎉 Screen frame updated successfully!")

                // Decode base64 to bitmap ONCE and expose via screenBitmap
                try {
                    val base64Data = response.substringAfter("base64,", "")
                    if (base64Data.isNotEmpty()) {
                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            _screenBitmap.value = bitmap
                            Log.d("ConnectionManager", "✅ Screen bitmap decoded ${bitmap.width}x${bitmap.height}")
                        } else {
                            Log.e("ConnectionManager", "❌ Bitmap decode returned null")
                            _screenBitmap.value = null
                        }
                    } else {
                        Log.e("ConnectionManager", "❌ No base64 payload after prefix")
                        _screenBitmap.value = null
                    }
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "❌ Error decoding base64 to bitmap: ${e.message}")
                    _screenBitmap.value = null
                }
            } else {
                Log.e("ConnectionManager", "❌ Invalid screen data format")
                Log.e("ConnectionManager", "Expected data:image, got: ${response.take(50)}")
            }
        } catch (e: Exception) {
            Log.e("ConnectionManager", "❌ Failed to fetch screen: ${e.message}")
            e.printStackTrace()
            _screenBitmap.value = null
        }
    }

    /**
     * Starts a periodic screen fetch in an internal scope that survives Compose lifecycle.
     * Calling multiple times is safe (it will no-op if already running).
     */
    fun startScreenStream(intervalMs: Long = 500L) {
        if (screenJob?.isActive == true) return
        screenJob = internalScope.launch {
            Log.d("ConnectionManager", "Starting screen stream (interval ${intervalMs}ms)")
            while (isActive) {
                try {
                    if (_connectionState.value == ConnectionState.CONNECTED && currentConnectionCode != null) {
                        fetchScreen()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "Screen stream fetch error: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * Stop the running screen stream (if any).
     */
    fun stopScreenStream() {
        try {
            screenJob?.cancel()
            screenJob = null
            Log.d("ConnectionManager", "Stopped screen stream")
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Error stopping screen stream: ${e.message}")
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
