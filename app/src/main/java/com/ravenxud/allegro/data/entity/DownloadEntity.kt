package com.ravenxud.allegro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 下载记录。dmId 对应系统 DownloadManager 的 id，
 * 本里程碑只写入不读取，T11/T11b 会补齐进度与通知。
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dmId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val status: Int,
    val bytesSoFar: Long = 0,
    val totalBytes: Long = -1,
    val createdAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
