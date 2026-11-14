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
import org.json.JSONArray
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
 * 数据源：GitHub Releases API（`/releases` 列表）。
 *
 * 为什么不用 `/releases/latest`：GitHub 的 latest 会**跳过 prerelease**，
 * 而本项目的版本号长期是 `0.x.y-beta`；早期实现因此从来没检查到过更新。
 * 现在拉列表后按语义化版本自己挑（见 [UpdateSelector]）。
 *
 * 仓库地址由设置项提供，未配置时直接返回"已是最新"，不会发任何请求。
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

        // 读列表而不是 /releases/latest：后者会跳过 prerelease，beta 就永远收不到更新
        val json = runCatching {
            val connection = (URL("https://api.github.com/repos/$repository/releases?per_page=30")
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

        val candidates = runCatching { parseCandidates(json) }.getOrNull()
        if (candidates == null) {
            _state.value = UpdateState.Failed("更新信息格式异常")
            return@withContext _state.value
        }

        val current = currentVersionName()
        val picked = pickLatestRelease(candidates)?.takeIf { isNewerVersion(it.tag, current) }
        val apk = picked?.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

        val result = if (picked == null || apk == null) {
            UpdateState.UpToDate
        } else {
            UpdateState.Available(
                UpdateInfo(
                    versionName = picked.tag.removePrefix("v"),
                    notes = picked.notes.take(500),
                    apkUrl = apk.url,
                    sizeBytes = apk.sizeBytes,
                ),
            )
        }
        _state.value = result
        result
    }

    /** GitHub release 数组 → 候选列表（纯解析，选版本交给 [pickLatestRelease]）。 */
    private fun parseCandidates(json: String): List<ReleaseCandidate> {
        val array = JSONArray(json)
        val list = ArrayList<ReleaseCandidate>(array.length())
        for (i in 0 until array.length()) {
            val root = array.optJSONObject(i) ?: continue
            val assetArray = root.optJSONArray("assets")
            val assets = ArrayList<ReleaseAsset>(assetArray?.length() ?: 0)
            if (assetArray != null) {
                for (j in 0 until assetArray.length()) {
                    val asset = assetArray.optJSONObject(j) ?: continue
                    assets += ReleaseAsset(
                        name = asset.optString("name"),
                        url = asset.optString("browser_download_url"),
                        sizeBytes = asset.optLong("size"),
                    )
                }
            }
            list += ReleaseCandidate(
                tag = root.optString("tag_name"),
                title = root.optString("name"),
                notes = root.optString("body"),
                prerelease = root.optBoolean("prerelease", false),
                draft = root.optBoolean("draft", false),
                assets = assets,
            )
        }
        return list
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

}
