package com.unibus.bd.data.repository

import com.unibus.bd.core.network.ConnectionState
import com.unibus.bd.core.network.RealtimeBusLocationClient
import com.unibus.bd.data.local.dao.UniBusDao
import com.unibus.bd.data.local.entity.toDomain
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.CampusRoute
import com.unibus.bd.domain.model.Stoppage
import com.unibus.bd.domain.repository.BusTrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Concrete implementation of [BusTrackingRepository] integrating Room local database
 * and OkHttp WebSocket real-time bus telemetry client.
 */
class BusTrackingRepositoryImpl @Inject constructor(
    private val uniBusDao: UniBusDao,
    private val realtimeBusLocationClient: RealtimeBusLocationClient,
) : BusTrackingRepository {

    override fun getCachedRoutes(): Flow<List<CampusRoute>> {
        return uniBusDao.getAllRoutes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStoppagesForRoute(routeId: String): Flow<List<Stoppage>> {
        return uniBusDao.getRouteWithStoppages(routeId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun trackBusLive(tripId: String): Flow<BusLocation> {
        return realtimeBusLocationClient.observeBusTelemetry(tripId)
    }

    override fun getConnectionState(): StateFlow<ConnectionState> {
        return realtimeBusLocationClient.observeConnectionState()
    }
}
