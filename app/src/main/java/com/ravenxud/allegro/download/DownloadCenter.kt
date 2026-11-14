package com.ravenxud.allegro.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.ravenxud.allegro.data.entity.DownloadEntity
import com.ravenxud.allegro.data.repo.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 自研下载器：Range 断点续传 + 暂停/继续/取消。
 *
 * 关键点（0.17.1 修复）：
 * - OkHttp 的 `execute()` 是阻塞的，协程取消**不会**中断读取，
 *   必须持有 [Call] 并 `call.cancel()`，否则"暂停"后文件仍在增长、状态被并发改写；
 * - 用 [intent] 记录用户意图（pause / cancel），避免 try/catch 误判成失败；
 * - 恢复前先 `join()` 上一个任务，保证同一时刻只有一个写者。
 */
class DownloadCenter(
    private val context: Context,
    private val repository: DownloadRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _progress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, DownloadProgress>> = _progress.asStateFlow()

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val calls = ConcurrentHashMap<Long, Call>()
    private val intents = ConcurrentHashMap<Long, String>()
    private val samples = ConcurrentHashMap<Long, Pair<Long, Long>>()

    fun dir(): File = File(context.filesDir, "Download").apply { mkdirs() }

    fun enqueue(
        url: String,
        userAgent: String? = null,
        mimeType: String? = null,
        contentDisposition: String? = null,
    ): Long? {
        if (url.isBlank()) return null
        val fileName = safeName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        var id = -1L
        val latch = CountDownLatch(1)
        scope.launch {
            id = repository.add(
                DownloadEntity(
                    dmId = 0L,
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    status = DownloadStatus.RUNNING,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            latch.countDown()
            runDownload(id, url, fileName, userAgent)
        }
        latch.await()
        DownloadService.start(context)
        return id.takeIf { it > 0 }
    }

    fun pause(id: Long) {
        intents[id] = INTENT_PAUSE
        calls.remove(id)?.cancel()
        jobs[id]?.cancel()
        samples.remove(id)
        scope.launch {
            val bytes = fileOf(id).length()
            repository.updateProgress(id, DownloadStatus.PAUSED, bytes, -1)
        }
    }

    fun resume(id: Long) {
        val previous = jobs[id]
        scope.launch {
            // 等上一个任务真正退出，避免两个写者同时写同一个文件
            previous?.join()
            intents.remove(id)
            val entity = repository.byId(id) ?: return@launch
            repository.updateProgress(id, DownloadStatus.RUNNING, fileOf(id).length(), -1)
            runDownload(id, entity.url, entity.fileName, null)
            DownloadService.start(context)
        }
    }

    fun cancel(id: Long) {
        intents[id] = INTENT_CANCEL
        calls.remove(id)?.cancel()
        jobs[id]?.cancel()
        samples.remove(id)
        scope.launch {
            repository.updateProgress(id, DownloadStatus.CANCELED, 0, -1)
            fileOf(id).delete()
        }
        _progress.value = _progress.value - id
    }

    fun retry(id: Long) {
        val previous = jobs[id]
        scope.launch {
            previous?.join()
            intents.remove(id)
            val entity = repository.byId(id) ?: return@launch
            fileOf(id).delete()
            repository.updateProgress(id, DownloadStatus.RUNNING, 0, -1)
            runDownload(id, entity.url, entity.fileName, null)
            DownloadService.start(context)
        }
    }

    fun openIntent(id: Long): Intent? {
        val file = fileOf(id)
        if (!file.exists()) return null
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun activeCount(): Int = _progress.value.size

    private fun runDownload(id: Long, url: String, fileName: String, userAgent: String?) {
        calls.remove(id)?.cancel()
        val job = scope.launch {
            val file = fileOf(id)
            var downloaded = if (file.exists()) file.length() else 0L
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply { userAgent?.let { header("User-Agent", it) } }
                    .apply { if (downloaded > 0) header("Range", "bytes=$downloaded-") }
                    .build()

                val call = client.newCall(request)
                calls[id] = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        finish(id, fileName, DownloadStatus.FAILED, downloaded)
                        return@launch
                    }
                    val body = response.body
                    if (body == null) {
                        finish(id, fileName, DownloadStatus.FAILED, downloaded)
                        return@launch
                    }
                    if (downloaded > 0 && response.code != 206) {
                        // 服务器不支持 Range，只能重下
                        downloaded = 0L
                        file.delete()
                    }
                    val total = if (body.contentLength() > 0) downloaded + body.contentLength() else -1L

                    RandomAccessFile(file, "rw").use { output ->
                        output.seek(downloaded)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var lastPush = 0L
                            while (true) {
                                if (intents[id] != null) return@launch // 用户已暂停/取消
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastPush > 200) {
                                    lastPush = now
                                    val previous = samples[id]
                                    samples[id] = downloaded to now
                                    val speed = if (previous != null && now > previous.second) {
                                        ((downloaded - previous.first) * 1000.0 / (now - previous.second)).toLong()
                                    } else 0L
                                    val eta = if (speed > 0 && total > downloaded) (total - downloaded) / speed else -1L
                                    publish(id, fileName, downloaded, total, speed, eta)
                                }
                            }
                        }
                    }
                    finish(id, fileName, DownloadStatus.DONE, downloaded)
                }
            } catch (io: IOException) {
                // call.cancel() 会走到这里：按用户意图落状态，不判为失败
                when (intents[id]) {
                    INTENT_PAUSE -> finish(id, fileName, DownloadStatus.PAUSED, downloaded)
                    INTENT_CANCEL -> finish(id, fileName, DownloadStatus.CANCELED, downloaded)
                    else -> finish(id, fileName, DownloadStatus.FAILED, downloaded)
                }
            } finally {
                intents.remove(id, INTENT_PAUSE)
                intents.remove(id, INTENT_CANCEL)
                calls.remove(id)
                jobs.remove(id)
                samples.remove(id)
                if (activeCount() == 0) DownloadService.stop(context)
            }
        }
        jobs[id] = job
    }

    private suspend fun finish(id: Long, fileName: String, status: Int, bytes: Long) {
        _progress.value = _progress.value - id
        withContext(Dispatchers.IO) {
            repository.updateProgress(id, status, bytes, if (status == DownloadStatus.DONE) bytes else -1)
        }
        if (status == DownloadStatus.DONE) {
            samples.remove(id)
        }
    }

    private fun publish(id: Long, fileName: String, bytes: Long, total: Long, speed: Long, eta: Long) {
        _progress.value = _progress.value + (
            id to DownloadProgress(id, fileName, DownloadStatus.RUNNING, bytes, total, speed, eta)
            )
    }

    private fun fileOf(id: Long): File = File(dir(), "dl_$id.bin")

    private fun safeName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download.bin" }

    private companion object {
        const val INTENT_PAUSE = "pause"
        const val INTENT_CANCEL = "cancel"
    }
}
