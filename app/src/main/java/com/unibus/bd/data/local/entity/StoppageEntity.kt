package com.unibus.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unibus.bd.domain.model.Stoppage

/**
 * Room entity representing a route stoppage in local storage.
 */
@Entity(
    tableName = "stoppages",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["routeId"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routeId"])],
)
data class StoppageEntity(
    @PrimaryKey
    val stoppageId: String,
    @ColumnInfo(index = true)
    val routeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val sequenceOrder: Int,
    val geofenceRadiusMeters: Float,
)

fun StoppageEntity.toDomain(): Stoppage {
    return Stoppage(
        stoppageId = stoppageId,
        name = name,
        lat = lat,
        lng = lng,
        sequenceOrder = sequenceOrder,
        geofenceRadiusMeters = geofenceRadiusMeters,
    )
}

fun Stoppage.toEntity(routeId: String): StoppageEntity {
    return StoppageEntity(
        stoppageId = stoppageId,
        routeId = routeId,
        name = name,
        lat = lat,
        lng = lng,
        sequenceOrder = sequenceOrder,
        geofenceRadiusMeters = geofenceRadiusMeters,
    )
}
