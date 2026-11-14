package com.example.simplebrowser.action

import com.example.simplebrowser.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Share

/** 长按菜单的内置动作。 */
object OpenInNewTabAction : BrowserAction {
    override val id = "open_new_tab"
    override val titleRes = R.string.menu_new_tab
    override val icon = MiuixIcons.Add
    override val groups = setOf(ActionGroup.LINK, ActionGroup.IMAGE)
    override fun execute(context: ActionContext) = context.controller.openTab(context.url).let { }
}

object OpenInBackgroundAction : BrowserAction {
    override val id = "open_background_tab"
    override val titleRes = R.string.menu_background_tab
    override val icon = MiuixIcons.Add
    override val groups = setOf(ActionGroup.LINK)
    override fun execute(context: ActionContext) {
        context.controller.openTab(context.url, background = true)
    }
}

object CopyLinkAction : BrowserAction {
    override val id = "copy_link"
    override val titleRes = R.string.menu_copy_link
    override val icon = MiuixIcons.Copy
    override val groups = setOf(ActionGroup.LINK)
    override fun execute(context: ActionContext) = context.controller.copyToClipboard(context.url)
}

object CopyImageAction : BrowserAction {
    override val id = "copy_image"
    override val titleRes = R.string.menu_copy_image
    override val icon = MiuixIcons.Copy
    override val groups = setOf(ActionGroup.IMAGE)
    override fun execute(context: ActionContext) = context.controller.copyToClipboard(context.url)
}

object DownloadLinkAction : BrowserAction {
    override val id = "download_link"
    override val titleRes = R.string.menu_download_link
    override val icon = MiuixIcons.Download
    override val groups = setOf(ActionGroup.LINK)
    override fun execute(context: ActionContext) = context.controller.download(context.url)
}

object SaveImageAction : BrowserAction {
    override val id = "save_image"
    override val titleRes = R.string.menu_save_image
    override val icon = MiuixIcons.Download
    override val groups = setOf(ActionGroup.IMAGE)
    override fun execute(context: ActionContext) = context.controller.download(context.url)
}

object ShareAction : BrowserAction {
    override val id = "share"
    override val titleRes = R.string.menu_share
    override val icon = MiuixIcons.Share
    override val groups = setOf(ActionGroup.LINK)
    override fun execute(context: ActionContext) = context.controller.share(context.url)
}
