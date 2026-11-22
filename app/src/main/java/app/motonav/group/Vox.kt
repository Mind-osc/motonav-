package app.motonav.group

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Πότε μιλάς (χωρίς πράξεις ήχου: μόνο αποφάσεις, για να ελέγχεται με tests).
 * Κομμάτια των 20 ms. Αρχή: 4 συνεχόμενα (80 ms) πάνω από το κατώφλι. Τέλος: 1,2 s ησυχία ή 15 s συνολικά.
 */
class VoxDetector(var thresholdDb: Double) {
    enum class Event { NONE, START, END }

    var talking = false
        private set
    private var loud = 0
    private var quiet = 0
    private var frames = 0

    fun feed(levelDb: Double): Event {
        val above = levelDb >= thresholdDb
        if (!talking) {
            loud = if (above) loud + 1 else 0
            if (loud >= START_FRAMES) {
                talking = true
                quiet = 0
                frames = loud
                return Event.START
            }
            return Event.NONE
        }
        frames++
        quiet = if (above) 0 else quiet + 1
        if (quiet >= END_FRAMES || frames >= MAX_FRAMES) {
            talking = false
            loud = 0
            return Event.END
        }
        return Event.NONE
    }

    companion object {
        const val FRAME_MS = 20
        const val START_FRAMES = 4
        const val END_FRAMES = 60          // 1,2 s
        const val MAX_FRAMES = 750         // 15 s
        const val PREROLL_FRAMES = 15      // 300 ms πριν την αρχή, για να μη χάνεται η πρώτη συλλαβή

        /** Ευαισθησία 1 (μόνο δυνατά) … 5 (και ψιθυριστά) → κατώφλι σε dBFS. */
        fun thresholdFor(sensitivity: Int): Double = -22.0 - (sensitivity.coerceIn(1, 5) - 1) * 6.0

        fun levelDb(frame: ShortArray, n: Int): Double {
            if (n <= 0) return -100.0
            var sum = 0.0
            for (i in 0 until n) { val v = frame[i] / 32768.0; sum += v * v }
            val rms = sqrt(sum / n)
            return if (rms <= 1e-6) -100.0 else 20 * log10(rms)
        }
    }
}

/**
 * Walkie-talkie χωρίς χέρια: το μικρόφωνο ακούει, και όταν μιλάς στέλνει το κομμάτι (m4a) στην παρέα.
 * Δεν ακούει όσο παίζει ηχητικό από άλλον, για να μην το ξαναστέλνει.
 */
class VoxEngine(private val context: Context, private val onClip: (ByteArray, Long) -> Unit) {
    @Volatile var sensitivity = 3
    @Volatile var paused = false
    /** Στάθμη τώρα 0..1 για το μετρητή στην οθόνη. */
    @Volatile var level = 0f
        private set
    @Volatile var talking = false
        private set
    private var thread: Thread? = null
    @Volatile private var running = false

    val isRunning get() = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "vox").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        level = 0f
        talking = false
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val rate = 16_000
        val frameN = rate * VoxDetector.FRAME_MS / 1000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, frameN * 8))
        } catch (_: Exception) {
            running = false
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            running = false
            return
        }
        val det = VoxDetector(VoxDetector.thresholdFor(sensitivity))
        val preroll = ArrayDeque<ShortArray>()
        val clip = ArrayList<ShortArray>()
        val frame = ShortArray(frameN)
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(frame, 0, frameN)
                if (n <= 0) continue
                val copy = frame.copyOf(n)
                val db = VoxDetector.levelDb(copy, n)
                level = ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
                det.thresholdDb = VoxDetector.thresholdFor(sensitivity)
                if (paused) {
                    // όσο παίζει ηχητικό από άλλον: τίποτα (και ό,τι μισό, πετιέται)
                    if (det.talking) det.feed(-100.0)
                    clip.clear(); preroll.clear(); talking = false
                    continue
                }
                when (det.feed(db)) {
                    VoxDetector.Event.START -> {
                        talking = true
                        clip.clear()
                        clip.addAll(preroll)
                        clip.add(copy)
                    }
                    VoxDetector.Event.END -> {
                        talking = false
                        clip.add(copy)
                        val pcm = join(clip)
                        clip.clear()
                        // λιγότερο από 0,6 s ομιλίας (χωρίς την ησυχία στο τέλος): μάλλον θόρυβος
                        val speechMs = pcm.size * 1000L / rate - VoxDetector.END_FRAMES * VoxDetector.FRAME_MS
                        if (speechMs >= 600) {
                            runCatching {
                                val f = File(context.cacheDir, "vox_out.m4a")
                                AacEncoder.encode(pcm, rate, f)
                                onClip(f.readBytes(), pcm.size * 1000L / rate)
                            }
                        }
                    }
                    VoxDetector.Event.NONE -> if (det.talking) clip.add(copy)
                }
                preroll.addLast(copy)
                while (preroll.size > VoxDetector.PREROLL_FRAMES) preroll.removeFirst()
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            level = 0f
            talking = false
        }
    }

    private fun join(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var o = 0
        parts.forEach { p -> p.copyInto(out, o); o += p.size }
        return out
    }
}

/** PCM 16-bit mono → AAC σε m4a, με τον κωδικοποιητή του κινητού. */
object AacEncoder {
    fun encode(pcm: ShortArray, rate: Int, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 24_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { it.asShortBuffer().put(pcm) }.array()
        var offset = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        var guard = 0
        try {
            while (guard++ < 20_000) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = minOf(buf.remaining(), bytes.size - offset)
                        val pts = (offset / 2).toLong() * 1_000_000L / rate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(bytes, offset, n)
                            codec.queueInputBuffer(i, 0, n, pts, 0)
                            offset += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        muxer.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}
