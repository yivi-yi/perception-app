package com.yivi.perception.data.repo

import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.data.db.PerceptionDao
import kotlinx.coroutines.flow.Flow

class PerceptionRepository(private val dao: PerceptionDao) {

    fun observeByCategory(category: String): Flow<List<EventEntity>> = dao.observeByCategory(category)

    suspend fun listByCategory(category: String): List<EventEntity> = dao.getByCategory(category)

    suspend fun all(): List<EventEntity> = dao.getAll()

    suspend fun add(event: EventEntity): Long = dao.insert(event)

    suspend fun update(event: EventEntity) = dao.update(event)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
