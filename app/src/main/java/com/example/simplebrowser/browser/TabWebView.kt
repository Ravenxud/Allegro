package com.example.simplebrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.webkit.WebViewClient

/**
 * 单个页面容器：只把系统 WebView 的事件翻译成回调，
 * 业务逻辑全部交给 BrowserController。
 */
class TabWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {

    interface Callbacks {
        fun onPageStarted(view: TabWebView, url: String)
        fun onPageFinished(view: TabWebView, url: String)
        fun onProgressChanged(view: TabWebView, progress: Int)
        fun onTitleChanged(view: TabWebView, title: String)
        fun onFaviconReceived(view: TabWebView, icon: Bitmap?)
        fun onUrlChanged(view: TabWebView, url: String)
        fun onMainFrameError(view: TabWebView, errorCode: Int, description: String, failingUrl: String?)
        fun onDownloadRequested(view: TabWebView, request: DownloadRequest)
        fun onLongPress(view: TabWebView, hit: WebView.HitTestResult)

        /** @return true 表示拦截该请求。[isForMainFrame] 为主文档时不拦。 */
        fun shouldBlockRequest(url: String, isForMainFrame: Boolean): Boolean
        fun onBlobDownload(view: TabWebView, fileName: String, mimeType: String, base64: String)
        fun onScrollState(view: TabWebView, atTop: Boolean, allowPull: Boolean, scrollY: Int)

        fun onReaderExtracted(view: TabWebView, json: String)

        fun onElementPicked(view: TabWebView, selector: String)

        fun onPageText(view: TabWebView, text: String)

        fun onBlobTooLarge(view: TabWebView, fileName: String)
    }

    data class DownloadRequest(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?,
        val contentLength: Long,
    )

    /** 所属标签 id，由控制器在创建时写入。 */
    var tabId: String = ""

    lateinit var callbacks: Callbacks

    var mobileUserAgent: String = ""
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
            mobileUserAgent = userAgentString
            // 滚动与交互优先，减少主线程争用导致的掉帧
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            // 允许页面在后台预取，提升前进/后退命中率
            cacheMode = WebSettings.LOAD_DEFAULT
            // 离线页是 files/offline 下的本地 HTML，需要 file:// 访问；
            // 只开 allowFileAccess，不开 allowFileAccessFromFileURLs（避免本地页跨域读文件）
            allowFileAccess = true
            // 安全浏览（与系统 WebView 联动）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        webViewClient = Client()
        webChromeClient = ChromeClient()

        setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            callbacks.onDownloadRequested(
                this,
                DownloadRequest(url, userAgent, contentDisposition, mimeType, contentLength),
            )
        }

        addJavascriptInterface(JsBridge(), "SimpleBrowserBridge")

        setOnLongClickListener {
            val type = hitTestResult.type
            val interesting = type == HitTestResult.ANCHOR_TYPE ||
                type == HitTestResult.SRC_ANCHOR_TYPE ||
                type == HitTestResult.IMAGE_TYPE ||
                type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            if (interesting && hitTestResult.extra != null) {
                callbacks.onLongPress(this, hitTestResult)
                true
            } else {
                false
            }
        }
    }

    /** 只用于 blob 下载回传，不暴露任何可执行能力。 */
    private inner class JsBridge {

        @JavascriptInterface
        fun onPageText(secret: String?, text: String?) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onPageText(this@TabWebView, text.orEmpty()) }
        }

        @JavascriptInterface
        fun onElementPicked(secret: String?, selector: String?) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onElementPicked(this@TabWebView, selector.orEmpty()) }
        }

        @JavascriptInterface
        fun onReaderResult(secret: String?, json: String?) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onReaderExtracted(this@TabWebView, json.orEmpty()) }
        }

        @JavascriptInterface
        fun onMediaFound(secret: String?, json: String?) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            android.util.Log.d("MediaSniffer", json.orEmpty().take(300))
        }

        @JavascriptInterface
        fun onScrollY(secret: String?, atTop: Int, allowPull: Int, y: Int) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onScrollState(this@TabWebView, atTop == 1, allowPull == 1, y) }
        }

        @JavascriptInterface
        fun onBlobDownload(secret: String?, fileName: String?, mimeType: String?, base64: String?) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            val data = base64?.takeIf { it.isNotEmpty() } ?: return
            post { callbacks.onBlobDownload(this@TabWebView, fileName ?: "download", mimeType ?: "application/octet-stream", data) }
        }

        fun onBlobTooLarge(secret: String?, fileName: String?) {
            if (!com.example.simplebrowser.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onBlobTooLarge(this@TabWebView, fileName ?: "download") }
        }
    }

    private inner class Client : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            // 主文档永不拦截：整页被拦就是白屏，比漏广告严重得多
            if (request.isForMainFrame) return null
            if (!callbacks.shouldBlockRequest(request.url.toString(), false)) return null
            return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (!request.isForMainFrame) return false
            if (!url.startsWith("http://") && !url.startsWith("https://")) return true
            callbacks.onUrlChanged(this@TabWebView, url)
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            com.example.simplebrowser.js.BridgeAuth.rotate()
            url?.let { callbacks.onPageStarted(this@TabWebView, it) }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            url?.let { callbacks.onPageFinished(this@TabWebView, it) }
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            url?.let { callbacks.onUrlChanged(this@TabWebView, it) }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame != true) return
            callbacks.onMainFrameError(
                this@TabWebView,
                error?.errorCode ?: -1,
                error?.description?.toString().orEmpty(),
                request.url?.toString(),
            )
        }
    }

    private inner class ChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            callbacks.onProgressChanged(this@TabWebView, newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            title?.let { callbacks.onTitleChanged(this@TabWebView, it) }
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            callbacks.onFaviconReceived(this@TabWebView, icon)
        }
    }
}
