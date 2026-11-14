package com.ravenxud.allegro.ui

import com.ravenxud.allegro.browser.TabState
import com.ravenxud.allegro.data.model.TabGroup

/**
 * 把「分组 + 标签」整理成 UI 需要的分段结构。
 *
 * 规则：
 * - 分组按 position 排序；组内标签保持原顺序；
 * - 折叠的分组仍然出现在列表里（只是类目不展示标签），否则用户没法展开；
 * - 「未分组」永远排最后，且为空时不出现。
 *
 * 纯函数，便于单测。
 */
object TabGrouping {

    data class Section(
        val group: TabGroup?,
        val tabs: List<TabState>,
    ) {
        val isUngrouped: Boolean get() = group == null
        val title: String get() = group?.name ?: "未分组"
        val collapsed: Boolean get() = group?.collapsed == true
    }

    fun sections(groups: List<TabGroup>, tabs: List<TabState>): List<Section> {
        val byGroup = tabs.groupBy { it.groupId }
        val known = groups.map { it.id }.toSet()
        val sections = groups.map { group ->
            Section(group = group, tabs = byGroup[group.id].orEmpty())
        }
        // 指向了已删除分组的标签，一律归到未分组，避免"消失的标签"
        val ungrouped = tabs.filter { it.groupId == null || it.groupId !in known }
        return if (ungrouped.isEmpty()) sections else sections + Section(group = null, tabs = ungrouped)
    }

    /** 当前可见（未被折叠隐藏）的标签数量，用于角标与测试。 */
    fun visibleCount(sections: List<Section>): Int =
        sections.sumOf { if (it.collapsed) 0 else it.tabs.size }
}
