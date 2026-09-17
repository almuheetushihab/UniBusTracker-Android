package com.unibus.bd.domain.model

/**
 * Represents the real-time position, movement state, and occupancy of a campus bus.
 *
 * @property busId Unique identifier of the bus.
 * @property licensePlate Vehicle license plate number (e.g., "DHAKA METRO-JA-11-2233").
 * @property routeId ID of the route this bus is currently operating on.
 * @property lat Current latitude coordinate.
 * @property lng Current longitude coordinate.
 * @property speedKmH Current speed of the bus in kilometers per hour.
 * @property bearing Compass heading angle in degrees (0..360).
 * @property lastUpdatedTimestamp Epoch timestamp in milliseconds of the last location update.
 * @property crowdLevel Occupancy status of the bus.
 */
data class BusLocation(
    val busId: String,
    val licensePlate: String,
    val routeId: String,
    val lat: Double,
    val lng: Double,
    val speedKmH: Float = 0f,
    val bearing: Float = 0f,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val crowdLevel: CrowdLevel = CrowdLevel.SEATS_AVAILABLE,
)
