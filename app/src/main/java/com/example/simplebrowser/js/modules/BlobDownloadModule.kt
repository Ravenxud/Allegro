package com.example.simplebrowser.js.modules

import com.example.simplebrowser.js.InjectAt
import com.example.simplebrowser.js.JsContext
import com.example.simplebrowser.js.JsModule

/**
 * blob: 下载拦截。
 *
 * WebView 无法像 Chrome 那样把 blob 交给 DownloadManager，
 * 这里把 <a download> 的 blob 读成 base64，经 JS 桥交给原生落盘。
 * 超过 8MB 的文件放弃（避免 base64 把主线程拖死）。
 */
class BlobDownloadModule : JsModule {

    override val id = "blob-download"
    override val injectAt = InjectAt.DOC_END
    override val priority = 20

    override fun js(context: JsContext): String = """
        (function(){
          if (window.__sbBlobHook) return;
          window.__sbBlobHook = true;
          var MAX = 8 * 1024 * 1024;

          document.addEventListener('click', function(event){
            var a = event.target && event.target.closest ? event.target.closest('a[download]') : null;
            if (!a) return;
            var href = a.getAttribute('href') || '';
            if (href.indexOf('blob:') !== 0) return;

            event.preventDefault();
            event.stopPropagation();

            fetch(href).then(function(r){ return r.blob(); }).then(function(blob){
              if (!blob || blob.size > MAX) {
                if (window.SimpleBrowserBridge) {
                  SimpleBrowserBridge.onBlobTooLarge(window.__sbBridgeSecret || '', a.download || 'download');
                }
                return;
              }
              var reader = new FileReader();
              reader.onloadend = function(){
                var dataUrl = String(reader.result || '');
                var comma = dataUrl.indexOf(',');
                var base64 = comma >= 0 ? dataUrl.substring(comma + 1) : '';
                if (window.SimpleBrowserBridge && base64) {
                  SimpleBrowserBridge.onBlobDownload(
                    window.__sbBridgeSecret || '',
                    a.download || 'download',
                    blob.type || 'application/octet-stream',
                    base64
                  );
                }
              };
              reader.readAsDataURL(blob);
            }).catch(function(){ /* 静默失败，交给页面自身处理 */ });
          }, true);
        })();
    """.trimIndent()
}
