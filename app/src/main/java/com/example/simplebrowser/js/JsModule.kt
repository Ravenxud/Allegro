package com.example.simplebrowser.js

/** 注入时机。 */
enum class InjectAt { DOC_START, DOC_END }

/** 注入上下文，决定脚本内容（如桌面模式）。 */
data class JsContext(
    val url: String,
    val desktopMode: Boolean,
)

/**
 * 页面脚本模块。约定：
 * - `js()` 返回自包含 IIFE，不依赖外部变量；
 * - 必须幂等：同一页面重复注入不能重复生效；
 * - 需要跨模块通信时，用 `window.__sb*` 命名空间。
 */
interface JsModule {
    val id: String
    val injectAt: InjectAt
    val priority: Int get() = 0
    fun js(context: JsContext): String
}
