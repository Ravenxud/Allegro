package com.ravenxud.allegro.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Theme

/**
 * 全站唯一图标入口。
 *
 * 规矩：界面里**只能**引用 `AllegroIcons.X`，不要直接写 `MiuixIcons.X`。
 *
 * 为什么：之前 20+ 个文件各自引用图标，尺寸（16/18/20/24/28dp）和描边风格互不相同，
 * 视觉上"不像一套"。收敛到一个文件后：
 *  1. 尺寸档位统一（[Size.Chrome] / [Size.Row] / [Size.Large]）
 *  2. 将来整体换成别的图标集（如 Tabler），只改这一个文件的实现，调用点零改动
 */
object AllegroIcons {

    /** 图标尺寸档位。 */
    object Size {
        /** 工具栏、圆形按钮里的图标。 */
        val Chrome: Dp = 20.dp

        /** 列表行、菜单行里的图标。 */
        val Row: Dp = 24.dp

        /** 空态插图、强调入口。 */
        val Large: Dp = 32.dp
    }

    // ---- 导航 ----
    val Back: ImageVector get() = MiuixIcons.Back
    val Forward: ImageVector get() = MiuixIcons.Forward
    val Close: ImageVector get() = MiuixIcons.Close
    val More: ImageVector get() = MiuixIcons.More
    val ChevronForward: ImageVector get() = MiuixIcons.ChevronForward
    val Refresh: ImageVector get() = MiuixIcons.Refresh
    val Home: ImageVector get() = MiuixIcons.Home

    // ---- 标签 ----
    val Add: ImageVector get() = MiuixIcons.Add
    val AddCircle: ImageVector get() = MiuixIcons.AddCircle
    val GridView: ImageVector get() = MiuixIcons.GridView
    val Recent: ImageVector get() = MiuixIcons.Recent
    val Sort: ImageVector get() = MiuixIcons.Sort
    val Copy: ImageVector get() = MiuixIcons.Copy
    val Paste: ImageVector get() = MiuixIcons.Paste
    val ListView: ImageVector get() = MiuixIcons.ListView

    // ---- 页面动作 ----
    val Search: ImageVector get() = MiuixIcons.Search
    val Share: ImageVector get() = MiuixIcons.Share
    val Download: ImageVector get() = MiuixIcons.Download
    val Folder: ImageVector get() = MiuixIcons.Folder
    val Delete: ImageVector get() = MiuixIcons.Delete
    val Edit: ImageVector get() = MiuixIcons.Edit
    val Settings: ImageVector get() = MiuixIcons.Settings
    val Theme: ImageVector get() = MiuixIcons.Theme
    val Background: ImageVector get() = MiuixIcons.Background
    val Blocklist: ImageVector get() = MiuixIcons.Blocklist
    val Hide: ImageVector get() = MiuixIcons.Hide
    val Favorites: ImageVector get() = MiuixIcons.Favorites
    val Ok: ImageVector get() = MiuixIcons.Ok
    val Play: ImageVector get() = MiuixIcons.Play
    val Pause: ImageVector get() = MiuixIcons.Pause
}
