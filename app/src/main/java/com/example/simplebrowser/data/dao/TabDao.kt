package com.example.simplebrowser.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.simplebrowser.data.entity.TabEntity

@Dao
interface TabDao {

    @Query("SELECT * FROM tabs WHERE deleted = 0 ORDER BY position ASC")
    suspend fun getAll(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<TabEntity>)

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()

    /** 整表替换，保证顺序与内存态一致。 */
    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>) {
        deleteAll()
        insertAll(tabs)
    }
}
