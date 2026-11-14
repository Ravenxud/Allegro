package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * JS 桥接前置（学过 Luna 的 UserscriptPrelude）。
 *
 * 注入脚本里习惯写 `window.__sbReader(...)` 这类业务语义函数，
 * 但真正暴露给页面的只有 `SimpleBrowserBridge`（@JavascriptInterface）。
 * 这里统一做一次映射，避免每个模块各写一份、也避免"调用了不存在的函数"这种静默失败。
 */
class BridgePreludeModule : JsModule {

    override val id = "bridge-prelude"
    override val injectAt = InjectAt.DOC_START
    override val priority = 1

    override fun js(context: JsContext): String = """
        (function(){
          if (window.__sbPrelude) return;
          window.__sbPrelude = true;
          var B = window.SimpleBrowserBridge;
          if (!B) return;

          function forward(name, argCount){
            return function(){
              try {
                var args = Array.prototype.slice.call(arguments, 0, argCount);
                B[name].apply(B, args);
              } catch (e) {}
            };
          }

          if (!window.__sbReader) window.__sbReader = forward('onReaderResult', 2);
          if (!window.__sbPageText) window.__sbPageText = forward('onPageText', 2);
          if (!window.__sbScrollY) window.__sbScrollY = forward('onScrollY', 4);
          if (!window.__sbMediaFound) window.__sbMediaFound = forward('onMediaFound', 2);
          if (!window.__sbBlobDownload) window.__sbBlobDownload = forward('onBlobDownload', 4);
          if (!window.__sbBlobTooLarge) window.__sbBlobTooLarge = forward('onBlobTooLarge', 2);
        })();
    """.trimIndent()
}
