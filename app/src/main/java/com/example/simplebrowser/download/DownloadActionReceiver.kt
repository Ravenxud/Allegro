package com.example.simplebrowser.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.simplebrowser.di.ServiceLocator

/** 通知栏「暂停 / 继续 / 取消」的落点。 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) return
        ServiceLocator.init(context.applicationContext)
        val center = ServiceLocator.downloadCenter
        when (intent.action) {
            ACTION_PAUSE -> center.pause(id)
            ACTION_RESUME -> center.resume(id)
            ACTION_CANCEL -> center.cancel(id)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.simplebrowser.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.example.simplebrowser.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.simplebrowser.action.CANCEL_DOWNLOAD"
        const val EXTRA_ID = "download_id"
    }
}
