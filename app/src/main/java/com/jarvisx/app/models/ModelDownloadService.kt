package com.jarvisx.app.models

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.jarvisx.app.JarvisApp
import com.jarvisx.app.MainActivity
import com.jarvisx.core.model.ModelCatalog
import com.jarvisx.core.model.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of each model's download, shown on the Models screen. */
sealed class DownloadState {
    object Idle : DownloadState()
    object Checking : DownloadState()
    data class Downloading(val bytes: Long, val total: Long) : DownloadState() {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    object Verifying : DownloadState()
    object Done : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

object DownloadCenter {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    fun set(id: String, state: DownloadState) = _states.update { it + (id to state) }
    fun get(id: String): DownloadState = _states.value[id] ?: DownloadState.Idle
    val anyActive: Boolean
        get() = _states.value.values.any { it is DownloadState.Checking || it is DownloadState.Downloading || it is DownloadState.Verifying }
}

/**
 * Foreground service so a multi-gigabyte download survives the screen turning off.
 * Android shows its notification for as long as it runs. One download at a time.
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var currentId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                job?.cancel()
                currentId?.let { DownloadCenter.set(it, DownloadState.Idle) }
                stopSelfCompletely()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val spec = intent.getStringExtra(EXTRA_ID)?.let { ModelCatalog.byId(it) } ?: ModelCatalog.all.first()
                // startForegroundService() requires startForeground() promptly, even if we then decline.
                startForeground(NOTIFICATION_ID, notification(spec, "Starting…", 0, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                if (job?.isActive == true) { // one download at a time
                    if (spec.id != currentId) DownloadCenter.set(spec.id, DownloadState.Failed("Another download is running. Try again when it finishes."))
                    return START_NOT_STICKY
                }
                if (intent.getStringExtra(EXTRA_ID)?.let { ModelCatalog.byId(it) } == null) {
                    stopSelfCompletely()
                    return START_NOT_STICKY
                }
                run(spec)
            }
        }
        return START_NOT_STICKY
    }

    private fun run(spec: ModelSpec) {
        currentId = spec.id
        val downloader = ModelDownloader((application as JarvisApp).graph.modelStore)
        job = scope.launch {
            try {
                downloader.download(spec) { p ->
                    when (p) {
                        ModelDownloader.Progress.Checking -> DownloadCenter.set(spec.id, DownloadState.Checking)
                        is ModelDownloader.Progress.Downloading -> {
                            DownloadCenter.set(spec.id, DownloadState.Downloading(p.bytes, p.total))
                            val pct = if (p.total > 0) (p.bytes * 100 / p.total).toInt() else 0
                            notify(notification(spec, "${p.bytes / MB} / ${if (p.total > 0) p.total / MB else "?"} MB", pct, p.total <= 0))
                        }
                        ModelDownloader.Progress.Verifying -> {
                            DownloadCenter.set(spec.id, DownloadState.Verifying)
                            notify(notification(spec, "Verifying checksum…", 100, true))
                        }
                    }
                }
                DownloadCenter.set(spec.id, DownloadState.Done)
            } catch (e: CancellationException) {
                DownloadCenter.set(spec.id, DownloadState.Idle)
            } catch (e: Exception) {
                DownloadCenter.set(spec.id, DownloadState.Failed(e.message ?: "Download failed."))
            } finally {
                stopSelfCompletely()
            }
        }
    }

    private fun stopSelfCompletely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(n: Notification) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)

    private fun notification(spec: ModelSpec, text: String, percent: Int, indeterminate: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${spec.displayName}")
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 42
        private const val MB = 1024L * 1024L
        private const val ACTION_START = "com.jarvisx.app.DOWNLOAD"
        private const val ACTION_CANCEL = "com.jarvisx.app.CANCEL_DOWNLOAD"
        private const val EXTRA_ID = "model_id"

        fun start(context: Context, spec: ModelSpec) {
            DownloadCenter.set(spec.id, DownloadState.Checking)
            context.startForegroundService(
                Intent(context, ModelDownloadService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, spec.id)
            )
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
