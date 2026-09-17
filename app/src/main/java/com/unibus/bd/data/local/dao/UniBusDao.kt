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
abstract class UniBusDao {

    @Query("SELECT * FROM routes")
    abstract fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM stoppages WHERE routeId = :routeId ORDER BY sequenceOrder ASC")
    abstract fun getRouteWithStoppages(routeId: String): Flow<List<StoppageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertStoppages(stoppages: List<StoppageEntity>)

    @Query("DELETE FROM stoppages")
    abstract suspend fun clearStoppages()

    @Query("DELETE FROM routes")
    abstract suspend fun clearRoutes()

    @Transaction
    open suspend fun clearCache() {
        clearStoppages()
        clearRoutes()
    }
}
