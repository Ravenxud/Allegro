package com.example.simplebrowser.action

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.simplebrowser.browser.BrowserController
import com.example.simplebrowser.browser.MenuRequest

/** 动作作用于哪类命中目标。 */
enum class ActionGroup { LINK, IMAGE }

data class ActionContext(
    val controller: BrowserController,
    val tabId: String,
    val url: String,
    val pageUrl: String,
    val group: ActionGroup,
)

/**
 * 一个可执行动作。菜单、长按、以后的通知/快捷方式都从注册表取，
 * 新增功能只需实现本接口并在 [ActionRegistry] 里注册。
 */
interface BrowserAction {
    val id: String
    val titleRes: Int
    val icon: ImageVector
    val groups: Set<ActionGroup>
    fun isAvailable(context: ActionContext): Boolean = true
    fun execute(context: ActionContext)
}

fun MenuRequest.toGroup(): ActionGroup =
    if (kind == MenuRequest.Kind.IMAGE) ActionGroup.IMAGE else ActionGroup.LINK

fun MenuRequest.toActionContext(controller: BrowserController): ActionContext =
    ActionContext(controller, tabId, url, pageUrl, toGroup())
