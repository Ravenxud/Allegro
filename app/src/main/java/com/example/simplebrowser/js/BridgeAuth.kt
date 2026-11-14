package com.example.simplebrowser.js

import java.security.SecureRandom

/**
 * JS 桥接令牌（学自 Luna 的 `__lunaBridgeSecret`）。
 *
 * 页面脚本能调用 `@JavascriptInterface` 暴露的所有方法，
 * 但只要每次页面加载下发一个**随机令牌**、原生侧校验，
 * 就能挡住"恶意页面直接调原生"的路径（令牌只存在于注入脚本作用域内，
 * 且不写进 DOM、不挂在可枚举的全局对象上）。
 */
object BridgeAuth {

    private val random = SecureRandom()

    @Volatile
    private var secret: String = newSecret()

    private fun newSecret(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 每次主文档开始加载时轮换，旧令牌立即失效。 */
    fun rotate(): String {
        secret = newSecret()
        return secret
    }

    fun current(): String = secret

    fun isValid(candidate: String?): Boolean =
        candidate != null && candidate.isNotEmpty() && candidate == secret
}
