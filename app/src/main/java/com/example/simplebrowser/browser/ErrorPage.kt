package com.example.simplebrowser.browser

import android.content.Context
import com.example.simplebrowser.R

/** 主文档加载失败时的本地错误页（跟随系统深浅色）。 */
internal object ErrorPage {

    fun build(context: Context, url: String, description: String): String {
        val safeUrl = url.replace("\"", "%22")
        val title = context.getString(R.string.error_page_title)
        val retry = context.getString(R.string.error_page_retry)
        val desc = description.ifBlank { "ERR" }.replace("<", "&lt;")
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html,body{height:100%;margin:0;background:#fff;color:#000;font-family:sans-serif}
              @media (prefers-color-scheme: dark){html,body{background:#121212;color:#F2F2F2}}
              .wrap{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:24px;box-sizing:border-box;text-align:center}
              h1{font-size:18px;font-weight:600;margin:0}
              p{font-size:13px;color:#8A8A8E;margin:0;word-break:break-all}
              a{margin-top:8px;padding:10px 28px;border-radius:12px;background:#0088FF;color:#fff;text-decoration:none;font-size:15px}
            </style></head>
            <body><div class="wrap">
              <h1>$title</h1>
              <p>$desc</p>
              <p>$safeUrl</p>
              <a href="$safeUrl">$retry</a>
            </div></body></html>
        """.trimIndent()
    }
}
