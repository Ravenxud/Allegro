package com.ravenxud.allegro.ui.legal

/**
 * 第三方组件登记表。
 *
 * 规矩：**任何外部代码进入本工程，必须在这里登记一条**（名称 / 作者 / 许可证 / 来源 / 用途）。
 * 设置 →「开源许可」直接渲染这张表，所以它同时是合规声明与用户可见的致谢页。
 *
 * 本项目自身以 GPL-3.0 发布（见仓库根目录 LICENSE）。引入 GPL-3.0 代码后必须保持该许可，
 * 并在此列出出处；引入 Apache-2.0 / MIT 代码需保留其版权与许可声明。
 */
data class ThirdPartyEntry(
    /** 组件名或项目名。 */
    val name: String,
    /** 作者 / 组织。 */
    val author: String,
    /** 许可证标识，如 GPL-3.0、Apache-2.0、MIT。 */
    val license: String,
    /** 上游地址。 */
    val url: String,
    /** 在本工程中的用途，一句话。 */
    val usage: String,
    /** 是否为「直接复用其源码」。 */
    val vendored: Boolean = false,
)

object ThirdPartyRegistry {

    /** 本项目自身的许可证声明，显示在列表最上方。 */
    const val SELF_LICENSE = "GPL-3.0"
    const val SELF_SUMMARY = "Allegro 以 GPL-3.0 发布；源码见项目主页。"

    val entries: List<ThirdPartyEntry> = listOf(
        // ---------- 直接复用源码（vendored） ----------
        ThirdPartyEntry(
            name = "KernelSU Style UI Kit",
            author = "chenaizhang",
            license = "GPL-3.0",
            url = "https://github.com/chenaizhang/KernelSU-Style-UI-Kit",
            usage = "部分 UI 组件与工具（详见每处文件头的出处注释）",
            vendored = true,
        ),
        ThirdPartyEntry(
            name = "KernelSU",
            author = "tiann 及贡献者",
            license = "GPL-3.0",
            url = "https://github.com/tiann/KernelSU",
            usage = "上述 UI Kit 的上游；其组件设计源自 KernelSU Manager",
            vendored = true,
        ),

        // ---------- 依赖库 ----------
        ThirdPartyEntry(
            name = "Miuix",
            author = "YuKongA",
            license = "Apache-2.0",
            url = "https://github.com/miuix-kotlin-multiplatform/miuix",
            usage = "界面组件、设置项、图标",
        ),
        ThirdPartyEntry(
            name = "Backdrop",
            author = "Kyant",
            license = "Apache-2.0",
            url = "https://github.com/Kyant0/AndroidLiquidGlass",
            usage = "层捕获与玻璃材质（首页玻璃）",
        ),
        ThirdPartyEntry(
            name = "AndroidX",
            author = "The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack",
            usage = "Compose、Room、Activity、Lifecycle 等基础库",
        ),
        ThirdPartyEntry(
            name = "OkHttp",
            author = "Square, Inc.",
            license = "Apache-2.0",
            url = "https://square.github.io/okhttp/",
            usage = "下载、WebDAV 同步、桌面版文档抓取",
        ),
        ThirdPartyEntry(
            name = "jsoup",
            author = "Jonathan Hedley",
            license = "MIT",
            url = "https://jsoup.org/",
            usage = "离线页与阅读模式的 HTML 处理",
        ),
        ThirdPartyEntry(
            name = "Kotlin / kotlinx.coroutines",
            author = "JetBrains",
            license = "Apache-2.0",
            url = "https://kotlinlang.org/",
            usage = "语言运行时与协程",
        ),
    )

    /** 仅「复用源码」的条目，用于关于页的醒目致谢。 */
    val vendoredEntries: List<ThirdPartyEntry> get() = entries.filter { it.vendored }
}
