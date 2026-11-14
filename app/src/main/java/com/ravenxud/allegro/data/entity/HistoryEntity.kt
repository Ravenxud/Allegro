package com.ravenxud.allegro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 历史记录以 URL 为主键，同一地址只保留最近一次访问时间。 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val lastVisitedAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
