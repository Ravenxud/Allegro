package com.ravenxud.allegro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 标签分组。折叠状态跟着分组走，跨重启保留。 */
@Entity(tableName = "tab_groups")
data class TabGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int = 0,
    val position: Int = 0,
    val collapsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
