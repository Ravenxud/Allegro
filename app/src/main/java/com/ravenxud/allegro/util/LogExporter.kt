package com.ravenxud.allegro.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * 诊断日志导出。
 *
 * 为什么需要三种方式：只提供 `ACTION_SEND` 时，如果系统分享面板里没有合适的接收方
 * （很多精简 ROM 会裁掉文件分享目标），用户就会遇到"点了没反应/导出不了"。
 * 这里额外提供「复制到剪贴板」与「写入系统下载目录」两条不依赖分享面板的路径。
 */
object LogExporter {

    /** 读取当前日志文本；为空时返回 null。 */
    fun readLog(): String? {
        val text = DiagLog.read()
        return text.takeIf { it.isNotBlank() }
    }

    /** 复制到剪贴板，返回是否成功。 */
    fun copyToClipboard(context: Context): Boolean {
        val text = readLog() ?: return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            ?: return false
        return runCatching {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Allegro diag.log", text))
            true
        }.getOrDefault(false)
    }

    /**
     * 写入系统「下载」目录（Android 10+ 走 MediaStore，无需存储权限）。
     * @return 实际文件名，失败返回 null。
     */
    fun saveToDownloads(context: Context): String? {
        val text = readLog() ?: return null
        val name = "allegro-diag-${System.currentTimeMillis()}.log"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return null
                name
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                val file = java.io.File(dir, name)
                file.writeText(text)
                file.name
            }
        }.getOrNull()
    }

    /** 清空日志文件。 */
    fun clear(): Boolean = runCatching {
        DiagLog.clear()
        true
    }.getOrDefault(false)
}
