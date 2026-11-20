package com.bahaa.srtvideogenerator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ===== NEW CODE (update, part 1): rendering runs in a foreground service so it keeps going
// when you leave the app or turn the screen off. =====

data class RenderState(
    val rendering: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val resultUri: Uri? = null,
    val error: String? = null
)

/** Shared between the screen and the service (lives as long as the app process). */
object RenderController {
    private val _state = MutableStateFlow(RenderState())
    val state: StateFlow<RenderState> = _state.asStateFlow()

    @Volatile private var pending: RenderRequest? = null
    @Volatile var job: Job? = null

    fun begin(req: RenderRequest): Boolean {
        synchronized(this) {
            if (_state.value.rendering) return false
            pending = req
            _state.value = RenderState(rendering = true, status = "Starting…")
            return true
        }
    }

    fun takePending(): RenderRequest? = synchronized(this) { pending.also { pending = null } }

    fun progress(p: Float, msg: String) {
        _state.value = _state.value.copy(progress = p, status = msg)
    }

    fun finish(uri: Uri?, error: String?, status: String) {
        _state.value = RenderState(rendering = false, progress = if (uri != null) 1f else 0f,
            status = status, resultUri = uri, error = error)
    }

    fun cancel() { job?.cancel() }
}

class RenderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            RenderController.cancel()
            return START_NOT_STICKY
        }
        val req = RenderController.takePending()
        if (req == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(0, "Starting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hu:render").apply {
            acquire(4 * 60 * 60 * 1000L)
        }

        RenderController.job = scope.launch {
            val self = coroutineContext[Job]
            var uri: Uri? = null
            var error: String? = null
            var status = ""
            try {
                uri = NativeMediaGenerator(applicationContext).generate(
                    req,
                    onProgress = { p, msg ->
                        RenderController.progress(p, msg)
                        val pct = (p * 100).roundToInt()
                        if (pct != lastPercent) {
                            lastPercent = pct
                            notify(buildNotification(pct, msg))
                        }
                    },
                    cancelled = { self?.isActive != true }
                )
                status = "Done – saved to Movies/SRT Video Generator"
            } catch (_: NativeMediaGenerator.Cancelled) {
                status = "Cancelled"
            } catch (e: Throwable) {
                error = "${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}"
                status = ""
            } finally {
                try { wakeLock?.release() } catch (_: Exception) {}
            }
            RenderController.finish(uri, error, status)
            ServiceCompat.stopForeground(this@RenderService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notify(finalNotification(if (uri != null) "Video saved to your gallery" else (error ?: status)), NOTIF_ID + 1)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Video rendering", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(percent: Int, text: String): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, RenderService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Making your video… $percent%")
            .setContentText(text)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun finalNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Video renderer")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    private fun notify(n: Notification, id: Int = NOTIF_ID) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_CANCEL = "com.bahaa.srtvideogenerator.CANCEL"
        private const val CHANNEL = "render"
        private const val NOTIF_ID = 41
    }
}
