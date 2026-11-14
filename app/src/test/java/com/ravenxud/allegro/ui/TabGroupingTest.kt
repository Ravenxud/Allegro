package com.ravenxud.allegro.ui

import com.ravenxud.allegro.browser.TabState
import com.ravenxud.allegro.data.model.TabGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGroupingTest {

    private fun tab(id: String, groupId: String? = null) =
        TabState(id = id, url = "https://$id.test/", title = id, groupId = groupId)

    private val work = TabGroup("g1", "工作", position = 0, collapsed = false)
    private val read = TabGroup("g2", "阅读", position = 1, collapsed = true)

    @Test
    fun `groups in order then ungrouped last`() {
        val tabs = listOf(tab("a", "g1"), tab("b", null), tab("c", "g2"))
        val sections = TabGrouping.sections(listOf(work, read), tabs)
        assertEquals(listOf("工作", "阅读", "未分组"), sections.map { it.title })
        assertEquals(listOf("a"), sections[0].tabs.map { it.id })
        assertEquals(listOf("c"), sections[1].tabs.map { it.id })
        assertEquals(listOf("b"), sections[2].tabs.map { it.id })
    }

    @Test
    fun `no ungrouped section when empty`() {
        val sections = TabGrouping.sections(listOf(work), listOf(tab("a", "g1")))
        assertEquals(1, sections.size)
        assertTrue(sections.none { it.isUngrouped })
    }

    @Test
    fun `tabs pointing at deleted group fall into ungrouped`() {
        val sections = TabGrouping.sections(listOf(work), listOf(tab("a", "gone"), tab("b", "g1")))
        val ungrouped = sections.first { it.isUngrouped }
        assertEquals(listOf("a"), ungrouped.tabs.map { it.id })
    }

    @Test
    fun `collapsed groups keep header but hide tabs`() {
        val sections = TabGrouping.sections(listOf(work, read), listOf(tab("a", "g1"), tab("c", "g2")))
        assertTrue(sections[1].collapsed)
        assertEquals(1, sections[1].tabs.size) // 数据仍在，界面自行隐藏
        assertEquals(1, TabGrouping.visibleCount(sections))
    }

    @Test
    fun `empty groups still show as sections`() {
        val sections = TabGrouping.sections(listOf(TabGroup("g3", "空组")), emptyList())
        assertEquals(1, sections.size)
        assertTrue(sections[0].tabs.isEmpty())
        assertEquals("空组", sections[0].title)
    }
}
