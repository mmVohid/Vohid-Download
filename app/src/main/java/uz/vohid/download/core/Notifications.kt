package uz.vohid.download.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uz.vohid.download.MainActivity
import uz.vohid.download.R
import uz.vohid.download.data.DownloadItem
import uz.vohid.download.data.Status

object Notifications {
    private const val CH_PROGRESS = "progress"
    private const val CH_DONE = "done"
    const val ID_SERVICE = 1001

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CH_PROGRESS,
                ctx.getString(R.string.notif_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_DONE,
                ctx.getString(R.string.notif_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun openDownloads(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_DOWNLOADS
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Foreground servis uchun doimiy bildirishnoma. */
    fun progress(ctx: Context, items: List<DownloadItem>): Notification {
        val active = items.filter { it.status.isActive }
        val running = active.filter { it.status == Status.RUNNING || it.status == Status.SAVING }
        val current = running.firstOrNull() ?: active.firstOrNull()
        val avg = if (running.isEmpty()) 0f else running.map { it.progress }.average().toFloat()

        val cancelAll = PendingIntent.getService(
            ctx,
            1,
            Intent(ctx, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(ctx.getString(R.string.notif_progress_title, active.size))
            .setContentText(current?.title.orEmpty())
            .setColor(ContextCompat.getColor(ctx, R.color.notif_accent))
            .setProgress(100, (avg * 100).toInt(), running.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openDownloads(ctx))
            .addAction(0, ctx.getString(R.string.notif_cancel_all), cancelAll)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun done(ctx: Context, item: DownloadItem) {
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(ctx.getString(R.string.notif_done))
            .setContentText(item.title)
            .setColor(ContextCompat.getColor(ctx, R.color.notif_accent))
            .setAutoCancel(true)
            .setContentIntent(openDownloads(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), n) }
    }

    @SuppressLint("MissingPermission")
    fun failed(ctx: Context, item: DownloadItem) {
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(ctx.getString(R.string.notif_failed))
            .setContentText(item.title)
            .setSubText(item.message)
            .setColor(ContextCompat.getColor(ctx, R.color.notif_accent))
            .setAutoCancel(true)
            .setContentIntent(openDownloads(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), n) }
    }
}
