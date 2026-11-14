package com.ravenxud.allegro.data.model

/** 标签分组的轻量模型（UI 只认这个，不碰 Room 实体）。 */
data class TabGroup(
    val id: String,
    val name: String,
    val position: Int = 0,
    val collapsed: Boolean = false,
)
