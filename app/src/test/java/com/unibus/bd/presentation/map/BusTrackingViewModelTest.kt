package com.unibus.bd.presentation.map

import com.unibus.bd.core.network.ConnectionState
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.CampusRoute
import com.unibus.bd.domain.model.Stoppage
import com.unibus.bd.domain.repository.BusTrackingRepository
import com.unibus.bd.domain.usecase.CalculateEtaAndDistanceUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BusTrackingViewModelTest {

    private lateinit var fakeRepository: FakeBusTrackingRepository
    private lateinit var useCase: CalculateEtaAndDistanceUseCase
    private lateinit var viewModel: BusTrackingViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeBusTrackingRepository()
        useCase = CalculateEtaAndDistanceUseCase()
        viewModel = BusTrackingViewModel(fakeRepository, useCase)
    }

    @Test
    fun initialUiStateIsLoading() {
        assertEquals(BusTrackingUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun startTrackingTransitionsStateToSuccess() = runBlocking {
        val initialBus = BusLocation(
            busId = "BUS-101",
            licensePlate = "DHAKA-1234",
            routeId = "ROUTE-1",
            lat = 23.7315,
            lng = 90.3965,
            speedKmH = 20f,
        )

        viewModel.startTracking(tripId = "TRIP-101", routeId = "ROUTE-1")
        fakeRepository.emitBusLocation(initialBus)

        var state: BusTrackingUiState = viewModel.uiState.value
        val startTime = System.currentTimeMillis()
        while (state !is BusTrackingUiState.Success && System.currentTimeMillis() - startTime < 1000) {
            delay(50)
            state = viewModel.uiState.value
        }

        assertTrue(state is BusTrackingUiState.Success)
        val success = state as BusTrackingUiState.Success
        assertEquals("BUS-101", success.busLocation.busId)
        assertEquals("STOP-1", success.targetStoppage?.stoppageId)
        assertTrue(success.distanceMeters > 0f)
        assertEquals(ConnectionState.CONNECTED, success.connectionState)
    }

    private class FakeBusTrackingRepository : BusTrackingRepository {
        private val busTelemetryFlow = MutableSharedFlow<BusLocation>(replay = 1)
        private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.CONNECTED)

        fun emitBusLocation(busLocation: BusLocation) {
            busTelemetryFlow.tryEmit(busLocation)
        }

        override fun getCachedRoutes(): Flow<List<CampusRoute>> = flowOf(emptyList())

        override fun getStoppagesForRoute(routeId: String): Flow<List<Stoppage>> {
            return flowOf(
                listOf(
                    Stoppage("STOP-1", "TSC", 23.7315, 90.3965, 1),
                    Stoppage("STOP-2", "Curzon Hall", 23.7262, 90.4011, 2),
                ),
            )
        }

        override fun trackBusLive(tripId: String): Flow<BusLocation> = busTelemetryFlow

        override fun getConnectionState(): StateFlow<ConnectionState> = connectionStateFlow
    }
}
