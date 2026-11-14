package com.example.simplebrowser.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simplebrowser.data.entity.BookmarkEntity
import com.example.simplebrowser.data.entity.BookmarkFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BookmarkEntity>)

    @Query("SELECT * FROM bookmarks WHERE deleted = 0 ORDER BY folderId IS NOT NULL, position ASC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE deleted = 0 ORDER BY position ASC, createdAt DESC")
    suspend fun all(): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url AND deleted = 0")
    suspend fun countByUrl(url: String): Int

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE bookmarks SET folderId = :folderId WHERE url = :url")
    suspend fun moveToFolder(url: String, folderId: String?)

    @Query("UPDATE bookmarks SET position = :position WHERE url = :url")
    suspend fun setPosition(url: String, position: Int)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()

    // ---------------- 文件夹 ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: BookmarkFolderEntity)

    @Query("SELECT * FROM bookmark_folders WHERE deleted = 0 ORDER BY position ASC, createdAt ASC")
    fun observeFolders(): Flow<List<BookmarkFolderEntity>>

    @Query("SELECT * FROM bookmark_folders WHERE deleted = 0 ORDER BY position ASC, createdAt ASC")
    suspend fun folders(): List<BookmarkFolderEntity>

    @Query("DELETE FROM bookmark_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE bookmarks SET folderId = NULL WHERE folderId = :id")
    suspend fun detachFolder(id: String)
}
