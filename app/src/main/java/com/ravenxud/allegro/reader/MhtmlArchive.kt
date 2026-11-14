package com.ravenxud.allegro.reader

import java.util.Base64

/**
 * MHTML（`WebView.saveWebArchive` 的产物）→ 自包含 HTML 的转换器。
 *
 * 为什么必须有这一层：Android WebView **不能渲染 MHTML**，把 `file://x.mhtml`
 * 塞给 WebView 只会把整段报文当纯文本显示。所以保存离线页后要把 MHTML 拆成
 * 「主 HTML + 资源内联成 data URI」，落成单个 .html 才能离线打开。
 *
 * 纯 JVM 实现（不依赖 Android），因此可以直接跑单元测试。
 */
object MhtmlArchive {

    /** 转换结果。[inlinedParts] 是被内联进 HTML 的资源个数。 */
    data class Result(val html: String, val partCount: Int, val inlinedParts: Int)

    /** 单个资源超过这个大小就不内联（避免 HTML 过大、内存暴涨）。 */
    private const val MAX_PART_INLINE = 512 * 1024

    /** 所有内联资源的总大小上限。 */
    private const val MAX_TOTAL_INLINE = 8 * 1024 * 1024

    private const val CR = 13
    private const val LF = 10
    private val CRLF = "\r\n"
    private val CRLF_CRLF = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)

    private class Part(
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        val contentType: String get() = headers["content-type"].orEmpty()
        val mime: String get() = contentType.substringBefore(';').trim().lowercase()
        val charset: String
            get() = Regex("charset\\s*=\\s*\"?([^\";\\s]+)", RegexOption.IGNORE_CASE)
                .find(contentType)?.groupValues?.get(1) ?: "utf-8"
        val location: String? get() = headers["content-location"]?.trim()?.takeIf { it.isNotEmpty() }
        val contentId: String?
            get() = headers["content-id"]?.trim()
                ?.removeSurrounding("<", ">")
                ?.takeIf { it.isNotEmpty() }
        val isHtml: Boolean get() = mime == "text/html" || mime == "application/xhtml+xml"
    }

    /**
     * 把 MHTML 字节转换成自包含 HTML；失败返回 null。
     *
     * @param maxInlineBytes 覆盖默认的内联总上限（大页面保存时可放宽）。
     */
    fun toHtml(source: ByteArray, maxInlineBytes: Int = MAX_TOTAL_INLINE): Result? {
        if (source.isEmpty()) return null
        if (startsWithPartDelimiter(source)) return toHtmlFromPartsOnly(source)
        val headerEnd = findHeaderEnd(source, 0)
            ?: return toHtmlFromPartsOnly(source)
        val rootHeaders = parseHeaders(source, 0, headerEnd.first)
        val boundary = resolveBoundary(rootHeaders["content-type"].orEmpty(), source)
            ?: return toHtmlFromPartsOnly(source)

        val parts = splitParts(source, headerEnd.second, boundary)
        if (parts.isEmpty()) return null
        val main = parts.firstOrNull { it.isHtml }
            ?: parts.filter { looksLikeHtml(it) }.maxByOrNull { it.body.size }
            ?: return null

        val byKey = HashMap<String, Part>()
        parts.forEach { part ->
            part.location?.let { byKey[normalize(it)] = part }
            part.contentId?.let { byKey["cid:$it"] = part }
        }

        var budget = maxInlineBytes
        var inlined = 0
        val uriCache = HashMap<Part, String?>()
        val visiting = HashSet<Part>()

        fun uriOf(part: Part): String? {
            if (part === main) return null
            if (uriCache.containsKey(part)) return uriCache[part]
            if (!visiting.add(part)) return null
            val body = run {
                // CSS 里的 url(...) 也要一起解析，否则内联了 CSS 也是断图
                val isCss = part.mime == "text/css"
                if (!isCss) return@run part.body
                val base = part.location.orEmpty()
                val css = rewriteCss(decode(part), base) { lookup ->
                    resolvePart(byKey, lookup)?.let { uriOf(it) }
                }
                css.toByteArray(charsetOf(part))
            }
            visiting.remove(part)
            val uri = if (body.isEmpty() || body.size > MAX_PART_INLINE || body.size > budget) {
                null
            } else {
                budget -= body.size
                inlined++
                "data:${part.mime.ifEmpty { "application/octet-stream" }};base64," +
                    Base64.getEncoder().encodeToString(body)
            }
            uriCache[part] = uri
            return uri
        }

        val baseUrl = main.location ?: ""
        val html = rewriteHtml(decode(main), baseUrl) { ref ->
            resolvePart(byKey, resolve(baseUrl, ref))?.let { uriOf(it) }
        }
        return Result(html = html, partCount = parts.size, inlinedParts = inlined)
    }

    /** 在已解析的报文里按 URL / cid 找资源。 */
    private fun resolvePart(byKey: Map<String, Part>, url: String): Part? {
        if (url.isEmpty()) return null
        return byKey[normalize(url)] ?: byKey[url] ?: byKey[url.removePrefix("#")]
    }

    /** 去掉 fragment、统一小写 scheme/host 之外的差异，便于把相对引用和 Content-Location 对上。 */
    private fun normalize(url: String): String {
        val cut = url.indexOf('#')
        return if (cut >= 0) url.substring(0, cut) else url
    }

    /** 相对引用 → 绝对引用（解析失败时原样返回）。 */
    private fun resolve(base: String, ref: String): String {
        if (ref.isEmpty()) return ref
        val cleaned = ref.trim()
        if (cleaned.startsWith("data:") || cleaned.startsWith("javascript:") ||
            cleaned.startsWith("#") || cleaned.startsWith("mailto:")
        ) {
            return ""
        }
        runCatching { java.net.URI(cleaned) }.getOrNull()?.let { if (it.isAbsolute) return cleaned }
        if (base.isEmpty()) return cleaned
        return runCatching { java.net.URI(base).resolve(cleaned).toString() }.getOrDefault(cleaned)
    }

    private fun charsetOf(part: Part): java.nio.charset.Charset =
        runCatching { java.nio.charset.Charset.forName(part.charset) }.getOrNull() ?: Charsets.UTF_8

    private fun decode(part: Part): String = String(part.body, charsetOf(part)).removePrefix("\uFEFF")

    /**
     * 把 HTML 里对本地资源的引用（src/href/poster）换成 data URI。
     *
     * 真实 MHTML 的 HTML 大多写的是相对路径（`src="/img/a.png"`），
     * 只用 Content-Location 做整串替换是匹配不上的。
     */
    internal fun rewriteHtml(html: String, baseUrl: String, lookup: (String) -> String?): String {
        val baseAttr = Regex(
            "<base\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)
        val base = if (baseAttr.isNullOrBlank()) baseUrl else resolve(baseUrl, baseAttr)

        val attrRegex = Regex(
            "(src|href|poster|data-src|data-original)\\s*=\\s*([\"'])([^\"']*)\\2",
            RegexOption.IGNORE_CASE,
        )
        return attrRegex.replace(html) { match ->
            val name = match.groupValues[1]
            val quote = match.groupValues[2]
            val raw = match.groupValues[3]
            val uri = runCatching { lookup(resolve(base, raw)) }.getOrNull()
            if (uri.isNullOrEmpty()) match.value else "$name=$quote$uri$quote"
        }
    }

    /** 把 CSS 里的 `url(...)` 换成 data URI。 */
    internal fun rewriteCss(css: String, baseUrl: String, lookup: (String) -> String?): String {
        val urlRegex = Regex("url\\(\\s*([\"']?)([^\"')]+)\\1\\s*\\)", RegexOption.IGNORE_CASE)
        return urlRegex.replace(css) { match ->
            val raw = match.groupValues[2].trim()
            val uri = runCatching { lookup(resolve(baseUrl, raw)) }.getOrNull()
            if (uri.isNullOrEmpty()) match.value else "url(${match.groupValues[1]}$uri${match.groupValues[1]})"
        }
    }

    /** 文件是不是直接以 `--boundary` 开头（有些实现不写根 MIME 头）。 */
    private fun startsWithPartDelimiter(source: ByteArray): Boolean {
        val head = String(source, 0, minOf(source.size, 256), Charsets.ISO_8859_1)
        val first = head.lineSequence().firstOrNull()?.trim().orEmpty()
        return first.startsWith("--") && first.length > 3
    }

    /** 返回 (头部结束位置, 正文开始位置)；支持 CRLF 与 LF 两种换行。 */
    private fun findHeaderEnd(source: ByteArray, from: Int): Pair<Int, Int>? {
        val crlf = indexOf(source, CRLF_CRLF, from)
        val lflf = indexOf(source, "\n\n".toByteArray(Charsets.ISO_8859_1), from)
        val first = when {
            crlf < 0 -> lflf
            lflf < 0 -> crlf
            else -> minOf(crlf, lflf)
        }
        if (first < 0) return null
        val bodyStart = if (first == crlf) first + CRLF_CRLF.size else first + 2
        return first to bodyStart
    }

    /** 头里没有 boundary 时，扫描前 8KB 找 `--xxx` 形式的分隔线。 */
    private fun resolveBoundary(contentType: String, source: ByteArray): String? {
        val declared = Regex("boundary\\s*=\\s*\"?([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)?.trim()
        if (!declared.isNullOrEmpty()) return declared
        val head = String(source, 0, minOf(source.size, 8192), Charsets.ISO_8859_1)
        return head.lineSequence()
            .firstOrNull { it.trimStart().startsWith("--") && it.trim().length > 4 }
            ?.trim()
            ?.removePrefix("--")
            ?.removeSuffix("--")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun looksLikeHtml(part: Part): Boolean {
        if (part.mime.startsWith("text/")) return false
        val text = String(part.body, 0, minOf(part.body.size, 512), Charsets.ISO_8859_1)
        return text.contains("<html", true) || text.contains("<!DOCTYPE", true)
    }

    /** 没有任何 MIME 头时的兜底：把第一段之前的内容当分隔线找出来继续解析。 */
    private fun toHtmlFromPartsOnly(source: ByteArray): Result? {
        val text = String(source, 0, minOf(source.size, 8192), Charsets.ISO_8859_1)
        val boundary = text.lineSequence()
            .firstOrNull { it.trimStart().startsWith("--") && it.trim().length > 4 }
            ?.trim()?.removePrefix("--")?.removeSuffix("--")?.trim()
            ?: return null
        val parts = splitParts(source, 0, boundary)
        if (parts.isEmpty()) return null
        val main = parts.firstOrNull { it.isHtml }
            ?: parts.filter { looksLikeHtml(it) }.maxByOrNull { it.body.size }
            ?: return null
        return Result(html = decode(main), partCount = parts.size, inlinedParts = 0)
    }

    // ---------------------------------------------------------------- 报文解析

    private fun splitParts(source: ByteArray, start: Int, boundary: String): List<Part> {
        val delimiter = "--$boundary".toByteArray(Charsets.ISO_8859_1)
        val positions = ArrayList<Int>(32)
        var cursor = start
        while (true) {
            val at = indexOf(source, delimiter, cursor)
            if (at < 0) break
            positions += at
            cursor = at + delimiter.size
        }
        if (positions.isEmpty()) return emptyList()

        val parts = ArrayList<Part>(positions.size)
        positions.forEachIndexed { index, at ->
            val bodyStart = at + delimiter.size
            // 结束标记 `--boundary--`
            if (bodyStart + 1 < source.size &&
                source[bodyStart] == '-'.code.toByte() &&
                source[bodyStart + 1] == '-'.code.toByte()
            ) {
                return@forEachIndexed
            }
            val from = skipLineBreak(source, bodyStart)
            val to = if (index + 1 < positions.size) positions[index + 1] else source.size
            val end = trimTrailingLineBreak(source, from, to)
            if (end <= from) return@forEachIndexed
            parts += parsePart(source, from, end)
        }
        return parts
    }

    private fun parsePart(source: ByteArray, from: Int, to: Int): Part {
        val split = findHeaderEnd(source, from)
        val headers: Map<String, String>
        val bodyFrom: Int
        if (split == null || split.first >= to || split.second > to) {
            headers = emptyMap()
            bodyFrom = from
        } else {
            headers = parseHeaders(source, from, split.first)
            bodyFrom = split.second
        }
        val raw = source.copyOfRange(bodyFrom.coerceAtMost(to), to)
        val encoding = headers["content-transfer-encoding"]?.trim()?.lowercase().orEmpty()
        val body = when {
            encoding.startsWith("base64") -> runCatching {
                Base64.getMimeDecoder().decode(raw)
            }.getOrDefault(raw)
            encoding.startsWith("quoted-printable") -> decodeQuotedPrintable(raw)
            else -> raw
        }
        return Part(headers, body)
    }

    private fun parseHeaders(source: ByteArray, from: Int, to: Int): Map<String, String> {
        val text = String(source, from, (to - from).coerceAtLeast(0), Charsets.ISO_8859_1)
        val map = LinkedHashMap<String, String>()
        var lastKey: String? = null
        text.split(CRLF).forEach { line ->
            when {
                line.isBlank() -> Unit
                line.first().isWhitespace() && lastKey != null ->
                    map[lastKey] = (map[lastKey].orEmpty() + " " + line.trim()).trim()
                else -> {
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase()
                        map[key] = line.substring(colon + 1).trim()
                        lastKey = key
                    }
                }
            }
        }
        return map
    }

    /** quoted-printable：`=XX` 还原字节，`=\r\n` 软换行丢弃。 */
    internal fun decodeQuotedPrintable(input: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(input.size)
        var i = 0
        while (i < input.size) {
            val b = input[i]
            if (b == '='.code.toByte()) {
                if (i + 1 < input.size && input[i + 1].toInt() == CR &&
                    i + 2 < input.size && input[i + 2].toInt() == LF
                ) {
                    i += 3
                    continue
                }
                if (i + 1 < input.size && input[i + 1].toInt() == LF) {
                    i += 2
                    continue
                }
                if (i + 2 < input.size) {
                    val high = hexValue(input[i + 1])
                    val low = hexValue(input[i + 2])
                    if (high >= 0 && low >= 0) {
                        out.write(high * 16 + low)
                        i += 3
                        continue
                    }
                }
                out.write(b.toInt())
                i++
            } else {
                out.write(b.toInt())
                i++
            }
        }
        return out.toByteArray()
    }

    private fun hexValue(byte: Byte): Int = when (val c = byte.toInt().toChar()) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private fun skipLineBreak(source: ByteArray, at: Int): Int {
        var i = at
        while (i < source.size && (source[i].toInt() == CR || source[i].toInt() == LF)) i++
        return i
    }

    private fun trimTrailingLineBreak(source: ByteArray, from: Int, to: Int): Int {
        var end = to
        while (end > from && (source[end - 1].toInt() == CR || source[end - 1].toInt() == LF)) end--
        return end
    }

    private fun indexOf(source: ByteArray, needle: ByteArray, start: Int): Int {
        if (needle.isEmpty() || source.size < needle.size) return -1
        val last = source.size - needle.size
        var i = start.coerceAtLeast(0)
        while (i <= last) {
            if (source[i] == needle[0]) {
                var j = 1
                while (j < needle.size && source[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }
}
