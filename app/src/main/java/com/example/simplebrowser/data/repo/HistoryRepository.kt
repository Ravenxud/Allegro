package com.example.simplebrowser.data.repo

import com.example.simplebrowser.data.dao.HistoryDao
import com.example.simplebrowser.data.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    suspend fun record(url: String, title: String) {
        dao.upsert(
            HistoryEntity(
                url = url,
                title = title.ifBlank { url },
                lastVisitedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(url: String) = dao.delete(url)

    suspend fun clear() = dao.clear()
}
