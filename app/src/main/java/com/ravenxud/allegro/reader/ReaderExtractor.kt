package com.ravenxud.allegro.reader

import com.ravenxud.allegro.js.BridgeAuth

/**
 * 阅读模式提取（Readability + 后处理 + 智能拼页）。
 *
 * 正文提取的三层处理：
 * 1. **提取**：Mozilla Readability（Apache-2.0，assets/readability）；
 * 2. **后处理**：等价 Chromium dom_distiller 的三步——
 *    - 链接净化（只保留 http/https，统一 target/rel，其余解包为纯文本）
 *    - 图片分类（宽图标记 full-width、去掉写死的宽高、加 lazy）
 *    - 列表/引用/表格整理（去空段落、去脚本样式、表格可横向滚动）
 * 3. **智能拼页**：识别 `rel=next` / `.next` / “下一页”链接，抓取后续页面
 *    再提取并追加，最多 3 页，结果里带 `pages` 计数。
 *
 * 三层都在页面内完成，只把最终 HTML 通过桥回传，避免多次往返。
 */
object ReaderExtractor {

    /** 单次最多合并的页数（含第一页）。 */
    private const val MAX_PAGES = 3

    /**
     * 注入脚本。注意：不在此处内联 Readability 源码，
     * 由调用方把 [readabilityScript] 放在前面一起注入，避免重复拼接 90KB 字符串。
     */
    fun script(maxPages: Int = MAX_PAGES): String {
        val secret = BridgeAuth.current()
        return """
            (function(){
              var SECRET = '$secret';
              function send(json){
                try {
                  if (window.__sbReader) { window.__sbReader(SECRET, json); return; }
                  if (window.SimpleBrowserBridge) { SimpleBrowserBridge.onReaderResult(SECRET, json); }
                } catch (e) {}
              }
              if (typeof Readability !== 'function') { send(''); return; }

              // ---------- 后处理（等价 Chromium dom_distiller） ----------
              function sanitizeLinks(root){
                var links = root.querySelectorAll('a');
                for (var i = 0; i < links.length; i++){
                  var a = links[i];
                  var href = a.getAttribute('href') || '';
                  var ok = /^https?:\/\//i.test(href) || href.charAt(0) === '#';
                  if (ok) {
                    a.setAttribute('target', '_blank');
                    a.setAttribute('rel', 'noopener noreferrer');
                  } else {
                    var text = document.createTextNode(a.textContent || '');
                    if (a.parentNode) a.parentNode.replaceChild(text, a);
                  }
                }
              }

              function classifyImages(root){
                var imgs = root.querySelectorAll('img');
                for (var i = 0; i < imgs.length; i++){
                  var img = imgs[i];
                  img.removeAttribute('width');
                  img.removeAttribute('height');
                  img.removeAttribute('style');
                  img.setAttribute('loading', 'lazy');
                  var w = parseInt(img.getAttribute('data-width') || '0', 10);
                  if (!w && img.naturalWidth) w = img.naturalWidth;
                  if (w >= 600) img.className = (img.className || '') + ' full-width';
                }
              }

              function tidyStructure(root){
                var junk = root.querySelectorAll('script,style,noscript,form,button,input,textarea,select');
                for (var i = 0; i < junk.length; i++){
                  if (junk[i].parentNode) junk[i].parentNode.removeChild(junk[i]);
                }
                var paras = root.querySelectorAll('p,div');
                for (var j = 0; j < paras.length; j++){
                  var el = paras[j];
                  if (!el.children.length && !(el.textContent || '').trim()) {
                    if (el.parentNode) el.parentNode.removeChild(el);
                  }
                }
                var tables = root.querySelectorAll('table');
                for (var k = 0; k < tables.length; k++){
                  var t = tables[k];
                  t.removeAttribute('width');
                  t.style.width = '100%';
                  t.style.display = 'block';
                  t.style.overflowX = 'auto';
                }
              }

              function postProcess(html){
                var doc = document.implementation.createHTMLDocument('reader');
                doc.body.innerHTML = html;
                sanitizeLinks(doc.body);
                classifyImages(doc.body);
                tidyStructure(doc.body);
                return doc.body.innerHTML;
              }

              // ---------- 智能拼页 ----------
              function findNextLink(doc){
                var selectors = [
                  'a[rel~=next]', 'link[rel~=next]',
                  'a.next', 'a.next-page', '.pagination a.next',
                  '.pager a.next', 'a#next', '.page-nav a.next'
                ];
                for (var i = 0; i < selectors.length; i++){
                  var el = doc.querySelector(selectors[i]);
                  if (el) {
                    var href = el.getAttribute('href') || el.getAttribute('data-href') || '';
                    if (href) return new URL(href, location.href).href;
                  }
                }
                var anchors = doc.querySelectorAll('a');
                for (var j = 0; j < anchors.length; j++){
                  var t = (anchors[j].textContent || '').trim();
                  if (/^(下一页|下页|next|Next|›|»)$/.test(t)) {
                    var h = anchors[j].getAttribute('href') || '';
                    if (h) return new URL(h, location.href).href;
                  }
                }
                return null;
              }

              function extractFrom(doc, titleFallback){
                var clone = doc.cloneNode(true);
                var article = new Readability(clone, { charThreshold: 200 }).parse();
                if (!article || !article.content) return null;
                return {
                  title: article.title || titleFallback || '',
                  byline: article.byline || '',
                  siteName: article.siteName || location.host,
                  content: article.content
                };
              }

              // ---------- 主流程 ----------
              var first = extractFrom(document, document.title);
              if (!first) { send(''); return; }

              var pages = 1;
              var mergedHtml = first.content;
              var nextUrl = findNextLink(document);
              var visited = {};
              visited[location.href] = 1;

              function finish(){
                var payload = {
                  title: first.title,
                  byline: first.byline,
                  siteName: first.siteName,
                  content: postProcess(mergedHtml),
                  pages: pages
                };
                send(JSON.stringify(payload));
              }

              function step(){
                if (!nextUrl || pages >= $maxPages || visited[nextUrl]) { finish(); return; }
                visited[nextUrl] = 1;
                fetch(nextUrl, { credentials: 'include' })
                  .then(function(r){ return r.text(); })
                  .then(function(text){
                    var parsed = new DOMParser().parseFromString(text, 'text/html');
                    var part = extractFrom(parsed, first.title);
                    if (part && part.content) {
                      mergedHtml += '<hr class="page-break">' + part.content;
                      pages += 1;
                      nextUrl = findNextLink(parsed);
                    } else {
                      nextUrl = null;
                    }
                  })
                  .catch(function(){ nextUrl = null; })
                  .then(function(){
                    if (nextUrl && pages < $maxPages) step(); else finish();
                  });
              }

              step();
            })();
        """.trimIndent()
    }

    /** 从 assets 读取 Readability 源码，需在 [script] 之前注入。 */
    fun readabilityScript(): String = runCatching {
        com.ravenxud.allegro.di.ServiceLocator.appContext.assets
            .open("readability/Readability.js").bufferedReader().use { it.readText() }
    }.getOrDefault("")

    /** 把提取结果包成阅读页 HTML（排版与深浅色）。 */
    fun readerHtml(json: String): String {
        val obj = runCatching { org.json.JSONObject(json) }.getOrNull()
        val title = obj?.optString("title").orEmpty().ifBlank { extract(json, "title") }
        val byline = obj?.optString("byline").orEmpty()
        val site = obj?.optString("siteName").orEmpty()
        val pages = obj?.optInt("pages", 1) ?: 1
        val content = obj?.optString("content").orEmpty().ifBlank { extractRaw(json, "content") }
        val mergedNote = if (pages > 1) " · 已合并 $pages 页" else ""
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              :root { color-scheme: light dark; }
              body{
                margin:0 auto;padding:20px 18px 56px;max-width:720px;
                font:17px/1.8 -apple-system,"Noto Sans SC",sans-serif;
                color:#1a1a1a;background:#fff;
              }
              @media (prefers-color-scheme: dark){
                body{color:#e8e8e8;background:#121212}
                a{color:#5ea8ff}
                blockquote{color:#aaa;border-color:#3a6fb0}
                hr.page-break{border-color:#333}
              }
              h1{font-size:24px;line-height:1.4;margin:8px 0 10px}
              .meta{font-size:13px;color:#8a8a8e;margin-bottom:22px}
              img,video,figure{max-width:100%;height:auto;border-radius:6px}
              img.full-width{display:block;margin:16px 0}
              pre{overflow:auto;background:rgba(127,127,127,.12);padding:12px;border-radius:10px}
              code{font-family:ui-monospace,monospace;font-size:14px}
              blockquote{border-left:3px solid #0088ff;margin:16px 0;padding:4px 14px;color:#666}
              a{color:#0088ff;text-decoration:none}
              hr.page-break{border:0;border-top:1px dashed #ccc;margin:28px 0}
              table{border-collapse:collapse}
            </style></head>
            <body>
              <h1>${escape(title)}</h1>
              <div class="meta">${escape(site)}${if (byline.isNotBlank()) " · " + escape(byline) else ""}$mergedNote</div>
              $content
            </body></html>
        """.trimIndent()
    }

    private fun extract(json: String, key: String): String {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = pattern.find(json)?.groupValues?.get(1) ?: return ""
        return raw.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractRaw(json: String, key: String): String {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = pattern.find(json)?.groupValues?.get(1) ?: return ""
        return raw.replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\")
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
