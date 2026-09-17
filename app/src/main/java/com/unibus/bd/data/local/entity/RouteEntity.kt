package com.unibus.bd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.unibus.bd.domain.model.CampusRoute
import com.unibus.bd.domain.model.Stoppage

/**
 * Room entity representing a campus route in local storage.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val routeId: String,
    val routeName: String,
    val origin: String,
    val destination: String,
    val polyline: String,
)

fun RouteEntity.toDomain(stoppages: List<Stoppage> = emptyList()): CampusRoute {
    return CampusRoute(
        routeId = routeId,
        routeName = routeName,
        origin = origin,
        destination = destination,
        stoppages = stoppages,
        polyline = polyline,
    )
}

fun CampusRoute.toEntity(): RouteEntity {
    return RouteEntity(
        routeId = routeId,
        routeName = routeName,
        origin = origin,
        destination = destination,
        polyline = polyline,
    )
}
