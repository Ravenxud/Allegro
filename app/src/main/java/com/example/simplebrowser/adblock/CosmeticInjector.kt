package com.example.simplebrowser.adblock

/**
 * 把元素隐藏规则编译成一段注入脚本。
 *
 * 幂等：页面里已存在 `#__sb_adblock` 样式节点时直接返回。
 */
internal object CosmeticInjector {

    fun script(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""
        val css = selectors.joinToString(separator = ",") + "{display:none !important;}"
        val escaped = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
        return buildString {
            append("(function(){")
            append("if(document.getElementById('__sb_adblock'))return;")
            append("var s=document.createElement('style');")
            append("s.id='__sb_adblock';")
            append("s.textContent='")
            append(escaped)
            append("';")
            append("(document.head||document.documentElement).appendChild(s);")
            append("})();")
        }
    }
}
