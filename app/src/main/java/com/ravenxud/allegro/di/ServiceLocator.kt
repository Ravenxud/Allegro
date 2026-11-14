package com.ravenxud.allegro.di

import android.content.Context
import com.ravenxud.allegro.adblock.AdBlockRepository
import com.ravenxud.allegro.data.AppDatabase
import com.ravenxud.allegro.data.SettingsRepository
import com.ravenxud.allegro.data.site.SiteSettingsRepository
import com.ravenxud.allegro.data.UserScriptStore
import com.ravenxud.allegro.data.repo.BookmarkRepository
import com.ravenxud.allegro.data.repo.DownloadRepository
import com.ravenxud.allegro.download.DownloadCenter
import com.ravenxud.allegro.sync.SyncRepository
import com.ravenxud.allegro.update.UpdateRepository
import com.ravenxud.allegro.data.repo.HistoryRepository
import com.ravenxud.allegro.data.repo.TabsRepository

/** 手写单例容器，替代 Hilt；进程内只初始化一次。 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var appContext: Context
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var tabsRepository: TabsRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var bookmarkRepository: BookmarkRepository
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var downloadCenter: DownloadCenter
        private set

    lateinit var adBlockRepository: AdBlockRepository
        private set

    lateinit var updateRepository: UpdateRepository
        private set

    lateinit var userScriptStore: UserScriptStore
        private set

    lateinit var siteSettingsRepository: SiteSettingsRepository
        private set

    lateinit var syncRepository: SyncRepository
        private set

    /** 更新源变更后重建仓库实例。 */
    fun rebindUpdateRepo(repo: String) {
        updateRepository = UpdateRepository(appContext, repo)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            database = AppDatabase.get(appContext)
            tabsRepository = TabsRepository(database.tabDao(), database.closedTabDao())
            historyRepository = HistoryRepository(database.historyDao())
            bookmarkRepository = BookmarkRepository(database.bookmarkDao())
            downloadRepository = DownloadRepository(database.downloadDao())
            settingsRepository = SettingsRepository(appContext)
            downloadCenter = DownloadCenter(appContext, downloadRepository)
            adBlockRepository = AdBlockRepository(appContext)
            userScriptStore = UserScriptStore(appContext)
            siteSettingsRepository = SiteSettingsRepository(appContext)
            syncRepository = SyncRepository(
                appContext,
                bookmarkRepository,
                siteSettingsRepository,
                settingsRepository,
                adBlockRepository,
            )
            updateRepository = UpdateRepository(
                appContext,
                settingsRepository.updateRepo(),
            )
            initialized = true
        }
    }
}
