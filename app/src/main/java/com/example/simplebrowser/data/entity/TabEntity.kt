package com.example.simplebrowser.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 标签页持久化实体。
 *
 * favicon 与 state 都是二进制：state 用于后续里程碑存放 WebView.saveState 的序列化结果，
 * 本里程碑先留空。
 */
@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val favicon: ByteArray? = null,
    val position: Int,
    val pinned: Boolean = false,
    val state: ByteArray? = null,
    val lastAccessedAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
