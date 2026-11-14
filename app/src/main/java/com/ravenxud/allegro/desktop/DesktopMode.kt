package com.ravenxud.allegro.desktop

/**
 * 桌面版是否生效的统一判定。
 *
 * 判定顺序（先命中先返回）：
 * 1. 站点设置明确「强制桌面」→ 开
 * 2. 站点设置明确「强制移动」（`desktopUa == false` 且用户改动过）→ 关
 * 3. 标签页的临时桌面开关
 * 4. 全局「始终请求桌面版」
 *
 * 注意第 2 步的语义：站点设置里 `desktopUa = false` 是"用户明确要求移动版"，
 * 但默认值也是 false，所以调用方必须传 `siteConfigured`（该站点是否有显式设置）。
 */
object DesktopMode {

    fun effective(
        siteConfigured: Boolean,
        siteDesktopUa: Boolean,
        tabDesktop: Boolean,
        globalAlways: Boolean,
    ): Boolean = when {
        siteConfigured && siteDesktopUa -> true
        siteConfigured -> false
        tabDesktop -> true
        else -> globalAlways
    }
}
