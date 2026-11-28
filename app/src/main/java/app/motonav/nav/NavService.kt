package app.motonav.nav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.motonav.MainActivity
import app.motonav.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import app.motonav.i18n.tr

/**
 * Υπηρεσία προσκηνίου για όσο διαρκεί η πλοήγηση. Με αυτήν το Android αφήνει την εφαρμογή
 * να παίρνει GPS, να μιλάει και να γράφει τη διαδρομή όταν η οθόνη είναι σβηστή
 * ή είσαι σε άλλη εφαρμογή. Δείχνει μόνιμη ειδοποίηση με την επόμενη οδηγία και κουμπί «Τέλος».
 */
class NavService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                NavBus.requestStop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val text = intent?.getStringExtra(EXTRA_TEXT) ?: tr("Πλοήγηση σε εξέλιξη")
                val n = notification(this, text)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Και μικρόφωνο, αν έχει άδεια: για να ακούει η παρέα (VOX) και με σβηστή οθόνη.
                    val mic = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        (if (mic && Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                    ServiceCompat.startForeground(this, NOTIF_ID, n, types)
                } else {
                    startForeground(NOTIF_ID, n)
                }
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "app.motonav.STOP_NAV"
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            val i = Intent(context, NavService::class.java).putExtra(EXTRA_TEXT, text)
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        /** Νέο κείμενο στην ειδοποίηση (επόμενη οδηγία). */
        fun update(context: Context, text: String) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching { nm.notify(NOTIF_ID, notification(context, text)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavService::class.java))
        }

        private fun notification(context: Context, text: String): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, tr("Πλοήγηση"), NotificationManager.IMPORTANCE_LOW).apply {
                        description = tr("Μόνιμη ειδοποίηση όσο πλοηγείσαι")
                        setShowBadge(false)
                    }
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stop = PendingIntent.getService(
                context, 1,
                Intent(context, NavService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.puck_arrow)
                .setContentTitle(tr("Streetwolf · πλοήγηση"))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentIntent(open)
                .addAction(0, tr("Τέλος"), stop)
                .build()
        }
    }
}

/** Μήνυμα από την ειδοποίηση («Τέλος») προς την οθόνη. */
object NavBus {
    /** Τρέχει πλοήγηση; (για το μικρό παράθυρο όταν βγαίνεις από την εφαρμογή) */
    val navigating = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val _stop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequests: SharedFlow<Unit> = _stop

    fun requestStop() {
        _stop.tryEmit(Unit)
    }
}
