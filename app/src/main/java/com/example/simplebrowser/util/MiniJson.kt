package com.example.simplebrowser.util

/**
 * 极简 JSON 读写（约 100 行，零依赖）。
 *
 * 为什么不用 org.json：它是 Android SDK 的一部分，在 JVM 单测里只有会抛
 * `RuntimeException("Stub!")` 的桩实现，导致同步包的编解码完全无法测试；
 * 而同步是最需要"往返一致"保证的地方。
 *
 * 支持：object / array / string / number(long|double) / true / false / null。
 * 不支持：注释、单引号、尾逗号（够了，我们只读写自己的备份文件）。
 */
object MiniJson {

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> appendString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Float -> sb.append(if (value.isFinite()) value.toString() else "null")
            is Double -> sb.append(if (value.isFinite()) value.toString() else "null")
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                value.forEach { (k, v) ->
                    if (!first) sb.append(',')
                    first = false
                    appendString(sb, k?.toString().orEmpty())
                    sb.append(':')
                    append(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                value.forEach { item ->
                    if (!first) sb.append(',')
                    first = false
                    append(sb, item)
                }
                sb.append(']')
            }
            else -> appendString(sb, value.toString())
        }
    }

    private fun appendString(sb: StringBuilder, text: String) {
        sb.append('"')
        text.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        sb.append('"')
    }

    /** 解析失败返回 null（同步文件损坏时按"没有远端数据"处理，不要抛到 UI）。 */
    fun parse(text: String): Any? = runCatching { Parser(text).parseValue() }.getOrNull()

    fun obj(value: Any?): Map<String, Any?> = (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    fun array(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()

    fun str(value: Any?): String = value as? String ?: ""

    fun long(value: Any?): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    fun bool(value: Any?, fallback: Boolean = false): Boolean = value as? Boolean ?: fallback

    private class Parser(private val text: String) {
        private var index = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (index >= text.length) error("unexpected end")
            return when (val c = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else error("unexpected '$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            index++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { index++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> { index++; return map }
                    else -> error("bad object at $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            index++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { index++; return list }
            while (true) {
                list += parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> { index++; return list }
                    else -> error("bad array at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (index >= text.length) error("unterminated string")
                when (val c = text[index++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val esc = text[index++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = text.substring(index, index + 4)
                                index += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("bad escape \\$esc")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = index
            if (peek() == '-') index++
            while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) index++
            val raw = text.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: error("bad number '$raw'")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, index)) error("bad literal at $index")
            index += word.length
            return value
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun peek(): Char = if (index < text.length) text[index] else error("unexpected end")

        private fun expect(c: Char) {
            if (peek() != c) error("expected '$c' at $index")
            index++
        }

        private fun error(message: String): Nothing = throw IllegalArgumentException(message)
    }
}
