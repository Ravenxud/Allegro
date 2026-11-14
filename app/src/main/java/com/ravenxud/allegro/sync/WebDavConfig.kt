package com.ravenxud.allegro.sync

import java.net.URLEncoder

/**
 * WebDAV 连接配置。
 *
 * 只支持「基础认证 + 目录 + 单个文件」，够覆盖坚果云/Nextcloud/群晖/自建 nginx dav 的常见用法；
 * 不做分片、不做锁（WebDAV Lock），同步文件只有几十 KB，没必要。
 */
data class WebDavConfig(
    val baseUrl: String,
    val user: String = "",
    val password: String = "",
    val directory: String = "Allegro",
    val fileName: String = "allegro-sync.json",
) {

    val isConfigured: Boolean
        get() = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

    /** 目录 URL（补斜杠），PUT/GET 都基于它。 */
    fun directoryUrl(): String {
        val base = baseUrl.trim().trimEnd('/')
        val dir = directory.trim().trim('/').split('/').filter { it.isNotEmpty() }
            .joinToString("/") { encodeSegment(it) }
        return if (dir.isEmpty()) "$base/" else "$base/$dir/"
    }

    fun fileUrl(): String = directoryUrl() + encodeSegment(fileName)

    /** 需要认证时返回 Basic 头。 */
    fun authHeader(): String? {
        if (user.isEmpty() && password.isEmpty()) return null
        val raw = "$user:$password"
        val encoded = java.util.Base64.getEncoder().encodeToString(raw.toByteArray())
        return "Basic $encoded"
    }

    companion object {
        /**
         * 目录名按段编码：把 `我的 备份` 编成 `%E6%88%91%E7%9A%84%20%E5%A4%87%E4%BB%BD`。
         * URLEncoder 会把空格编成 `+`（那是 form 编码），这里替换回来。
         */
        fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }
}
