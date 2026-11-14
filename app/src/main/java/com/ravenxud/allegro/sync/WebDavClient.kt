package com.ravenxud.allegro.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 极简 WebDAV 客户端：只用 4 个方法（PROPFIND 探测、MKCOL 建目录、PUT 上传、GET 下载）。
 *
 * 失败一律包成 [Result]，HTTP 状态码信息放进异常消息，方便界面直接显示"401 认证失败"这类提示。
 */
class WebDavClient(
    private val config: WebDavConfig,
    client: OkHttpClient = defaultClient(),
) {

    private val http = client

    /** 探测目录是否存在（也用来验证地址与账号）。 */
    suspend fun check(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = builder(config.directoryUrl())
                .method("PROPFIND", null)
                .header("Depth", "0")
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> Unit
                    401, 403 -> error("认证失败（${response.code}）")
                    404 -> error("目录不存在（404）")
                    else -> error("服务器返回 ${response.code}")
                }
            }
        }
    }

    /** 目录不存在时创建（405/301 视为已存在）。 */
    suspend fun ensureDirectory(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = builder(config.directoryUrl()).method("MKCOL", null).build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299, 405, 301, 409 -> Unit
                    401, 403 -> error("认证失败（${response.code}）")
                    else -> error("创建目录失败（${response.code}）")
                }
            }
        }
    }

    /** 覆盖写入远端文件；目录不存在会先创建。 */
    suspend fun upload(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureDirectory().getOrThrow()
            val request = builder(config.fileUrl()).put(bytes.toRequestBody(JSON)).build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> Unit
                    401, 403 -> error("认证失败（${response.code}）")
                    507 -> error("服务器空间不足（507）")
                    else -> error("上传失败（${response.code}）")
                }
            }
        }
    }

    /** 远端没有文件时返回 null（首次同步）。 */
    suspend fun download(): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = builder(config.fileUrl()).get().build()
            http.newCall(request).execute().use { response ->
                when {
                    response.code in 200..299 -> response.body?.bytes() ?: error("响应为空")
                    response.code == 404 -> null
                    response.code == 401 || response.code == 403 -> error("认证失败（${response.code}）")
                    else -> error("下载失败（${response.code}）")
                }
            }
        }
    }

    private fun builder(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        config.authHeader()?.let { builder.header("Authorization", it) }
        return builder
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
