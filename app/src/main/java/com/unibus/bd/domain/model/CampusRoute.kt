package com.unibus.bd.domain.model

/**
 * Represents a campus bus route.
 *
 * @property routeId Unique identifier for the route.
 * @property routeName Name of the route (e.g., "Route A - Main Campus to Science Complex").
 * @property origin Starting location of the route.
 * @property destination Ending location of the route.
 * @property stoppages Ordered list of bus stops along this route.
 * @property polyline Encoded polyline string representing the map route path.
 */
data class CampusRoute(
    val routeId: String,
    val routeName: String,
    val origin: String,
    val destination: String,
    val stoppages: List<Stoppage> = emptyList(),
    val polyline: String = "",
)
