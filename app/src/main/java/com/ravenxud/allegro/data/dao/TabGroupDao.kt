package com.ravenxud.allegro.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ravenxud.allegro.data.entity.TabGroupEntity

@Dao
interface TabGroupDao {

    @Query("SELECT * FROM tab_groups ORDER BY position ASC, createdAt ASC")
    suspend fun all(): List<TabGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: TabGroupEntity)

    @Query("DELETE FROM tab_groups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tab_groups SET collapsed = :collapsed, updatedAt = :now WHERE id = :id")
    suspend fun setCollapsed(id: String, collapsed: Boolean, now: Long = System.currentTimeMillis())

    /** 删除分组时把成员移回「未分组」。 */
    @Query("UPDATE tabs SET groupId = NULL WHERE groupId = :groupId")
    suspend fun detachTabs(groupId: String)
}
