package com.smartdesk.mirrormobile.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class TestConnectionResponse(
    val status: String
)

@Serializable
data class ConnectionRequest(
    val code: String,
    val device_info: String = "Android Device"
)

@Serializable
data class ConnectionResponse(
    val success: Boolean,
    val request_id: Int? = null,
    val message: String? = null
)

@Serializable
data class ConnectionStatusResponse(
    val active: Boolean,
    val message: String
)

@Serializable
data class CommandRequest(
    val type: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class CommandResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val system_info: Map<String, String>? = null
)

@Serializable
data class ScreenResponse(
    val frame: String
)

@Serializable
data class SystemMetricsResponse(
    val cpu: String,
    val ram: String,
    val net: String
)

interface PcAgentApiService {
    @GET("/")
    suspend fun testConnection(): TestConnectionResponse // FIXED: Use proper response type

    @POST("connection/request")
    suspend fun requestConnection(@Body request: ConnectionRequest): ConnectionResponse

    @GET("connection/status/{code}")
    suspend fun getConnectionStatus(@Path("code") code: String): ConnectionStatusResponse

    @GET("mobile/screen")
    suspend fun getScreen(@Header("x-connection-code") code: String): ScreenResponse

    @POST("mobile/execute-command")
    suspend fun executeCommand(
        @Header("x-connection-code") code: String,
        @Body command: CommandRequest
    ): CommandResponse

    @GET("system-metrics")
    suspend fun getSystemMetrics(@Header("x-connection-code") code: String): SystemMetricsResponse
}