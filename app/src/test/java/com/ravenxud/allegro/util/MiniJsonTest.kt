package com.ravenxud.allegro.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `writes primitives`() {
        assertEquals("null", MiniJson.write(null))
        assertEquals("true", MiniJson.write(true))
        assertEquals("42", MiniJson.write(42))
        assertEquals("\"hi\"", MiniJson.write("hi"))
    }

    @Test
    fun `escapes strings`() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", MiniJson.write("a\"b\\c\nd"))
        assertEquals("\"\\u0001\"", MiniJson.write("\u0001"))
    }

    @Test
    fun `roundtrip nested structure`() {
        val value = mapOf(
            "a" to 1L,
            "b" to listOf("x", 2L, null, true),
            "c" to mapOf("d" to "中文", "e" to 1.5),
        )
        val parsed = MiniJson.obj(MiniJson.parse(MiniJson.write(value)))
        assertEquals(1L, MiniJson.long(parsed["a"]))
        assertEquals(4, MiniJson.array(parsed["b"]).size)
        assertEquals("中文", MiniJson.str(MiniJson.obj(parsed["c"])["d"]))
    }

    @Test
    fun `parses unicode escapes`() {
        val parsed = MiniJson.obj(MiniJson.parse("""{"k":"\u4e2d\u6587"}"""))
        assertEquals("中文", MiniJson.str(parsed["k"]))
    }

    @Test
    fun `returns null for broken input instead of throwing`() {
        assertNull(MiniJson.parse("{"))
        assertNull(MiniJson.parse("{\"a\":}"))
        assertNull(MiniJson.parse("not json"))
    }

    @Test
    fun `array and obj helpers tolerate wrong types`() {
        assertEquals(emptyMap<String, Any?>(), MiniJson.obj("x"))
        assertTrue(MiniJson.array(null).isEmpty())
        assertEquals("", MiniJson.str(1L))
        assertEquals(0L, MiniJson.long("abc"))
        assertEquals(true, MiniJson.bool(true))
        assertEquals(true, MiniJson.bool(null, true))
    }
}
