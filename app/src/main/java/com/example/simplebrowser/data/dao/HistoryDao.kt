package com.example.simplebrowser.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simplebrowser.data.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("SELECT * FROM history WHERE deleted = 0 ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}
