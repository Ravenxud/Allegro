package com.example.simplebrowser

import android.app.Application
import com.example.simplebrowser.di.ServiceLocator

class SimpleBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        warmUpWebView()
    }

    /**
     * 启动时在后台线程预热 WebView：把内核与 provider 初始化提前，
     * 用户真正打开第一个页面时可省掉 150~400ms 的初始化时间。
     */
    private fun warmUpWebView() {
        runCatching {
            android.webkit.WebView.setWebContentsDebuggingEnabled(false)
        }
        Thread {
            runCatching {
                android.webkit.WebView(this).apply {
                    // 仅用于触发内核加载，不渲染
                    settings.javaScriptEnabled = true
                    loadUrl("about:blank")
                    destroy()
                }
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }
}
