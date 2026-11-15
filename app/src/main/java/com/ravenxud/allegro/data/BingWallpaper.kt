package com.ravenxud.allegro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Bing 每日壁纸：按天缓存，避免每次进首页都下载。
 *
 * 命中的顺序：
 * 1. 私有目录里今天的缓存 → 直接用
 * 2. 否则请求 HPImageArchive 拿 urlbase，下载竖屏版本（1080x1920）落盘
 * 3. 网络失败但有旧缓存 → 用旧的（宁可过期也别白屏）
 */
object BingWallpaper {

    private const val META = "bing_wallpaper_day"
    private const val IMAGE = "bing_wallpaper.jpg"
    private const val ARCHIVE = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 阻塞调用，必须在 IO 线程执行。 */
    fun load(context: Context): Bitmap? {
        val dir = context.filesDir
        val image = File(dir, IMAGE)
        val meta = File(dir, META)
        val today = todayKey()
        val cachedToday = meta.exists() && meta.readText().trim() == today && image.exists()

        if (!cachedToday) {
            runCatching { download(image) }
                .onSuccess { runCatching { meta.writeText(today) } }
        }
        if (!image.exists()) return null
        return runCatching { BitmapFactory.decodeFile(image.absolutePath) }.getOrNull()
    }

    private fun download(target: File) {
        val json = client.newCall(Request.Builder().url(ARCHIVE).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("bing archive ${resp.code}")
            resp.body?.string() ?: error("bing archive empty")
        }
        val urlBase = JSONObject(json)
            .getJSONArray("images")
            .getJSONObject(0)
            .getString("urlbase")
        // 竖屏优先，Bing 会按后缀返回对应分辨率
        val candidates = listOf(
            "https://www.bing.com$urlBase" + "_1080x1920.jpg",
            "https://www.bing.com$urlBase" + "_1920x1080.jpg",
        )
        var lastError: Throwable? = null
        for (url in candidates) {
            val bytes = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("bing image ${resp.code}")
                    resp.body?.bytes() ?: error("bing image empty")
                }
            }.getOrElse { lastError = it; null } ?: continue
            if (bytes.size < 10_000) continue
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return
        }
        error(lastError ?: IllegalStateException("bing image unavailable"))
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
