package app.motonav.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Κενό listener: υπάρχει μόνο για να μας δώσει το Android τα MediaSession των άλλων εφαρμογών
 * (Spotify, YouTube Music κ.λπ.). Θέλει μία φορά «Πρόσβαση στις ειδοποιήσεις».
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() { MediaHub.start(this) }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { MediaHub.refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { MediaHub.refresh() }
}

data class NowPlaying(val pkg: String, val title: String, val artist: String, val art: Bitmap?, val playing: Boolean)

/** Τι παίζει τώρα, και play/pause/επόμενο/προηγούμενο, για οποιαδήποτε εφαρμογή μουσικής. */
object MediaHub {
    var now by mutableStateOf<NowPlaying?>(null)
        private set

    /** Η εφαρμογή που διάλεξες από το συρτάρι: προτιμάται όταν παίζουν πολλές. */
    var preferredPkg: String? = null

    private val main = Handler(Looper.getMainLooper())
    private var mgr: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() { controller = null; refresh() }
    }

    fun hasAccess(c: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun accessIntent(): Intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun start(c: Context) {
        if (!hasAccess(c)) return
        if (mgr != null) { refresh(); return }
        val m = c.applicationContext.getSystemService(MediaSessionManager::class.java) ?: return
        val cn = ComponentName(c.applicationContext, MediaListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            mgr = m; component = cn
            pick(m.getActiveSessions(cn))
        } catch (_: SecurityException) {
            mgr = null
        }
    }

    fun refresh() {
        val m = mgr ?: return
        val cn = component ?: return
        try { pick(m.getActiveSessions(cn)) } catch (_: SecurityException) { }
    }

    private fun isPlaying(c: MediaController) = c.playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
    }

    private fun pick(list: List<MediaController>?) {
        val l = list.orEmpty()
        val chosen = l.firstOrNull { it.packageName == preferredPkg && isPlaying(it) }
            ?: l.firstOrNull { isPlaying(it) }
            ?: l.firstOrNull { it.packageName == preferredPkg }
            ?: l.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) { now = null; return }
        val md = c.metadata
        now = NowPlaying(
            pkg = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString() ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.description?.subtitle?.toString() ?: "",
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = isPlaying(c),
        )
    }

    /** Ο controller της εφαρμογής, ή κανένας αν δεν έχει ανοιχτή συνεδρία. */
    private fun controllerFor(pkg: String?): MediaController? =
        controller?.takeIf { pkg == null || it.packageName == pkg }

    fun playPause(ctx: Context, pkg: String?) {
        val c = controllerFor(pkg)
        if (c != null) {
            if (isPlaying(c)) c.transportControls.pause() else c.transportControls.play()
        } else if (pkg != null) {
            // Η εφαρμογή δεν παίζει ακόμη: «πάτα» το play των ακουστικών μόνο σε αυτήν.
            sendKey(ctx, pkg, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    fun next(ctx: Context, pkg: String?) {
        controllerFor(pkg)?.transportControls?.skipToNext() ?: pkg?.let { sendKey(ctx, it, KeyEvent.KEYCODE_MEDIA_NEXT) }
    }

    fun previous(ctx: Context, pkg: String?) {
        controllerFor(pkg)?.transportControls?.skipToPrevious() ?: pkg?.let { sendKey(ctx, it, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
    }

    private fun sendKey(ctx: Context, pkg: String, code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, code))
            try { ctx.sendBroadcast(i) } catch (_: Exception) { }
        }
    }
}

/** Οι εφαρμογές που πρόσθεσες στο συρτάρι του χάρτη. */
object MyApps {
    data class App(val pkg: String, val label: String)

    private const val PREFS = "my_apps"
    val list = mutableStateListOf<String>()
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pkgs", "") ?: ""
        list.clear()
        list.addAll(s.split(',').filter { it.isNotBlank() && isInstalled(c, it) })
    }

    private fun save(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pkgs", list.joinToString(",")).apply()
    }

    fun add(c: Context, pkg: String) { if (pkg !in list) { list.add(pkg); save(c) } }
    fun remove(c: Context, pkg: String) { list.remove(pkg); save(c) }

    fun isInstalled(c: Context, pkg: String) = c.packageManager.getLaunchIntentForPackage(pkg) != null

    /** Όλες οι εφαρμογές με εικονίδιο στην αρχική οθόνη, εκτός από εμάς. */
    fun installed(c: Context): List<App> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != c.packageName }
            .map { App(it, label(c, it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }

    fun icon(c: Context, pkg: String): Bitmap? = try {
        c.packageManager.getApplicationIcon(pkg).toBitmap(128, 128)
    } catch (_: Exception) { null }

    /** Εφαρμογή μουσικής/ήχου: έχει MediaBrowserService (Spotify, YouTube Music, Deezer, podcasts…). */
    fun isMedia(c: Context, pkg: String): Boolean {
        val i = Intent("android.media.browse.MediaBrowserService").setPackage(pkg)
        return c.packageManager.queryIntentServices(i, 0).isNotEmpty()
    }

    fun open(c: Context, pkg: String) {
        c.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { c.startActivity(it) } catch (_: Exception) { }
        }
    }
}
