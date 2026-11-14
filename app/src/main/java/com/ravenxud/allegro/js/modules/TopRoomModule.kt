package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * 让位页面的固定浮层（学自 Luna 的 TopRoom / BottomRoom）。
 *
 * 很多移动站点自带 `position: fixed` 顶栏/底栏，会与我们的地址栏、工具栏重叠。
 * 做法：找出贴边固定的元素，把它们整体「顶进去」一个安全间距，
 * 而不是靠 CSS hack 或强行隐藏。
 *
 * 仅在顶层文档执行；带幂等标记；对动画使用与原生一致的缓动曲线。
 */
class TopRoomModule : JsModule {

    override val id = "top-room"
    override val injectAt = InjectAt.DOC_END
    override val priority = 40

    override fun js(context: JsContext): String = """
        (function(){
          var FLAG = '__sbRoomInstalled';
          if (window[FLAG]) return;
          window[FLAG] = true;
          var GAP = 0;               // 由原生通过 __sbRoomGap 设置
          var applied = [];
          var style = null;

          function ensureStyle(){
            if (style) return style;
            style = document.createElement('style');
            style.id = '__sb_room_style';
            style.textContent =
              'body.__sb-room-pad{padding-top:var(--sb-room-top,0px)!important;}' +
              'html.__sb-room-pad{scroll-padding-top:var(--sb-room-top,0px)!important;}';
            (document.head || document.documentElement).appendChild(style);
            return style;
          }

          function fixedTopElements(){
            var out = [];
            var all = document.querySelectorAll('header,nav,[class*=header],[class*=topbar],[class*=navbar],[style*=fixed]');
            for (var i = 0; i < all.length && out.length < 6; i++){
              var el = all[i];
              try {
                var st = getComputedStyle(el);
                if (st.position !== 'fixed' && st.position !== 'sticky') continue;
                var r = el.getBoundingClientRect();
                if (r.height <= 0 || r.height > 160) continue;
                if (r.top > 24) continue;
                out.push({el: el, h: Math.round(r.height)});
              } catch (e) {}
            }
            return out;
          }

          window.__sbRoom = function(gap){
            if (typeof gap === 'number') GAP = gap;
            try {
              if (window.top !== window) return;
              var list = fixedTopElements();
              // 站点自带固定头 → 给 body 加与之一致的 padding，避免遮住正文
              if (list.length){
                var h = 0;
                for (var i = 0; i < list.length; i++) h = Math.max(h, list[i].h);
                ensureStyle();
                document.documentElement.style.setProperty('--sb-room-top', (h + GAP) + 'px');
                document.documentElement.classList.add('__sb-room-pad');
              } else {
                document.documentElement.classList.remove('__sb-room-pad');
              }
            } catch (e) {}
          };

          window.__sbRoom(0);
        })();
    """.trimIndent()
}
