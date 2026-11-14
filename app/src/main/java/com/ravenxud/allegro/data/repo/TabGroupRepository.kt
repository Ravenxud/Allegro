package com.ravenxud.allegro.data.repo

import com.ravenxud.allegro.data.dao.TabGroupDao
import com.ravenxud.allegro.data.entity.TabGroupEntity
import com.ravenxud.allegro.data.model.TabGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 标签分组仓库。
 *
 * 分组本身存在 `tab_groups`，成员关系存在 `tabs.groupId`（比中间表简单：
 * 一个标签只属于一个组，删除分组时把成员置空即可）。
 */
class TabGroupRepository(private val dao: TabGroupDao) {

    private val _groups = MutableStateFlow<List<TabGroup>>(emptyList())
    val groups: StateFlow<List<TabGroup>> = _groups.asStateFlow()

    suspend fun load(): List<TabGroup> {
        val list = runCatching { dao.all() }.getOrDefault(emptyList()).map {
            TabGroup(it.id, it.name, it.position, it.collapsed)
        }
        _groups.value = list
        return list
    }

    suspend fun create(name: String): TabGroup {
        val current = _groups.value
        val group = TabGroup(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "未命名分组" },
            position = current.size,
        )
        runCatching {
            dao.upsert(
                TabGroupEntity(
                    id = group.id,
                    name = group.name,
                    position = group.position,
                    collapsed = false,
                ),
            )
        }
        _groups.value = current + group
        return group
    }

    suspend fun rename(id: String, name: String) {
        val group = _groups.value.firstOrNull { it.id == id } ?: return
        val clean = name.trim().ifBlank { group.name }
        runCatching {
            dao.upsert(
                TabGroupEntity(
                    id = group.id,
                    name = clean,
                    position = group.position,
                    collapsed = group.collapsed,
                ),
            )
        }
        _groups.value = _groups.value.map { if (it.id == id) it.copy(name = clean) else it }
    }

    suspend fun delete(id: String) {
        runCatching {
            dao.detachTabs(id)
            dao.delete(id)
        }
        _groups.value = _groups.value.filterNot { it.id == id }
    }

    suspend fun setCollapsed(id: String, collapsed: Boolean) {
        runCatching { dao.setCollapsed(id, collapsed) }
        _groups.value = _groups.value.map { if (it.id == id) it.copy(collapsed = collapsed) else it }
    }
}
