package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * 统一 viewport：普通模式强制 device-width，桌面模式给 1280px。
 * 幂等：已注入过或页面自己声明了 viewport 就不动。
 */
class ViewportModule : JsModule {

    override val id = "viewport"
    override val injectAt = InjectAt.DOC_START
    override val priority = 0

    override fun js(context: JsContext): String {
        val content = if (context.desktopMode) {
            "width=1280"
        } else {
            "width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes"
        }
        return """
            (function(){
              if (window.__sbViewport) return;
              window.__sbViewport = true;
              var apply = function(){
                var head = document.head;
                if (!head) return;
                var meta = document.querySelector('meta[name=viewport]');
                if (!meta) {
                  meta = document.createElement('meta');
                  meta.name = 'viewport';
                  head.appendChild(meta);
                }
                meta.setAttribute('content', '$content');
              };
              if (document.head) apply();
              else document.addEventListener('DOMContentLoaded', apply, { once: true });
            })();
        """.trimIndent()
    }
}
