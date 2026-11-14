package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * 网页深色（只改画布，不动内容颜色）。
 *
 * 规则：
 * 1. 网页自己适配了深色（prefers-color-scheme: dark 命中）→ 什么都不做，按网页的来；
 * 2. 网页没有深色适配 → 只把 html/body 背景置黑，并声明 color-scheme: dark，
 *    让浏览器默认文字/表单控件走深色；**不对任何元素做反色或改色**。
 *
 * 暴露 window.__sbDarkPage(enabled) 供原生开关。
 */
class PageDarkModule : JsModule {

    override val id = "page-dark"
    override val injectAt = InjectAt.DOC_END
    override val priority = 10

    override fun js(context: JsContext): String = """
        (function(){
          var STYLE_ID = '__sb_dark_canvas';
          var CLASS = '__sb-dark-canvas';

          function pageSupportsDark(){
            try {
              return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
            } catch (e) { return false; }
          }

          function apply(enabled){
            var root = document.documentElement;
            var style = document.getElementById(STYLE_ID);

            if (!enabled || pageSupportsDark()) {
              if (style) style.remove();
              if (root) root.classList.remove(CLASS);
              return false;
            }

            if (!style) {
              style = document.createElement('style');
              style.id = STYLE_ID;
              style.textContent = [
                'html.' + CLASS + '{color-scheme:dark;background-color:#000 !important}',
                'html.' + CLASS + ' body{background-color:#000 !important}'
              ].join('');
              (document.head || root).appendChild(style);
            }
            if (root) root.classList.add(CLASS);
            return true;
          }

          window.__sbDarkPage = function(enabled){ return apply(!!enabled); };
        })();
    """.trimIndent()
}
