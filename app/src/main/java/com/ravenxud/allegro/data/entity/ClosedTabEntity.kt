package com.ravenxud.allegro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 最近关闭的标签，用于"撤销关闭"。 */
@Entity(tableName = "closed_tabs")
data class ClosedTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val closedAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
