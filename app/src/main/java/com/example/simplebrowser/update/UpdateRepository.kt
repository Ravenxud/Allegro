package com.example.simplebrowser.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 检查到的更新信息。 */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/** 更新状态机。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Ready(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * 应用内更新。
 *
 * 数据源：GitHub Releases API（`releases/latest`），仓库地址由设置项提供，
 * 默认指向占位仓库，未配置时直接返回"已是最新"，不会发任何请求。
 */
class UpdateRepository(
    private val context: Context,
    private val repository: String,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        if (repository.isBlank()) {
            _state.value = UpdateState.UpToDate
            return@withContext UpdateState.UpToDate
        }
        _state.value = UpdateState.Checking
        val json = runCatching {
            val connection = (URL("https://api.github.com/repos/$repository/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SimpleBrowser")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()

        if (json == null) {
            _state.value = UpdateState.Failed("无法连接更新服务器")
            return@withContext _state.value
        }

        val info = runCatching {
            val root = JSONObject(json)
            val tag = root.optString("tag_name").removePrefix("v")
            val assets = root.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        url = asset.optString("browser_download_url")
                        size = asset.optLong("size")
                        break
                    }
                }
            }
            UpdateInfo(
                versionName = tag,
                notes = root.optString("body").take(500),
                apkUrl = url,
                sizeBytes = size,
            )
        }.getOrNull()

        val result = when {
            info == null || info.apkUrl.isBlank() -> UpdateState.Failed("更新信息格式异常")
            !isNewer(info.versionName) -> UpdateState.UpToDate
            else -> UpdateState.Available(info)
        }
        _state.value = result
        result
    }

    /** 下载 APK 到私有目录，完成后返回可安装的文件。 */
    suspend fun download(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "update").apply { mkdirs() }
        val target = File(dir, "update-${info.versionName}.apk")
        if (target.exists() && target.length() == info.sizeBytes && info.sizeBytes > 0) return@withContext target

        _state.value = UpdateState.Downloading(0)
        val ok = runCatching {
            val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(percent)
                            }
                        }
                    }
                }
            }
        }.isSuccess

        if (!ok) {
            target.delete()
            _state.value = UpdateState.Failed("下载失败")
            return@withContext null
        }
        _state.value = UpdateState.Ready(target)
        target
    }

    /** 调起系统安装器。 */
    fun install(file: File): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.isSuccess

    /** 简单版本比较：1.2.10 > 1.2.9。 */
    private fun isNewer(candidate: String): Boolean {
        val current = currentVersionName()
        fun parts(v: String) = v.split('.', '-', '+')
            .mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
