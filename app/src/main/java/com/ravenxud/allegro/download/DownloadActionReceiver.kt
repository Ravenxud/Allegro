package com.ravenxud.allegro.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ravenxud.allegro.di.ServiceLocator

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
        const val ACTION_PAUSE = "com.ravenxud.allegro.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.ravenxud.allegro.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.ravenxud.allegro.action.CANCEL_DOWNLOAD"
        const val EXTRA_ID = "download_id"
    }
}
