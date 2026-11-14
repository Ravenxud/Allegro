package com.ravenxud.allegro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 书签文件夹。id 用 UUID，便于导入导出时保持引用。 */
@Entity(tableName = "bookmark_folders")
data class BookmarkFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
