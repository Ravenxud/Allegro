package com.example.simplebrowser.di

import android.content.Context
import com.example.simplebrowser.adblock.AdBlockRepository
import com.example.simplebrowser.data.AppDatabase
import com.example.simplebrowser.data.SettingsRepository
import com.example.simplebrowser.data.UserScriptStore
import com.example.simplebrowser.data.repo.BookmarkRepository
import com.example.simplebrowser.data.repo.DownloadRepository
import com.example.simplebrowser.download.DownloadCenter
import com.example.simplebrowser.update.UpdateRepository
import com.example.simplebrowser.data.repo.HistoryRepository
import com.example.simplebrowser.data.repo.TabsRepository

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
            updateRepository = UpdateRepository(
                appContext,
                settingsRepository.updateRepo(),
            )
            initialized = true
        }
    }
}
