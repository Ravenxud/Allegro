package com.ravenxud.allegro.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ravenxud.allegro.data.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    /**
     * 按本地行 id 更新（自研下载器用 id 作为任务标识）。
     * 注意：早期版本误用 dmId 列匹配，导致状态永远写不到对应记录。
     */
    @Query(
        "UPDATE downloads SET status = :status, bytesSoFar = :bytes, totalBytes = :total, " +
            "updatedAt = :now WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, status: Int, bytes: Long, total: Long, now: Long)

    @Query("SELECT * FROM downloads WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE deleted = 0 AND status IN (0, 1)")
    suspend fun active(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun byId(id: Long): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
