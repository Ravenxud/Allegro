package com.example.simplebrowser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.simplebrowser.MainActivity
import com.example.simplebrowser.R
import com.example.simplebrowser.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 自研下载器的前台服务：进度通知 + 暂停/继续/取消。 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLocator.init(applicationContext)
        runCatching { startForeground(NOTIFICATION_ID, build(null)) }

        scope.launch {
            var emptySince = 0L
            ServiceLocator.downloadCenter.progress.collectLatest { map ->
                val current = map.values.firstOrNull()
                if (current == null) {
                    if (emptySince == 0L) emptySince = android.os.SystemClock.elapsedRealtime()
                    if (android.os.SystemClock.elapsedRealtime() - emptySince > 3000) stopSelf()
                } else {
                    emptySince = 0L
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    runCatching { manager.notify(NOTIFICATION_ID, build(current)) }
                }
            }
        }
        return START_STICKY
    }

    private fun build(item: DownloadProgress?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setOnlyAlertOnce(true)
            .setOngoing(item != null)
            .setContentTitle(
                if (item == null) getString(R.string.app_name)
                else getString(R.string.download_notification_title, item.percent)
            )
            .setContentText(
                when {
                    item == null -> ""
                    item.speedBytesPerSec > 0 && item.etaSeconds > 0 ->
                        "${item.fileName} · ${humanSpeed(item.speedBytesPerSec)} · ${humanEta(item.etaSeconds)}"
                    else -> item.fileName
                }
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )

        if (item != null) {
            builder.setProgress(100, item.percent, item.total <= 0)
            builder.addAction(
                0,
                getString(R.string.download_pause),
                actionIntent(DownloadActionReceiver.ACTION_PAUSE, item.id),
            )
            builder.addAction(
                0,
                getString(R.string.download_cancel),
                actionIntent(DownloadActionReceiver.ACTION_CANCEL, item.id),
            )
        }
        return builder.build()
    }

    private fun actionIntent(action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            (action.hashCode() * 31 + id).toInt(),
            Intent(this, DownloadActionReceiver::class.java)
                .setAction(action)
                .putExtra(DownloadActionReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun humanSpeed(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024) String.format("%.1f MB/s", kb / 1024) else String.format("%.0f KB/s", kb)
    }

    private fun humanEta(seconds: Long): String =
        if (seconds >= 60) "${seconds / 60} 分 ${seconds % 60} 秒" else "$seconds 秒"

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "download_progress"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}
