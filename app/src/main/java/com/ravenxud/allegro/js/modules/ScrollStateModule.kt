package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * 滚动状态上报（v3）。
 *
 * 与 v2 的关键区别：**先确定"页面到底能不能滚、滚的是谁"，再决定是否允许下拉刷新**。
 *
 * - 找到唯一的滚动根：优先 `document.scrollingElement`，否则扫描 `overflow:auto/scroll`
 *   且 `scrollHeight > clientHeight` 的元素，取面积最大的那个；
 * - 找不到滚动根（典型的虚拟滚动 / transform 位移页面，如米哈游官网）
 *   → `allowPull = 0`，该页面**直接禁用下拉刷新**；
 * - 找到滚动根 → `atTop` 取该根的 `scrollTop <= 2`。
 *
 * 这样不再依赖"是否收到过 scroll 事件"，避免"从没滚过但其实是虚拟滚动"的漏判。
 */
class ScrollStateModule : JsModule {

    override val id = "scroll-state"
    override val injectAt = InjectAt.DOC_END
    override val priority = 5

    override fun js(context: JsContext): String = """
        (function(){
          if (window.__sbScrollState) return;
          window.__sbScrollState = true;
          if (window.top !== window) return;

          var scroller = null;
          var allowPull = 0;
          var lastAtTop = null;
          var lastAllow = null;
          var scanned = false;

          function overflowScrollable(el){
            try {
              var st = getComputedStyle(el);
              var oy = st.overflowY;
              return oy === 'auto' || oy === 'scroll' || oy === 'overlay';
            } catch (e) { return false; }
          }

          /** 扫描页面，选出"真正在滚动的那个元素"。 */
          function findScroller(){
            var root = document.scrollingElement || document.documentElement;
            if (root && root.scrollHeight > window.innerHeight + 8) return root;

            var best = null;
            var bestArea = 0;
            var all = document.querySelectorAll('div,main,section,article,ul,body');
            var limit = Math.min(all.length, 300);
            for (var i = 0; i < limit; i++){
              var el = all[i];
              if (el.scrollHeight <= el.clientHeight + 8) continue;
              if (!overflowScrollable(el)) continue;
              var r = el.getBoundingClientRect();
              var area = r.width * r.height;
              if (area > bestArea) { bestArea = area; best = el; }
            }
            return best;
          }

          function refreshScroller(){
            scroller = findScroller();
            allowPull = scroller ? 1 : 0;
            scanned = true;
          }

          function atTop(){
            if (!scroller) return false;
            var y = scroller === document.documentElement || scroller === document.body
              ? (window.scrollY || scroller.scrollTop || 0)
              : (scroller.scrollTop || 0);
            return y <= 2;
          }

          function report(force){
            if (!scanned) refreshScroller();
            var top = allowPull === 1 && atTop() ? 1 : 0;
            if (!force && top === lastAtTop && allowPull === lastAllow) return;
            lastAtTop = top; lastAllow = allowPull;
            try {
              if (window.__sbScrollY) {
                window.__sbScrollY(window.__sbBridgeSecret || '', top, allowPull, 0);
              }
            } catch (e) {}
          }

          document.addEventListener('scroll', function(){ report(false); }, true);
          window.addEventListener('resize', function(){ refreshScroller(); report(true); }, { passive: true });
          // 触摸开始时重新确认一次（应对懒加载后页面结构变化）
          document.addEventListener('touchstart', function(){ refreshScroller(); report(true); }, { passive: true, capture: true });

          function boot(){ refreshScroller(); report(true); }
          if (document.readyState === 'complete') boot();
          else window.addEventListener('load', function(){ boot(); }, { once: true });
          setTimeout(boot, 500);
          setTimeout(function(){ refreshScroller(); report(true); }, 1800);
          setTimeout(function(){ refreshScroller(); report(true); }, 4000);
        })();
    """.trimIndent()
}
