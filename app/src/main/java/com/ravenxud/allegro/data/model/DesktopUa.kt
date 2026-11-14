package com.ravenxud.allegro.data.model

/** 桌面版使用的 User-Agent 平台。 */
enum class DesktopUa(val key: String, val label: String) {
    MACOS("macos", "macOS"),
    WINDOWS("windows", "Windows"),
    ;

    companion object {
        fun fromKey(key: String?): DesktopUa =
            entries.firstOrNull { it.key == key } ?: MACOS
    }
}
