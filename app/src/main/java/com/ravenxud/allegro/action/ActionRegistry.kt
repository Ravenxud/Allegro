package com.ravenxud.allegro.action

/**
 * 全局动作注册表。
 *
 * 新增一个动作：
 * 1. 实现 [BrowserAction]；
 * 2. 在 [builtIn] 里加一行；
 * 3. 不需要改 UI。
 */
object ActionRegistry {

    private val builtIn: List<BrowserAction> = listOf(
        OpenInNewTabAction,
        OpenInBackgroundAction,
        CopyLinkAction,
        CopyImageAction,
        DownloadLinkAction,
        SaveImageAction,
        ShareAction,
    )

    private val custom = mutableListOf<BrowserAction>()

    fun register(action: BrowserAction) {
        custom.removeAll { it.id == action.id }
        custom += action
    }

    fun actions(): List<BrowserAction> = builtIn + custom

    fun forGroup(group: ActionGroup, context: ActionContext): List<BrowserAction> =
        actions().filter { group in it.groups && it.isAvailable(context) }
}
