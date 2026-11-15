package com.ravenxud.allegro.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量诊断日志：把导航类事件写到 `filesDir/diag.log`，最多保留 [MAX_BYTES]。
 *
 * 为什么要有它：真机上"页面莫名其妙回到新标签页"这类问题，只看代码猜不准，
 * 但让用户跑 adb 也不现实。写文件 + 设置里一键分享，能把现象变成可分析的记录。
 *
 * 只记事件名、URL、错误码，不记页面内容、不记账号信息。
 */
object DiagLog {

    private const val MAX_BYTES = 256 * 1024L
    private const val FILE_NAME = "diag.log"
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null
    private val lock = Any()

    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    fun log(event: String, detail: String = "") {
        val target = file ?: return
        val line = "${formatter.format(Date())} [$event] $detail\n"
        synchronized(lock) {
            runCatching {
                target.appendText(line)
                if (target.length() > MAX_BYTES) {
                    // 简单轮转：保留后半段，避免无限增长
                    val keep = target.readText().takeLast((MAX_BYTES / 2).toInt())
                    target.writeText(keep)
                }
            }
        }
    }

    fun read(): String = synchronized(lock) {
        runCatching { file?.takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")
    }

    fun currentFile(): File? = file?.takeIf { it.exists() }

    /** 清空日志（导出后或排障前使用）。 */
    fun clear() {
        synchronized(lock) {
            runCatching { file?.writeText("") }
        }
    }
}
