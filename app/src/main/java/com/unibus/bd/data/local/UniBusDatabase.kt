package com.unibus.bd.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.unibus.bd.data.local.dao.UniBusDao
import com.unibus.bd.data.local.entity.RouteEntity
import com.unibus.bd.data.local.entity.StoppageEntity

/**
 * Main Room Database for UniBus BD local offline-first caching.
 */
@Database(
    entities = [
        RouteEntity::class,
        StoppageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UniBusDatabase : RoomDatabase() {
    abstract fun uniBusDao(): UniBusDao
}
