package com.ravenxud.allegro.ui.screens

import com.ravenxud.allegro.ui.theme.AllegroIcons
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ravenxud.allegro.R
import com.ravenxud.allegro.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 阅读模式：正文用 WebView 渲染（保留图片与结构），
 * 顶栏提供字号调整、朗读与返回。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
    html: String,
    title: String,
    onBack: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
) {
    var fontSize by remember { mutableIntStateOf(18) }
    var dark by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val background = if (dark) "#121212" else "#FFFFFF"
    val foreground = if (dark) "#E8E8E8" else "#1A1A1A"

    fun render() {
        val body = """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html,body{background:$background;color:$foreground;margin:0;padding:16px 18px 48px;}
              body{font-size:${fontSize}px;line-height:1.75;font-family:-apple-system,"PingFang SC",sans-serif;}
              img,video,iframe{max-width:100%;height:auto;border-radius:8px;}
              pre{overflow:auto;background:rgba(127,127,127,.12);padding:10px;border-radius:8px;font-size:${fontSize - 2}px;}
              a{color:#0088FF;} h1,h2,h3{line-height:1.35;} figure{margin:0 0 16px;}
              blockquote{border-left:3px solid #0088FF;margin:0;padding-left:12px;color:#888;}
            </style></head><body>$html</body></html>
        """.trimIndent()
        webView?.loadDataWithBaseURL(null, body, "text/html", "utf-8", null)
    }

    LaunchedEffect(html, fontSize, dark) {
        render()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title.ifBlank { stringResource(R.string.reader_title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AllegroIcons.Back, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(12) }) {
                        Text("A-", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(30) }) {
                        Text("A+", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { dark = !dark; render() }) {
                        Text(
                            if (dark) "☾" else "☀",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = {
                        if (speaking) {
                            speaking = false
                            onStopSpeak()
                        } else {
                            speaking = true
                            onSpeak()
                        }
                    }) {
                        Icon(
                            if (speaking) AllegroIcons.Pause else AllegroIcons.Play,
                            stringResource(R.string.reader_speak),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = false
                        webViewClient = WebViewClient()
                        webView = this
                        loadDataWithBaseURL(
                            null,
                            "<html><body style='background:$background'></body></html>",
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                },
                update = { view -> webView = view },
            )
        }
    }
}
