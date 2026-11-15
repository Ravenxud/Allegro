package com.ravenxud.allegro.data.model

/** 首页壁纸来源。 */
enum class WallpaperSource(val key: String) {
    /** 主题色光斑，跟随深浅色。 */
    DEFAULT("default"),

    /** Bing 每日壁纸，按天缓存到应用私有目录。 */
    BING("bing"),

    /** 用户自选图片（content:// URI，持久化授权）。 */
    CUSTOM("custom"),
    ;

    companion object {
        fun fromKey(raw: String?): WallpaperSource =
            entries.firstOrNull { it.key == raw } ?: DEFAULT
    }
}
