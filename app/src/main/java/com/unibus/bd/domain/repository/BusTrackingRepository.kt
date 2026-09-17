package com.unibus.bd.domain.repository

import com.unibus.bd.core.network.ConnectionState
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.CampusRoute
import com.unibus.bd.domain.model.Stoppage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for managing route data, stoppage information, and live bus telemetry.
 */
interface BusTrackingRepository {

    /**
     * Observes all locally cached campus routes.
     */
    fun getCachedRoutes(): Flow<List<CampusRoute>>

    /**
     * Observes the list of stoppages for a given [routeId], ordered by sequence.
     */
    fun getStoppagesForRoute(routeId: String): Flow<List<Stoppage>>

    /**
     * Establishes real-time telemetry tracking for a live bus [tripId].
     */
    fun trackBusLive(tripId: String): Flow<BusLocation>

    /**
     * Observes the current real-time network [ConnectionState].
     */
    fun getConnectionState(): StateFlow<ConnectionState>
}
