package com.ravenxud.allegro.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ravenxud.allegro.data.entity.ClosedTabEntity

@Dao
interface ClosedTabDao {

    @Insert
    suspend fun insert(item: ClosedTabEntity): Long

    @Query("SELECT * FROM closed_tabs WHERE deleted = 0 ORDER BY closedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ClosedTabEntity>

    @Query("DELETE FROM closed_tabs WHERE id NOT IN (SELECT id FROM closed_tabs ORDER BY closedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM closed_tabs WHERE id = :id")
    suspend fun delete(id: Long)
}
