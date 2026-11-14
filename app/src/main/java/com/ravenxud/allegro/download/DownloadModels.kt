package com.ravenxud.allegro.download

/** 下载状态机（与 DB 中的 status 对应）。 */
object DownloadStatus {
    const val PENDING = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
    const val CANCELED = 4
    const val PAUSED = 5
}

/** 运行中的下载进度。 */
data class DownloadProgress(
    val id: Long,
    val fileName: String,
    val status: Int,
    val bytes: Long,
    val total: Long,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = -1,
) {
    val percent: Int
        get() = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0
}
