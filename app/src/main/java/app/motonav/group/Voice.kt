package app.motonav.group

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Παίζει τα ηχητικά της παρέας ένα-ένα, χαμηλώνοντας για λίγο τη μουσική. */
class VoicePlayer(private val context: Context) {
    private val queue = ArrayDeque<Pair<ByteArray, String>>()
    private var playing = false
    private val am = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()

    fun play(bytes: ByteArray, from: String) {
        queue.addLast(bytes to from)
        if (!playing) next()
    }

    private fun next() {
        val (bytes, from) = queue.removeFirstOrNull() ?: run {
            playing = false
            GroupLink.speaking = null
            GroupLink.setPlaying(false)
            am?.abandonAudioFocusRequest(focus)
            return
        }
        playing = true
        GroupLink.speaking = from
        GroupLink.setPlaying(true)
        val f = File(context.cacheDir, "voice_in_${SystemClock.elapsedRealtime()}.m4a")
        f.writeBytes(bytes)
        am?.requestAudioFocus(focus)
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(f.path)
            mp.setOnCompletionListener { it.release(); f.delete(); next() }
            mp.setOnErrorListener { p, _, _ -> p.release(); f.delete(); next(); true }
            mp.prepare()
            mp.start()
        } catch (_: Exception) {
            mp.release()
            f.delete()
            next()
        }
    }
}

/** Ηχογράφηση όσο κρατάς πατημένο το κουμπί (έως 20 s). */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var startedAt = 0L
    private val file get() = File(context.cacheDir, "voice_out.m4a")

    fun start(): Boolean {
        stopQuietly()
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setMaxDuration(20_000)
            r.setOutputFile(file.path)
            r.prepare()
            r.start()
            rec = r
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (_: Exception) {
            stopQuietly()
            false
        }
    }

    /** Τα bytes και η διάρκεια, ή null αν ήταν πολύ σύντομο (κάτω από μισό δευτερόλεπτο). */
    fun stop(): Pair<ByteArray, Long>? {
        val r = rec ?: return null
        rec = null
        val ms = SystemClock.elapsedRealtime() - startedAt
        return try {
            r.stop()
            r.release()
            if (ms < 500) null else file.readBytes() to ms
        } catch (_: Exception) {
            r.release()
            null
        }
    }

    private fun stopQuietly() {
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null
    }
}
