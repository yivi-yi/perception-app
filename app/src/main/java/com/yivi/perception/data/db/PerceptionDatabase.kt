package com.yivi.perception.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class PerceptionDatabase : RoomDatabase() {
    abstract fun dao(): PerceptionDao
}
