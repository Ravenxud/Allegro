package com.ravenxud.allegro.desktop

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 桌面版主文档抓取器。
 *
 * 为什么不用 WebView 自己加载：WebView 无法修改 Client Hints，服务端会按"移动设备"处理。
 * 这里由 App 发请求，请求头完全可控（见 [DesktopHeaders]），重定向也自己跟随
 * （见 [DesktopRedirectPolicy]），因此不会有 m./www 互踢的死循环。
 *
 * 纯 JVM 实现（只依赖 OkHttp），可以直接跑单元测试。
 */
class DesktopDocumentFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val maxBytes: Int = 3 * 1024 * 1024,
    private val maxHops: Int = 5,
) {

    sealed interface Result {
        /** 成功拿到 HTML。[finalUrl] 与请求 URL 不同时，调用方需要注入 `<base>`。 */
        data class Html(
            val html: String,
            val finalUrl: String,
            val charset: String,
            val cookies: List<Pair<String, String>>,
        ) : Result

        /** 不该走代理（非 HTML、空响应等），交回 WebView 正常加载。 */
        data class Skipped(val reason: String) : Result

        /** 抓取失败（网络/证书/超限），同样交回 WebView。 */
        data class Failed(val reason: String) : Result
    }

    fun fetch(url: String, headers: Map<String, String>): Result {
        val policy = DesktopRedirectPolicy(maxHops).apply { start(url) }
        val cookies = ArrayList<Pair<String, String>>()
        var current = url

        repeat(maxHops + 1) {
            val request = Request.Builder()
                .url(current)
                .get()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val response = runCatching { client.newCall(request).execute() }
                .getOrElse { return Result.Failed("网络错误：${it.message}") }

            response.use { resp ->
                resp.headers.values("Set-Cookie").forEach { cookies += current to it }

                val code = resp.code
                if (code in 300..399) {
                    val location = resp.header("Location")
                        ?: return Result.Skipped("重定向缺少 Location")
                    val next = policy.next(current, location)
                        ?: return Result.Skipped("重定向被策略停止（疑似循环）")
                    current = next
                    return@repeat // 继续循环
                }
                if (code !in 200..299) return Result.Failed("HTTP $code")

                val mime = resp.header("Content-Type").orEmpty().lowercase()
                if (mime.isNotEmpty() && !mime.contains("text/html") && !mime.contains("application/xhtml")) {
                    return Result.Skipped("非 HTML：${mime.substringBefore(';')}")
                }
                val body = resp.body ?: return Result.Skipped("空响应")

                val length = body.contentLength()
                if (length > maxBytes) return Result.Skipped("文档过大：${length / 1024}KB")

                val bytes = runCatching { body.bytes() }
                    .getOrElse { return Result.Failed("读取失败：${it.message}") }
                if (bytes.size > maxBytes) return Result.Skipped("文档过大：${bytes.size / 1024}KB")
                if (bytes.isEmpty()) return Result.Skipped("空文档")

                val charset = charsetOf(mime)
                val html = runCatching { String(bytes, charset) }.getOrElse { String(bytes) }
                return Result.Html(html = html, finalUrl = current, charset = charset.name(), cookies = cookies)
            }
        }
        return Result.Skipped("重定向次数过多")
    }

    private fun charsetOf(contentType: String): java.nio.charset.Charset {
        val name = Regex("charset\\s*=\\s*\"?([^\";\\s]+)", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)
        return runCatching { java.nio.charset.Charset.forName(name.orEmpty()) }.getOrDefault(Charsets.UTF_8)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
