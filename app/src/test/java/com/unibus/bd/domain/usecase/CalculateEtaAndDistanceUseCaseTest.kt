package com.unibus.bd.domain.usecase

import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.Stoppage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateEtaAndDistanceUseCaseTest {

    private lateinit var useCase: CalculateEtaAndDistanceUseCase

    @Before
    fun setUp() {
        useCase = CalculateEtaAndDistanceUseCase()
    }

    @Test
    fun `when bus speed is low, fallback to Dhaka city average speed of 18 kmh`() {
        // TSC coordinates
        val busLat = 23.7315
        val busLng = 90.3965
        // Curzon Hall coordinates (approx 750m away)
        val stoppageLat = 23.7262
        val stoppageLng = 90.4011

        // Speed is 2 km/h (< 5 km/h)
        val result = useCase(
            busLat = busLat,
            busLng = busLng,
            stoppageLat = stoppageLat,
            stoppageLng = stoppageLng,
            busSpeedKmH = 2.0f,
        )

        // At 18 km/h = 300 meters/min, 750m takes ceil(750/300) = 3 min
        assertTrue(result.distanceMeters > 0f)
        assertEquals(3, result.etaMinutes)
        assertTrue(result.isGeofenceAlertActive) // 750m <= 1000m
    }

    @Test
    fun `when bus speed is high, use real bus speed`() {
        // Bus location and stoppage ~1500 meters apart
        val busLat = 23.7400
        val busLng = 90.3965
        val stoppageLat = 23.7262
        val stoppageLng = 90.4011

        val result = useCase(
            busLat = busLat,
            busLng = busLng,
            stoppageLat = stoppageLat,
            stoppageLng = stoppageLng,
            busSpeedKmH = 30.0f, // 30 km/h = 500 m/min
        )

        // 1500m / 500 m/min = 3 min
        assertTrue(result.distanceMeters > 1000f)
        assertFalse(result.isGeofenceAlertActive) // > 1000m
    }

    @Test
    fun `when distance is within 1000m, geofence alert is active`() {
        val busLocation = BusLocation(
            busId = "BUS-01",
            licensePlate = "DHAKA-11-2233",
            routeId = "ROUTE-A",
            lat = 23.7315,
            lng = 90.3965,
            speedKmH = 25.0f,
        )

        val stoppage = Stoppage(
            stoppageId = "STOP-01",
            name = "Curzon Hall",
            lat = 23.7280,
            lng = 90.3980,
            sequenceOrder = 1,
        )

        val result = useCase(busLocation, stoppage)

        assertTrue(result.distanceMeters <= 1000f)
        assertTrue(result.isGeofenceAlertActive)
    }
}
