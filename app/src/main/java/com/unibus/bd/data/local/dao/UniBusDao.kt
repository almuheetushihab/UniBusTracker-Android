package com.unibus.bd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.unibus.bd.data.local.entity.RouteEntity
import com.unibus.bd.data.local.entity.StoppageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for UniBus local database operations.
 */
@Dao
interface UniBusDao {

    @Query("SELECT * FROM routes")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM stoppages WHERE routeId = :routeId ORDER BY sequenceOrder ASC")
    fun getRouteWithStoppages(routeId: String): Flow<List<StoppageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoppages(stoppages: List<StoppageEntity>)

    @Query("DELETE FROM stoppages")
    suspend fun clearStoppages()

    @Query("DELETE FROM routes")
    suspend fun clearRoutes()

    @Transaction
    suspend fun clearCache() {
        clearStoppages()
        clearRoutes()
    }
}
