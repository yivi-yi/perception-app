package com.yivi.perception.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PerceptionDao {
    @Query("SELECT * FROM events WHERE category = :category ORDER BY time ASC")
    fun observeByCategory(category: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE category = :category ORDER BY time ASC")
    suspend fun getByCategory(category: String): List<EventEntity>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM events")
    suspend fun clearAll()
}
