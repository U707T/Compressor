package compress.joshattic.us.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.annotation.RequiresApi
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import compress.joshattic.us.MainActivity
import compress.joshattic.us.R

/**
 * Keeps a long batch alive while the app is in the background.
 *
 * Xiaomi HyperOS (and other aggressive OEM power managers) will freeze or kill a
 * plain background process mid-encode; a `dataSync` foreground service makes the
 * export survive screen-off and app switching. The export itself still runs in
 * [compress.joshattic.us.viewmodel.CompressorViewModel] — this service only owns
 * the notification and the process priority.
 */
class CompressionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: getString(R.string.batch_notification_title)
        val done = intent?.getIntExtra(EXTRA_DONE, 0) ?: 0
        val total = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0

        val notification = buildNotification(title, done, total)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Starting an FGS can be refused (background start restrictions,
            // missing POST_NOTIFICATIONS on some ROMs). Never crash the export.
            e.printStackTrace()
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15+ caps `dataSync` foreground services at 6 hours. If a huge queue
     * ever reaches that limit the system calls this; stop cleanly instead of
     * letting the platform ANR the process.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf(startId)
    }

    private fun buildNotification(title: String, done: Int, total: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_compress)
            .setContentTitle(title)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (total > 0) {
            val safeDone = done.coerceIn(0, total)
            builder.setContentText(
                getString(R.string.batch_notification_progress, safeDone, total)
            )
            builder.setProgress(total, safeDone, false)
        } else {
            builder.setContentText(getString(R.string.batch_notification_preparing))
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.batch_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.batch_notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "compression_progress"
        private const val NOTIFICATION_ID = 0x434F4D50 // "COMP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DONE = "done"
        private const val EXTRA_TOTAL = "total"

        fun start(context: Context, total: Int) {
            dispatch(context, context.getString(R.string.batch_notification_title), 0, total)
        }

        fun update(context: Context, title: String, done: Int, total: Int) {
            dispatch(context, title, done, total)
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CompressionForegroundService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun dispatch(context: Context, title: String, done: Int, total: Int) {
            try {
                val intent = Intent(context, CompressionForegroundService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_DONE, done)
                    putExtra(EXTRA_TOTAL, total)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException on Android 12+ when the
                // app is already in the background — the export keeps running.
                e.printStackTrace()
            }
        }
    }
}
