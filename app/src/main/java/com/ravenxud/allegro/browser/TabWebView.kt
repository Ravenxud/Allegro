package com.ravenxud.allegro.browser

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
import android.view.MotionEvent
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

        /** 页面要求 window.open / target=_blank 打开新窗口。 */
        fun onNewWindowRequest(view: TabWebView, url: String)

        /** 渲染进程被杀（重页面/内存压力），调用方需要重建 WebView。 */
        fun onRendererGone(view: TabWebView)
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

    /**
     * 网页里点输入框时，先把焦点从 Compose 的地址栏抢过来，
     * 否则输入法会被 Compose 侧"占着"，表现就是点了输入框不弹键盘。
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !hasFocus()) {
            requestFocus()
        }
        return super.onTouchEvent(event)
    }

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
            // 允许 window.open：飞书/微信这类站点大量使用弹窗，关掉会导致"点了没反应"
            setSupportMultipleWindows(true)
            // 离线页是 files/offline 下的本地 HTML，需要 file:// 访问；
            // 只开 allowFileAccess，不开 allowFileAccessFromFileURLs（避免本地页跨域读文件）
            allowFileAccess = true
            // 安全浏览（与系统 WebView 联动）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        isFocusable = true
        isFocusableInTouchMode = true
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
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onPageText(this@TabWebView, text.orEmpty()) }
        }

        @JavascriptInterface
        fun onElementPicked(secret: String?, selector: String?) {
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onElementPicked(this@TabWebView, selector.orEmpty()) }
        }

        @JavascriptInterface
        fun onReaderResult(secret: String?, json: String?) {
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onReaderExtracted(this@TabWebView, json.orEmpty()) }
        }

        @JavascriptInterface
        fun onMediaFound(secret: String?, json: String?) {
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
            android.util.Log.d("MediaSniffer", json.orEmpty().take(300))
        }

        @JavascriptInterface
        fun onScrollY(secret: String?, atTop: Int, allowPull: Int, y: Int) {
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
            post { callbacks.onScrollState(this@TabWebView, atTop == 1, allowPull == 1, y) }
        }

        @JavascriptInterface
        fun onBlobDownload(secret: String?, fileName: String?, mimeType: String?, base64: String?) {
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
            val data = base64?.takeIf { it.isNotEmpty() } ?: return
            post { callbacks.onBlobDownload(this@TabWebView, fileName ?: "download", mimeType ?: "application/octet-stream", data) }
        }

        fun onBlobTooLarge(secret: String?, fileName: String?) {
            if (!com.ravenxud.allegro.js.BridgeAuth.isValid(secret)) return
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
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                // lark://、weixin://、intent:// 这类交给系统；解析不到就提示，别静默失败
                val opened = runCatching {
                    val intent = if (url.startsWith("intent:")) {
                        android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                    } else {
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    }
                    context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (!opened) {
                    android.widget.Toast.makeText(
                        context,
                        com.ravenxud.allegro.R.string.link_open_external_failed,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                return true
            }
            callbacks.onUrlChanged(this@TabWebView, url)
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            com.ravenxud.allegro.js.BridgeAuth.rotate()
            url?.let { callbacks.onPageStarted(this@TabWebView, it) }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            url?.let { callbacks.onPageFinished(this@TabWebView, it) }
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            url?.let { callbacks.onUrlChanged(this@TabWebView, it) }
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: android.webkit.RenderProcessGoneDetail?,
        ): Boolean {
            callbacks.onRendererGone(this@TabWebView)
            return true
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

        /**
         * window.open / target=_blank：用一个临时 WebView 收下目标 URL，
         * 拿到后交给控制器开新标签，然后销毁临时实例（不真的在里面加载）。
         */
        /** 站点调 window.close() 时什么都不做：关掉主窗口会让用户"页面自己没了"。 */
        override fun onCloseWindow(window: WebView?) = Unit

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?,
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val probe = WebView(context)
            probe.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    if (target.startsWith("http://") || target.startsWith("https://")) {
                        callbacks.onNewWindowRequest(this@TabWebView, target)
                    }
                    v.post { runCatching { v.destroy() } }
                    return true
                }
            }
            transport.webView = probe
            resultMsg.sendToTarget()
            return true
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            title?.let { callbacks.onTitleChanged(this@TabWebView, it) }
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            callbacks.onFaviconReceived(this@TabWebView, icon)
        }
    }
}
