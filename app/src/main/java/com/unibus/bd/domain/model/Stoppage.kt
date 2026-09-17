package com.unibus.bd.domain.model

/**
 * Represents a designated bus stop along a campus route.
 *
 * @property stoppageId Unique identifier for the stoppage.
 * @property name Display name of the bus stop (e.g., "TSC", "Curzon Hall").
 * @property lat Latitude coordinate of the stop.
 * @property lng Longitude coordinate of the stop.
 * @property sequenceOrder Order index of this stop along the route.
 * @property geofenceRadiusMeters Radius in meters for geofence detection.
 */
data class Stoppage(
    val stoppageId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val sequenceOrder: Int,
    val geofenceRadiusMeters: Float = 50f,
)
