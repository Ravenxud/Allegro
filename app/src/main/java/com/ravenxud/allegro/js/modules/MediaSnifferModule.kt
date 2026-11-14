package com.ravenxud.allegro.js.modules

import com.ravenxud.allegro.js.InjectAt
import com.ravenxud.allegro.js.JsContext
import com.ravenxud.allegro.js.JsModule

/**
 * 媒体地址嗅探：捕获页面里的 mp4/m3u8 等直链。
 *
 * 关键差异：不只看 `<video src>`，而是扫页面里的**播放器状态对象**
 * （B 站 `__playinfo__`、Next.js `__NEXT_DATA__`、通用 `playerConfig` 等），
 * 从 JSON 里捞 m3u8/mp4 地址 —— 命中率远高于纯 DOM 抓取。
 *
 * 结果通过窗桥回传：`__sbMediaFound(secret, json)`。
 */
class MediaSnifferModule : JsModule {

    override val id = "media-sniffer"
    override val injectAt = InjectAt.DOC_END
    override val priority = 30

    override fun js(context: JsContext): String = """
        (function(){
          if (window.__sbMediaSniffer) return;
          window.__sbMediaSniffer = true;
          if (window.top !== window) return;

          var STATE_KEYS = ['__playinfo__','__playInfo__','__PLAYER_CONFIG__','playerConfig',
                            'videoInfo','playInfo','__NEXT_DATA__','__INITIAL_STATE__'];
          var URL_KEYS = ['baseUrl','base_url','baseURL','playUrl','play_url','url','src','backupUrl'];
          var seen = {};

          function isMedia(u){
            if (typeof u !== 'string' || u.length < 12) return false;
            return /\.(m3u8|mp4|m4v|webm|mpd|flv)(\?|$)/i.test(u);
          }

          function walk(node, depth, out){
            if (!node || depth > 6 || out.length > 12) return;
            if (typeof node === 'string'){ if (isMedia(node)) out.push(node); return; }
            if (typeof node !== 'object') return;
            if (Array.isArray(node)){ for (var i=0;i<node.length && i<40;i++) walk(node[i], depth+1, out); return; }
            for (var k in node){
              if (!Object.prototype.hasOwnProperty.call(node, k)) continue;
              if (URL_KEYS.indexOf(k) >= 0 || typeof node[k] === 'object') walk(node[k], depth+1, out);
            }
          }

          function scan(){
            var found = [];
            for (var i=0;i<STATE_KEYS.length;i++){
              try { walk(window[STATE_KEYS[i]], 0, found); } catch (e) {}
            }
            var vids = document.querySelectorAll('video[src],video source[src]');
            for (var j=0;j<vids.length;j++){
              var s = vids[j].getAttribute('src');
              if (isMedia(s)) found.push(s);
            }
            var uniq = [];
            for (var n=0;n<found.length;n++){
              if (!seen[found[n]]){ seen[found[n]] = 1; uniq.push(found[n]); }
            }
            if (uniq.length && window.__sbMediaFound){
              try { window.__sbMediaFound(window.__sbBridgeSecret || '', JSON.stringify(uniq.slice(0,8))); } catch (e) {}
            }
          }

          window.__sbMediaScan = scan;
          setTimeout(scan, 1200);
          setTimeout(scan, 4000);
        })();
    """.trimIndent()
}
