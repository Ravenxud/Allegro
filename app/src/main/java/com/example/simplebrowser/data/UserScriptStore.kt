package com.example.simplebrowser.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 一条用户脚本。 */
data class UserScript(
    val id: String,
    val name: String,
    val namespace: String,
    val matches: List<String>,
    val runAt: String,
    val code: String,
    val enabled: Boolean,
    val installedAt: Long,
)

/**
 * 用户脚本仓库：代码存文件，元数据存 JSON 索引。
 *
 * 不建数据库表，避免又一次 Room 迁移；脚本数量通常个位数，JSON 足够。
 */
class UserScriptStore(context: android.content.Context) {

    private val dir = File(context.filesDir, "userscripts").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun all(): List<UserScript> = runCatching {
        val array = JSONArray(index.readText())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val code = runCatching { File(dir, o.optString("file")).readText() }.getOrDefault("")
            if (code.isBlank()) return@mapNotNull null
            UserScript(
                id = o.optString("id"),
                name = o.optString("name"),
                namespace = o.optString("namespace"),
                matches = o.optJSONArray("matches")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                runAt = o.optString("runAt", "document-end"),
                code = code,
                enabled = o.optBoolean("enabled", true),
                installedAt = o.optLong("installedAt"),
            )
        }
    }.getOrDefault(emptyList())

    /** 从脚本文本解析元数据并保存；解析失败返回 null。 */
    fun install(source: String): UserScript? {
        val meta = parseMetadata(source) ?: return null
        val id = UUID.randomUUID().toString()
        val fileName = "$id.js"
        runCatching { File(dir, fileName).writeText(source) }.getOrElse { return null }

        val list = all().toMutableList()
        list += meta.copy(id = id, code = source, installedAt = System.currentTimeMillis())
        write(list.map { it.copy(code = "") to fileNameOf(it.id) })
        return meta.copy(id = id, code = source)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val list = all().map { if (it.id == id) it.copy(enabled = enabled) else it }
        write(list.map { it.copy(code = "") to fileNameOf(it.id) })
    }

    fun delete(id: String) {
        runCatching { File(dir, fileNameOf(id)).delete() }
        write(all().filterNot { it.id == id }.map { it.copy(code = "") to fileNameOf(it.id) })
    }

    /** 当前 URL 需要注入的脚本（按 run-at 过滤）。 */
    fun forUrl(url: String, runAt: String): List<UserScript> =
        all().filter { it.enabled && it.runAt.contains(runAt.removePrefix("document-")) && match(it, url) }

    private fun fileNameOf(id: String) = "$id.js"

    private fun write(entries: List<Pair<UserScript, String>>) {
        val array = JSONArray()
        entries.forEach { (script, fileName) ->
            array.put(JSONObject().apply {
                put("id", script.id)
                put("name", script.name)
                put("namespace", script.namespace)
                put("matches", JSONArray(script.matches))
                put("runAt", script.runAt)
                put("enabled", script.enabled)
                put("installedAt", script.installedAt)
                put("file", fileName)
            })
        }
        runCatching { index.writeText(array.toString()) }
    }

    // ---------------------------------------------------------------- 解析

    private fun parseMetadata(source: String): UserScript? {
        val header = Regex("/\\*\\*?[\\s\\S]*?\\*/").find(source)?.value ?: return null
        fun field(name: String): String? =
            Regex("@$name\\s+([^\\r\\n*]+)").find(header)?.groupValues?.get(1)?.trim()

        val matches = Regex("@(?:match|include)\\s+([^\\r\\n*]+)")
            .findAll(header)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

        val name = field("name") ?: return null
        if (matches.isEmpty()) return null
        return UserScript(
            id = "",
            name = name,
            namespace = field("namespace").orEmpty(),
            matches = matches,
            runAt = "@" + (field("run-at") ?: "document-end"),
            code = source,
            enabled = true,
            installedAt = 0L,
        )
    }

    // 支持通配符形式（星号匹配任意字符）与 /正则/ 形式。
    private fun match(script: UserScript, url: String): Boolean = script.matches.any { pattern ->
        when {
            pattern.startsWith("/") && pattern.endsWith("/") ->
                runCatching { Regex(pattern.trim('/')).containsMatchIn(url) }.getOrDefault(false)
            else -> {
                val regex = "^" + Regex.escape(pattern)
                    .replace("\\*", ".*")
                    .replace("\\?", ".") + "$"
                runCatching { Regex(regex).matches(url) }.getOrDefault(false)
            }
        }
    }
}
