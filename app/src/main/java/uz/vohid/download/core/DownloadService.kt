package uz.vohid.download.core

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Yuklashlar davomida jarayonni tirik ushlab turuvchi foreground servis.
 * Asosiy ishni [Downloader] bajaradi, servis esa bildirishnomani yangilaydi.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL_ALL) Downloader.cancelAll()

        ServiceCompat.startForeground(
            this,
            Notifications.ID_SERVICE,
            Notifications.progress(this, Downloader.items.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (job == null) {
            job = scope.launch {
                var last = 0L
                Downloader.items.collect { list ->
                    if (list.none { it.status.isActive }) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf(lastStartId)
                        return@collect
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - last >= 700) {
                        last = now
                        runCatching {
                            NotificationManagerCompat.from(this@DownloadService)
                                .notify(Notifications.ID_SERVICE, Notifications.progress(this@DownloadService, list))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL_ALL = "uz.vohid.download.CANCEL_ALL"

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (e: Throwable) {
                // Fon rejimida boshlashga ruxsat yo'q bo'lishi mumkin — yuklash baribir davom etadi.
            }
        }
    }
}
