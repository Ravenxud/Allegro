package com.ravenxud.allegro.data.site

import com.ravenxud.allegro.util.MiniJson

/**
 * 站点规则的导入/导出格式。
 *
 * 只包含"用户自己定过的规则"，不含内容与浏览记录：
 * 每站的 JS / 图片 / 桌面版，以及广告过滤白名单（按域名）。
 *
 * 纯逻辑（只依赖 MiniJson），便于单测。
 */
data class SiteRulesBundle(
    val exportedAt: Long,
    val sites: List<SiteSettings>,
    val adBlockWhitelist: List<String>,
) {

    fun encode(): String = MiniJson.write(
        linkedMapOf(
            "schema" to SCHEMA,
            "exportedAt" to exportedAt,
            "sites" to sites.map {
                linkedMapOf(
                    "host" to it.host,
                    "js" to it.javaScript,
                    "img" to it.images,
                    "ua" to it.desktopUa,
                )
            },
            "adblock" to adBlockWhitelist,
        ),
    )

    companion object {
        const val SCHEMA = 1

        fun decode(text: String): SiteRulesBundle? {
            val root = MiniJson.obj(MiniJson.parse(text))
            if (root.isEmpty()) return null
            val schema = MiniJson.long(root["schema"])
            if (schema <= 0 || schema > SCHEMA) return null

            val sites = MiniJson.array(root["sites"]).mapNotNull { raw ->
                val o = MiniJson.obj(raw)
                val host = MiniJson.str(o["host"]).trim().lowercase()
                if (host.isEmpty()) null else SiteSettings(
                    host = host,
                    javaScript = MiniJson.bool(o["js"], true),
                    images = MiniJson.bool(o["img"], true),
                    desktopUa = MiniJson.bool(o["ua"], false),
                )
            }.filterNot { it.isDefault }

            val adblock = MiniJson.array(root["adblock"])
                .map { MiniJson.str(it).trim().lowercase() }
                .filter { it.isNotEmpty() }

            return SiteRulesBundle(
                exportedAt = MiniJson.long(root["exportedAt"]),
                sites = sites,
                adBlockWhitelist = adblock.distinct(),
            )
        }
    }
}
