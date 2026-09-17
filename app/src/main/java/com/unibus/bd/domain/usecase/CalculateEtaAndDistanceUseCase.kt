package com.unibus.bd.domain.usecase

import android.location.Location
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.Stoppage
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result data holder containing distance in meters, estimated time of arrival (ETA) in minutes,
 * and geofence proximity alert state.
 */
data class EtaAndDistanceResult(
    val distanceMeters: Float,
    val etaMinutes: Int,
    val isGeofenceAlertActive: Boolean,
)

/**
 * Domain use case to calculate the exact distance in meters between a bus and a stoppage,
 * estimate arrival time in minutes based on live bus speed, and determine whether a 1,000m
 * geofence alert is active.
 */
class CalculateEtaAndDistanceUseCase @Inject constructor() {

    /**
     * Calculates distance, ETA, and geofence alert status using raw latitude/longitude coordinates and bus speed.
     *
     * Uses [Location.distanceBetween] for precise geodesic calculation on Android devices.
     */
    operator fun invoke(
        busLat: Double,
        busLng: Double,
        stoppageLat: Double,
        stoppageLng: Double,
        busSpeedKmH: Float,
    ): EtaAndDistanceResult {
        val distanceMeters = calculateDistanceInMeters(busLat, busLng, stoppageLat, stoppageLng)

        // Effective speed logic: if current speed is under 5 km/h (e.g. traffic or stopped), default to Dhaka city average of 18 km/h
        val effectiveSpeedKmH = if (busSpeedKmH < MIN_SPEED_THRESHOLD_KMH) {
            DEFAULT_DHAKA_CITY_SPEED_KMH
        } else {
            busSpeedKmH
        }

        // Speed in meters per minute = (speedInKmH * 1000 meters) / 60 minutes
        val speedMetersPerMinute = (effectiveSpeedKmH * 1000f) / 60f

        val etaMinutes = if ((distanceMeters <= 0f) || (speedMetersPerMinute <= 0f)) {
            0
        } else {
            ceil(distanceMeters / speedMetersPerMinute).toInt()
        }

        val isGeofenceAlertActive = distanceMeters <= GEOFENCE_ALERT_THRESHOLD_METERS

        return EtaAndDistanceResult(
            distanceMeters = distanceMeters,
            etaMinutes = etaMinutes,
            isGeofenceAlertActive = isGeofenceAlertActive,
        )
    }

    /**
     * Overloaded operator to accept [BusLocation] and [Stoppage] model objects.
     */
    operator fun invoke(
        busLocation: BusLocation,
        stoppage: Stoppage,
    ): EtaAndDistanceResult = invoke(
        busLat = busLocation.lat,
        busLng = busLocation.lng,
        stoppageLat = stoppage.lat,
        stoppageLng = stoppage.lng,
        busSpeedKmH = busLocation.speedKmH,
    )

    private fun calculateDistanceInMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): Float {
        return try {
            val results = FloatArray(1)
            Location.distanceBetween(startLat, startLng, endLat, endLng, results)
            results[0]
        } catch (_: Exception) {
            // Fallback for JVM unit test execution where android.location.Location is not mocked
            haversineDistanceMeters(startLat, startLng, endLat, endLng)
        }
    }

    private fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Float {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).toFloat()
    }

    companion object {
        const val MIN_SPEED_THRESHOLD_KMH = 5f
        const val DEFAULT_DHAKA_CITY_SPEED_KMH = 18f
        const val GEOFENCE_ALERT_THRESHOLD_METERS = 1000f
    }
}
