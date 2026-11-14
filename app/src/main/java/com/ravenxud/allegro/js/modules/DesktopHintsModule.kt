package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * 桌面模式下的"移动端指纹"抹除。
 *
 * 背景：WebView 只允许改 `User-Agent`，**无法改 Client Hints**（服务端仍收到
 * `Sec-CH-UA-Mobile: ?1`）。像 B 站这种同时看 UA 和 hints 的站点就会：
 * `m.` 认为你是桌面 → 302 到 `www.`；`www.` 按 hints 认为你是手机 → 302 回 `m.`，
 * 形成每几百毫秒一次的死循环，页面"疯狂刷新"。
 *
 * 服务端 hint 我们改不了，但 JS 侧读取的 `navigator.userAgentData` / `platform` /
 * `maxTouchPoints` 可以覆盖，能挡掉一批"前端判断再跳转"的站点。
 * 只对 DOC_START 注入，且只在桌面模式生效，普通浏览完全不受影响。
 */
class DesktopHintsModule : JsModule {

    override val id = "desktop-hints"
    override val injectAt = InjectAt.DOC_START
    override val priority = -10

    override fun js(context: JsContext): String {
        if (!context.desktopMode) return ""
        return """
            (function(){
              if (window.__sbDesktopHints) return;
              window.__sbDesktopHints = true;
              try {
                var meta = { mobile: false, platform: 'macOS', platformVersion: '14.0.0' };
                if (navigator.userAgentData) {
                  Object.defineProperty(navigator, 'userAgentData', {
                    configurable: true,
                    get: function(){
                      return {
                        mobile: false,
                        platform: 'macOS',
                        brands: navigator.userAgentData.brands,
                        getHighEntropyValues: function(){ return Promise.resolve(meta); },
                        toJSON: function(){ return meta; }
                      };
                    }
                  });
                }
                Object.defineProperty(navigator, 'platform', { configurable: true, get: function(){ return 'MacIntel'; } });
                Object.defineProperty(navigator, 'maxTouchPoints', { configurable: true, get: function(){ return 0; } });
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
