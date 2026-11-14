package com.example.simplebrowser

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.simplebrowser.browser.BrowserController
import com.example.simplebrowser.browser.UrlParser
import com.example.simplebrowser.browser.WebViewBrowserController
import com.example.simplebrowser.data.model.SearchEngines
import com.example.simplebrowser.di.ServiceLocator
import com.example.simplebrowser.ui.BrowserScreen
import com.example.simplebrowser.ui.theme.BrowserTheme

/**
 * 唯一 Activity：主题由 [BrowserTheme] 驱动，UI 交给 [BrowserScreen]。
 * 返回键：网页历史 → 双击退出。
 */
class MainActivity : ComponentActivity() {

    private lateinit var controller: BrowserController
    private val searchEngine get() = ServiceLocator.settingsRepository.searchEngine()
    private var lastBackAt = 0L

    /** 预测性返回手势进度：0 = 未开始，1 = 即将完成。 */
    private val backProgress = mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceLocator.init(applicationContext)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ServiceLocator.adBlockRepository.ensureLoaded()
        }

        controller = WebViewBrowserController(
            context = this,
            tabsRepository = ServiceLocator.tabsRepository,
            historyRepository = ServiceLocator.historyRepository,
            downloadCenter = ServiceLocator.downloadCenter,
            adBlock = ServiceLocator.adBlockRepository,
        )

        setContent {
            val mode by ServiceLocator.settingsRepository.themeMode.collectAsState()
            BrowserTheme(mode = mode) {
                BrowserScreen(
                    controller = controller,
                    settings = ServiceLocator.settingsRepository,
                    bookmarks = ServiceLocator.bookmarkRepository,
                    history = ServiceLocator.historyRepository,
                    downloads = ServiceLocator.downloadRepository,
                    downloadCenter = ServiceLocator.downloadCenter,
                    adBlock = ServiceLocator.adBlockRepository,
                    update = ServiceLocator.updateRepository,
                    themeMode = mode,
                    onCycleTheme = { ServiceLocator.settingsRepository.cycleTheme() },
                    backProgress = backProgress.floatValue,
                )
            }
        }

        requestNotificationPermissionIfNeeded()
        registerBackHandler()

        val deepLink = extractUrl(intent)
        if (deepLink != null) {
            controller.openTab(deepLink)
        } else {
            controller.restoreTabs()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractUrl(intent)?.let { controller.openTab(it) }
    }

    override fun onPause() {
        super.onPause()
        controller.persistNow()
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }

    /** 网页可后退就先退；到根页面时连按两次退出。 */
    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    backProgress.floatValue = 0.01f
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    backProgress.floatValue = backEvent.progress.coerceIn(0f, 1f)
                }

                override fun handleOnBackCancelled() {
                    backProgress.floatValue = 0f
                }

                override fun handleOnBackPressed() {
                    backProgress.floatValue = 0f
                    handleBack()
                }
            },
        )
    }

    private fun handleBack() {
        val tab = controller.state.value.activeTab
        if (tab?.canGoBack == true) {
            controller.goBack()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < EXIT_WINDOW_MS) {
            finish()
        } else {
            lastBackAt = now
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> UrlParser.toUrl(
                intent.getStringExtra(SearchManager.QUERY).orEmpty(),
                searchEngine.queryUrl,
            )
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { UrlParser.toUrl(it, searchEngine.queryUrl) }
            Intent.ACTION_PROCESS_TEXT -> intent
                .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { UrlParser.toUrl(it, searchEngine.queryUrl) }
            else -> null
        }
    }

    private companion object {
        const val EXIT_WINDOW_MS = 2000L
    }
}
