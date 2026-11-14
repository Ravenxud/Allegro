package com.example.simplebrowser.js

/** 模块注册表：按 priority 升序返回，保证依赖顺序可控。 */
class JsRegistry {

    private val modules = mutableListOf<JsModule>()

    fun register(module: JsModule): JsRegistry {
        modules.removeAll { it.id == module.id }
        modules += module
        return this
    }

    fun modulesFor(at: InjectAt): List<JsModule> =
        modules.filter { it.injectAt == at }.sortedBy { it.priority }

    fun all(): List<JsModule> = modules.toList()

    companion object {
        /** 内置模块；后续新增脚本只需在这里 register 一行。 */
        fun default(): JsRegistry = JsRegistry().apply {
            register(com.example.simplebrowser.js.modules.ViewportModule())
            register(com.example.simplebrowser.js.modules.ScrollStateModule())
            register(com.example.simplebrowser.js.modules.PageDarkModule())
            register(com.example.simplebrowser.js.modules.BlobDownloadModule())
            register(com.example.simplebrowser.js.modules.MediaSnifferModule())
            register(com.example.simplebrowser.js.modules.TopRoomModule())
        }
    }
}
