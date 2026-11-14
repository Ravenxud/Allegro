package com.ravenxud.allegro.js

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
            register(com.ravenxud.allegro.js.modules.BridgePreludeModule())
            register(com.ravenxud.allegro.js.modules.DesktopHintsModule())
            register(com.ravenxud.allegro.js.modules.ViewportModule())
            register(com.ravenxud.allegro.js.modules.ScrollStateModule())
            register(com.ravenxud.allegro.js.modules.PageDarkModule())
            register(com.ravenxud.allegro.js.modules.BlobDownloadModule())
            register(com.ravenxud.allegro.js.modules.MediaSnifferModule())
            register(com.ravenxud.allegro.js.modules.TopRoomModule())
            register(com.ravenxud.allegro.js.modules.UserScriptModule())
        }
    }
}
