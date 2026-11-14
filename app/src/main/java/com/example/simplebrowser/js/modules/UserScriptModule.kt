package com.example.simplebrowser.js.modules

import com.example.simplebrowser.di.ServiceLocator
import com.example.simplebrowser.js.InjectAt
import com.example.simplebrowser.js.JsContext
import com.example.simplebrowser.js.JsModule

/**
 * 用户脚本注入（GM API 子集）。
 *
 * 提供的前置 API：
 * - `GM_setValue / GM_getValue / GM_deleteValue`（按脚本 id 命名空间存在 localStorage）
 * - `GM_addStyle`
 * - `GM_xmlhttpRequest`（基于 fetch 的简化实现，受同源策略限制）
 * - `GM_info.script.name`
 *
 * 不支持的（会明确失效而不是静默）：跨域请求、`@require` 远程依赖、`GM_registerMenuCommand`。
 */
class UserScriptModule : JsModule {

    override val id = "userscript"
    override val injectAt = InjectAt.DOC_END
    override val priority = 90

    override fun js(context: JsContext): String {
        val scripts = runCatching {
            ServiceLocator.userScriptStore.forUrl(context.url, "document-end")
        }.getOrDefault(emptyList())
        if (scripts.isEmpty()) return ""

        return buildString {
            append("(function(){\n")
            append("window.__sbBridgeSecret = window.__sbBridgeSecret || '';\n")
            scripts.forEach { script ->
                append("try{\n")
                append("(function(){\n")
                // GM API 前置
                append(
                    """
                    var SID = '${script.id}';
                    var KEY = 'sb_us_' + SID + '_';
                    var GM_info = { script: { name: ${jsString(script.name)}, namespace: ${jsString(
                        script.namespace
                    )}, version: '1.0' } };
                    var GM = {
                      info: GM_info,
                      setValue: function(k, v){ try { localStorage.setItem(KEY + k, JSON.stringify(v)); } catch(e){} },
                      getValue: function(k, d){ try { var s = localStorage.getItem(KEY + k); return s === null ? d : JSON.parse(s); } catch(e){ return d; } },
                      deleteValue: function(k){ try { localStorage.removeItem(KEY + k); } catch(e){} },
                      addStyle: function(css){ var s=document.createElement('style'); s.textContent=css; (document.head||document.documentElement).appendChild(s); return s; },
                      xmlHttpRequest: function(o){ try { fetch(o.url,{method:o.method||'GET',headers:o.headers,body:o.data}).then(function(r){return r.text();}).then(function(t){ if(o.onload) o.onload({status:200,responseText:t}); }).catch(function(e){ if(o.onerror) o.onerror(e); }); } catch(e){ if(o.onerror) o.onerror(e); } }
                    };
                    var unsafeWindow = window;
                    var GM_setValue = GM.setValue, GM_getValue = GM.getValue,
                        GM_deleteValue = GM.deleteValue, GM_addStyle = GM.addStyle,
                        GM_xmlhttpRequest = GM.xmlHttpRequest;
                    """.trimIndent(),
                )
                append("\n")
                append(script.code)
                append("\n})();\n")
                append("}catch(e){ console.warn('[userscript] ${escape(script.name)}:', e); }\n")
            }
            append("})();")
        }
    }

    private fun jsString(value: String): String = "'" + value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ") + "'"

    private fun escape(value: String): String = value.replace("'", "")
}
