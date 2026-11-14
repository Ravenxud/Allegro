package com.example.simplebrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MhtmlArchiveTest {

    /** 造一份形如 saveWebArchive 产物的 MHTML。 */
    private fun mhtml(
        boundary: String = "----MultipartBoundary--abc123----",
        html: String,
        resources: List<Triple<String, String, String>> = emptyList(),
    ): ByteArray {
        val sb = StringBuilder()
        sb.append("From: <Saved by WebView>\r\n")
        sb.append("Subject: 测试页\r\n")
        sb.append("MIME-Version: 1.0\r\n")
        sb.append("Content-Type: multipart/related; type=\"text/html\"; boundary=\"$boundary\"\r\n")
        sb.append("\r\n")
        sb.append("--$boundary\r\n")
        sb.append("Content-Type: text/html\r\n")
        sb.append("Content-Transfer-Encoding: quoted-printable\r\n")
        sb.append("Content-Location: https://example.com/page\r\n")
        sb.append("\r\n")
        sb.append(html)
        sb.append("\r\n")
        resources.forEach { (location, mime, body) ->
            sb.append("--$boundary\r\n")
            sb.append("Content-Type: $mime\r\n")
            sb.append("Content-Transfer-Encoding: base64\r\n")
            sb.append("Content-Location: $location\r\n")
            sb.append("\r\n")
            sb.append(base64Lines(body))
            sb.append("\r\n")
        }
        sb.append("--$boundary--\r\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun base64Lines(text: String): String =
        Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(text.toByteArray())

    @Test
    fun `extracts html part`() {
        val result = MhtmlArchive.toHtml(mhtml(html = "<p>hello</p>"))
        assertNotNull(result)
        assertEquals("<p>hello</p>", result!!.html.trim())
        assertEquals(1, result.partCount)
        assertEquals(0, result.inlinedParts)
    }

    @Test
    fun `inlines base64 image as data uri`() {
        val png = "PNG-DATA"
        val bytes = mhtml(
            html = "<img src=\"https://example.com/a.png\">",
            resources = listOf(Triple("https://example.com/a.png", "image/png", png)),
        )
        val result = MhtmlArchive.toHtml(bytes)!!
        assertTrue(result.html.contains("data:image/png;base64,"))
        assertTrue(!result.html.contains("https://example.com/a.png\""))
        assertEquals(1, result.inlinedParts)
    }

    @Test
    fun `decodes quoted printable and soft line breaks`() {
        // 中文字符的 UTF-8 字节被 =XX 转义，且中间有软换行
        val html = "<p>=E4=BD=A0=\r\n=E5=A5=BD</p>"
        val result = MhtmlArchive.toHtml(mhtml(html = html))!!
        assertEquals("<p>你好</p>", result.html.trim())
    }

    @Test
    fun `replaces cid reference`() {
        val boundary = "b1"
        val raw = buildString {
            append("Content-Type: multipart/related; boundary=\"$boundary\"\r\n\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("\r\n")
            append("<img src=\"cid:logo@x\">\r\n")
            append("--$boundary\r\n")
            append("Content-Type: image/gif\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("Content-ID: <logo@x>\r\n")
            append("\r\n")
            append("R0lGODlhAQABAAAAACw=\r\n")
            append("--$boundary--\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
        val result = MhtmlArchive.toHtml(raw)!!
        assertTrue(result.html.contains("data:image/gif;base64,"))
        assertTrue(!result.html.contains("cid:logo@x"))
    }

    @Test
    fun `resolves relative references against page location`() {
        val bytes = mhtml(
            html = "<img src=\"/img/a.png\"><img src=\"img/b.png\"><a href=\"https://other.test/x\">x</a>",
            resources = listOf(
                Triple("https://example.com/img/a.png", "image/png", "A"),
                Triple("https://example.com/img/b.png", "image/png", "B"),
            ),
        )
        val result = MhtmlArchive.toHtml(bytes)!!
        assertEquals(2, Regex("data:image/png").findAll(result.html).count())
        // 站外链接保持不变
        assertTrue(result.html.contains("https://other.test/x"))
    }

    @Test
    fun `inlines stylesheet and rewrites its relative urls`() {
        val boundary = "b2"
        val raw = buildString {
            append("Content-Type: multipart/related; boundary=\"$boundary\"\r\n\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/html\r\n")
            append("Content-Location: https://example.com/post/1\r\n")
            append("\r\n")
            append("<link rel=\"stylesheet\" href=\"/s.css\"><p>x</p>\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/css\r\n")
            append("Content-Location: https://example.com/s.css\r\n")
            append("\r\n")
            append(".a{background:url(bg.png)}\r\n")
            append("--$boundary\r\n")
            append("Content-Type: image/png\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("Content-Location: https://example.com/bg.png\r\n")
            append("\r\n")
            append(base64Lines("BG"))
            append("\r\n")
            append("--$boundary--\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
        val result = MhtmlArchive.toHtml(raw)!!
        assertTrue(result.html.contains("data:text/css;base64,"))
        assertTrue(!result.html.contains("/s.css\""))
        // CSS 里的相对图片也被内联进 CSS 数据
        val cssPart = result.html.substringAfter("data:text/css;base64,").substringBefore('"')
        val css = String(Base64.getDecoder().decode(cssPart))
        assertTrue(css.contains("url(data:image/png;base64,"))
        assertEquals(2, result.inlinedParts)
    }

    @Test
    fun `accepts LF only line endings`() {
        val raw = buildString {
            append("MIME-Version: 1.0\n")
            append("Content-Type: multipart/related; boundary=\"bLF\"\n")
            append("\n")
            append("--bLF\n")
            append("Content-Type: text/html\n")
            append("\n")
            append("<p>lf</p>\n")
            append("--bLF--\n")
        }
        val result = MhtmlArchive.toHtml(raw.toByteArray())
        assertNotNull(result)
        assertEquals("<p>lf</p>", result!!.html.trim())
    }

    @Test
    fun `treats html part without content type as html`() {
        val raw = buildString {
            append("Content-Type: multipart/related; boundary=\"bNC\"\r\n\r\n")
            append("--bNC\r\n")
            append("Content-Location: https://example.com/\r\n")
            append("\r\n")
            append("<!DOCTYPE html><html><body><p>no-ct</p></body></html>\r\n")
            append("--bNC--\r\n")
        }
        val result = MhtmlArchive.toHtml(raw.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.html.contains("no-ct"))
    }

    @Test
    fun `parses archive without root headers`() {
        val raw = buildString {
            append("--bRoot\r\n")
            append("Content-Type: text/html\r\n")
            append("\r\n")
            append("<p>rootless</p>\r\n")
            append("--bRoot--\r\n")
        }
        val result = MhtmlArchive.toHtml(raw.toByteArray())
        assertNotNull(result)
        assertEquals("<p>rootless</p>", result!!.html.trim())
    }

    /**
     * 真实 Chromium 快照（用 CDP Page.captureSnapshot 抓的，与 WebView.saveWebArchive 同一套生成器）。
     * 这个用例是为了防"合成报文都对、真机报错"的老问题。
     */
    @Test
    fun `parses real chromium snapshot`() {
        val bytes = javaClass.getResourceAsStream("/mhtml/chromium_sample.mhtml")!!.readBytes()
        val result = MhtmlArchive.toHtml(bytes) ?: error("真实报文解析失败")
        assertTrue("中文没解出来", result.html.contains("离线页测试"))
        assertTrue("中文段落缺失", result.html.contains("这是一段用来验证离线保存的文字"))
        assertTrue("图片没内联", result.html.contains("data:image/png;base64,"))
        assertTrue("cid 引用没替换", !result.html.contains("cid:"))
        assertTrue("外链 css 没内联", result.html.contains("data:text/css"))
        assertTrue("还残留本地文件路径", !result.html.contains("file:///tmp/web/"))
    }

    @Test
    fun `returns null for non mhtml input`() {
        assertNull(MhtmlArchive.toHtml("not an mhtml".toByteArray()))
        assertNull(MhtmlArchive.toHtml(ByteArray(0)))
    }

    @Test
    fun `quoted printable hex decoding is byte exact`() {
        val decoded = MhtmlArchive.decodeQuotedPrintable("a=3Db=20c".toByteArray())
        assertEquals("a=b c", String(decoded))
    }
}
