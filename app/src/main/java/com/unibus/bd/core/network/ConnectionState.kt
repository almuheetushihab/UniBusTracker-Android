package com.unibus.bd.core.network

/**
 * Represents the real-time WebSocket connection lifecycle state for campus bus telemetry.
 */
sealed interface ConnectionState {
    data object DISCONNECTED : ConnectionState
    data object CONNECTING : ConnectionState
    data object CONNECTED : ConnectionState
    data class RECONNECTING(val attempt: Int) : ConnectionState
}
