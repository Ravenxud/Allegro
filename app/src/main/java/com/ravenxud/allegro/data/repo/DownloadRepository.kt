package com.ravenxud.allegro.data.repo

import com.ravenxud.allegro.data.dao.DownloadDao
import com.ravenxud.allegro.data.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val dao: DownloadDao) {

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun add(entity: DownloadEntity) = dao.insert(entity)

    suspend fun active(): List<DownloadEntity> = dao.active()

    suspend fun byId(id: Long): DownloadEntity? = dao.byId(id)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    suspend fun updateProgress(id: Long, status: Int, bytes: Long, total: Long) {
        dao.updateProgress(id, status, bytes, total, System.currentTimeMillis())
    }
}
