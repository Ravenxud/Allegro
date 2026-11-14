package com.ravenxud.allegro.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ravenxud.allegro.R

/** 长按菜单/下载回调里的系统级动作：复制、下载、分享。 */
internal class MenuActions(private val context: Context) {

    fun copy(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("SimpleBrowser", text))
        toast(R.string.menu_copied)
    }

    fun share(url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.menu_share)))
    }

    private fun toast(resId: Int, vararg args: Any) {
        val text = if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
