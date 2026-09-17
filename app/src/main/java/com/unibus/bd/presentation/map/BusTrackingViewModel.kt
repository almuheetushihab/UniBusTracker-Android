package com.unibus.bd.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibus.bd.core.network.ConnectionState
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.Stoppage
import com.unibus.bd.domain.repository.BusTrackingRepository
import com.unibus.bd.domain.usecase.CalculateEtaAndDistanceUseCase
import com.unibus.bd.domain.usecase.EtaAndDistanceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Immutable UI state sealed interface for live bus tracking screen.
 */
sealed interface BusTrackingUiState {
    object Loading : BusTrackingUiState
    data class Success(
        val busLocation: BusLocation,
        val targetStoppage: Stoppage?,
        val distanceMeters: Float,
        val etaMinutes: Int,
        val isGeofenceAlertActive: Boolean,
        val connectionState: ConnectionState,
    ) : BusTrackingUiState
    data class Error(val message: String) : BusTrackingUiState
}

private data class TrackingParams(
    val tripId: String,
    val routeId: String,
)

/**
 * MVI ViewModel responsible for managing real-time bus tracking UI state,
 * handling target stoppage selection, and calculating live ETA and geofence alerts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BusTrackingViewModel @Inject constructor(
    private val repository: BusTrackingRepository,
    private val calculateEtaAndDistanceUseCase: CalculateEtaAndDistanceUseCase,
) : ViewModel() {

    private val trackingParams = MutableStateFlow<TrackingParams?>(null)
    private val selectedStoppage = MutableStateFlow<Stoppage?>(null)

    val uiState: StateFlow<BusTrackingUiState> = trackingParams
        .flatMapLatest { params ->
            if (params == null) {
                flowOf<BusTrackingUiState>(BusTrackingUiState.Loading)
            } else {
                val liveBusFlow = repository.trackBusLive(params.tripId)
                val stoppagesFlow = repository.getStoppagesForRoute(params.routeId)
                val connectionStateFlow = repository.getConnectionState()

                combine(
                    liveBusFlow,
                    stoppagesFlow,
                    connectionStateFlow,
                    selectedStoppage,
                ) { busLocation, stoppages, connectionState, currentTarget ->
                    val targetStoppage = currentTarget ?: stoppages.firstOrNull()

                    val (distanceMeters, etaMinutes, isGeofenceAlertActive) = if (targetStoppage != null) {
                        calculateEtaAndDistanceUseCase(busLocation, targetStoppage)
                    } else {
                        EtaAndDistanceResult(
                            distanceMeters = 0f,
                            etaMinutes = 0,
                            isGeofenceAlertActive = false,
                        )
                    }

                    val result: BusTrackingUiState = BusTrackingUiState.Success(
                        busLocation = busLocation,
                        targetStoppage = targetStoppage,
                        distanceMeters = distanceMeters,
                        etaMinutes = etaMinutes,
                        isGeofenceAlertActive = isGeofenceAlertActive,
                        connectionState = connectionState,
                    )
                    result
                }.catch { throwable ->
                    emit(
                        BusTrackingUiState.Error(
                            message = throwable.localizedMessage ?: "An error occurred during live tracking",
                        ),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BusTrackingUiState.Loading,
        )

    /**
     * Initiates real-time tracking for the specified [tripId] and [routeId].
     */
    fun startTracking(tripId: String, routeId: String) {
        selectedStoppage.value = null
        trackingParams.value = TrackingParams(tripId = tripId, routeId = routeId)
    }

    /**
     * Updates the user's target stoppage for dynamic ETA and geofence distance calculation.
     */
    fun selectTargetStoppage(stoppage: Stoppage) {
        selectedStoppage.value = stoppage
    }
}
