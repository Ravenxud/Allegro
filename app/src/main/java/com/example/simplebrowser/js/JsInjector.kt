package com.example.simplebrowser.js

import android.webkit.WebView

/** 把同一时机的模块拼成一段脚本，一次性 evaluateJavascript。 */
class JsInjector(private val registry: JsRegistry) {

    fun scriptFor(at: InjectAt, context: JsContext): String {
        val secret = BridgeAuth.current()
        val prefix = "window.__sbBridgeSecret = '$secret';\n"
        return prefix + registry.modulesFor(at).joinToString(separator = "\n") { module ->
            "/* ${module.id} */\n" + module.js(context)
        }
    }

    fun inject(view: WebView, at: InjectAt, context: JsContext) {
        val script = scriptFor(at, context)
        if (script.isBlank()) return
        view.evaluateJavascript(script, null)
    }

    fun injectSingle(view: WebView, moduleId: String, context: JsContext) {
        val module = registry.all().firstOrNull { it.id == moduleId } ?: return
        view.evaluateJavascript(module.js(context), null)
    }
}
