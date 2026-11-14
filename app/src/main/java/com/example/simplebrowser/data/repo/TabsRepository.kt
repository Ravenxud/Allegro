package com.example.simplebrowser.data.repo

import com.example.simplebrowser.data.dao.ClosedTabDao
import com.example.simplebrowser.data.dao.TabDao
import com.example.simplebrowser.data.entity.ClosedTabEntity
import com.example.simplebrowser.data.entity.TabEntity

/** 标签与"最近关闭"的读写入口。 */
class TabsRepository(
    private val tabDao: TabDao,
    private val closedTabDao: ClosedTabDao,
) {

    suspend fun loadTabs(): List<TabEntity> = tabDao.getAll()

    suspend fun saveTabs(tabs: List<TabEntity>) = tabDao.replaceAll(tabs)

    suspend fun addClosedTab(entity: ClosedTabEntity) {
        closedTabDao.insert(entity)
        closedTabDao.trim(KEEP_CLOSED)
    }

    suspend fun recentClosed(limit: Int = 10): List<ClosedTabEntity> = closedTabDao.recent(limit)

    suspend fun removeClosed(id: Long) = closedTabDao.delete(id)

    private companion object {
        const val KEEP_CLOSED = 20
    }
}
