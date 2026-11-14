package com.example.simplebrowser.data.repo

import com.example.simplebrowser.data.dao.BookmarkDao
import com.example.simplebrowser.data.entity.BookmarkEntity
import com.example.simplebrowser.data.entity.BookmarkFolderEntity
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val dao: BookmarkDao) {

    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll()

    suspend fun all(): List<BookmarkEntity> = dao.all()

    suspend fun isBookmarked(url: String): Boolean = dao.countByUrl(url) > 0

    suspend fun add(title: String, url: String) {
        dao.insert(
            BookmarkEntity(
                title = title.ifBlank { url },
                url = url,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun remove(url: String) = dao.deleteByUrl(url)

    suspend fun clear() = dao.clear()

    // ---------------- 文件夹 ----------------

    fun observeFolders(): Flow<List<BookmarkFolderEntity>> = dao.observeFolders()

    suspend fun folders(): List<BookmarkFolderEntity> = dao.folders()

    suspend fun createFolder(name: String): BookmarkFolderEntity {
        val existing = dao.folders()
        val folder = BookmarkFolderEntity(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "未命名" },
            position = existing.size,
            createdAt = System.currentTimeMillis(),
        )
        dao.insertFolder(folder)
        return folder
    }

    suspend fun deleteFolder(id: String) {
        dao.detachFolder(id)
        dao.deleteFolder(id)
    }

    suspend fun moveToFolder(url: String, folderId: String?) = dao.moveToFolder(url, folderId)

    suspend fun reorder(url: String, position: Int) = dao.setPosition(url, position)

    /** 导入用：批量写入并写入文件夹。 */
    suspend fun importAll(folders: List<BookmarkFolderEntity>, bookmarks: List<BookmarkEntity>) {
        folders.forEach { dao.insertFolder(it) }
        dao.insertAll(bookmarks)
    }
}
