package com.unibus.bd.core.network

import android.util.Log
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.CrowdLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.math.min

/**
 * Resilient Real-Time WebSocket streaming client for live campus bus tracking.
 * Features automatic exponential backoff reconnection for handling 4G/3G network dead zones in Bangladesh.
 */
class RealtimeBusLocationClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetryFlow = MutableSharedFlow<BusLocation>(extraBufferCapacity = 64)
    val telemetryFlow: SharedFlow<BusLocation> = _telemetryFlow.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var currentTripId: String? = null
    private var reconnectJob: Job? = null
    private var retryAttempt = 0
    @Volatile
    private var isManualDisconnect = false

    /**
     * Observes live telemetry stream for the specified [tripId].
     * Initiates WebSocket connection if not already connected.
     */
    fun observeBusTelemetry(tripId: String): Flow<BusLocation> {
        if ((currentTripId != tripId) || (webSocket == null)) {
            connect(tripId)
        }
        return telemetryFlow
    }

    /**
     * Returns a [StateFlow] of the current [ConnectionState].
     */
    fun observeConnectionState(): StateFlow<ConnectionState> {
        return connectionState
    }

    /**
     * Connects to the real-time WebSocket telemetry endpoint.
     */
    @Synchronized
    fun connect(tripId: String) {
        currentTripId = tripId
        isManualDisconnect = false
        reconnectJob?.cancel()

        webSocket?.close(NORMAL_CLOSURE_STATUS, "Reconnecting to new trip")
        _connectionState.value = ConnectionState.CONNECTING

        val requestUrl = if (baseUrl.endsWith("/")) "$baseUrl$tripId" else "$baseUrl/$tripId"
        val request = Request.Builder()
            .url(requestUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Connected successfully to trip: $currentTripId")
                retryAttempt = 0
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                clientScope.launch {
                    parseAndEmitTelemetry(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.localizedMessage}", t)
                handleConnectionLoss()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                if (!isManualDisconnect) {
                    handleConnectionLoss()
                }
            }
        }
    }

    private fun parseAndEmitTelemetry(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val crowdLevelString = json.optString("crowdLevel", "SEATS_AVAILABLE")
            val crowdLevel = try {
                CrowdLevel.valueOf(crowdLevelString)
            } catch (_: IllegalArgumentException) {
                CrowdLevel.SEATS_AVAILABLE
            }

            val busLocation = BusLocation(
                busId = json.optString("busId", ""),
                licensePlate = json.optString("licensePlate", ""),
                routeId = json.optString("routeId", ""),
                lat = json.optDouble("lat", 0.0),
                lng = json.optDouble("lng", 0.0),
                speedKmH = json.optDouble("speedKmH", 0.0).toFloat(),
                bearing = json.optDouble("bearing", 0.0).toFloat(),
                lastUpdatedTimestamp = json.optLong("lastUpdatedTimestamp", System.currentTimeMillis()),
                crowdLevel = crowdLevel,
            )

            _telemetryFlow.tryEmit(busLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming bus location JSON payload", e)
        }
    }

    private fun handleConnectionLoss() {
        if (isManualDisconnect) return

        retryAttempt++
        val backoffDelayMs = calculateBackoffDelay(retryAttempt)
        _connectionState.value = ConnectionState.RECONNECTING(retryAttempt)

        Log.w(
            TAG,
            "Connection lost. Scheduling reconnect attempt #$retryAttempt in ${backoffDelayMs}ms",
        )

        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(backoffDelayMs)
            currentTripId?.let { tripId ->
                if (!isManualDisconnect) {
                    connect(tripId)
                }
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        // Exponential backoff: 1s -> 2s -> 4s -> 8s -> max 16s
        val exponent = min(attempt - 1, MAX_BACKOFF_EXPONENT)
        val delayMs = INITIAL_BACKOFF_MS * (1 shl exponent)
        return min(delayMs, MAX_BACKOFF_MS)
    }

    /**
     * Closes the active WebSocket connection with code 1000 and cancels reconnect attempts.
     */
    @Synchronized
    fun disconnect() {
        isManualDisconnect = true
        reconnectJob?.cancel()
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Manual disconnect requested by user")
        webSocket = null
        currentTripId = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        private const val TAG = "RealtimeBusLocationClient"
        const val DEFAULT_BASE_URL = "wss://api.unibusbd.com/v1/telemetry/live"
        private const val NORMAL_CLOSURE_STATUS = 1000

        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 16000L
        private const val MAX_BACKOFF_EXPONENT = 4 // 2^4 = 16
    }
}
