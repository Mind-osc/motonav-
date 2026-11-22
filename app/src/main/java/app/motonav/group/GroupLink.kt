package app.motonav.group

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motonav.geo.LatLon
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.security.SecureRandom

/** Ένα μέλος της παρέας όπως φαίνεται στον χάρτη. */
data class Member(
    val uid: String,
    val name: String,
    val color: Int,
    val position: LatLon,
    val bearing: Float?,
    val speedKmh: Float?,
    /** Πότε στάλθηκε η θέση (ώρα server, ms). */
    val t: Long,
    val destName: String?,
    val dest: LatLon?,
    val etaMs: Long?,
    val route: List<LatLon>,
)

/** Τι στέλνουμε για την πλοήγηση, αν τρέχει. */
data class NavShare(val dest: LatLon, val destName: String?, val etaMs: Long, val route: List<LatLon>)

/** Κωδικοί και κωδικοποίηση: ξεχωριστά για να ελέγχονται με tests. */
object GroupCodes {
    /** Χωρίς 0/O, 1/I/L: δεν μπερδεύονται όταν τα γράφεις. */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    /** 16 χαρακτήρες (~80 bit): είναι και το κλειδί της κρυπτογράφησης, γι' αυτό μακρύς. */
    const val LENGTH = 16

    fun newCode(random: java.util.Random = SecureRandom()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** «abcd-efgh-…», «motonav://join/ABCD…» → «ABCD…». null αν δεν είναι έγκυρος. */
    fun normalize(input: String): String? {
        val raw = input.trim().substringAfterLast('/').uppercase().filter { it.isLetterOrDigit() }
        return raw.takeIf { it.length == LENGTH && it.all { ch -> ch in ALPHABET } }
    }

    fun pretty(code: String) = code.chunked(4).joinToString("-")
    fun link(code: String) = "motonav://join/$code"

    /** Διαδρομή σε κείμενο «lat,lon;lat,lon», έως max σημεία (για να μένει μικρό). */
    fun encodeRoute(points: List<LatLon>, max: Int = 120): String {
        if (points.isEmpty()) return ""
        val step = maxOf(1, (points.size + max - 1) / max)
        val picked = points.filterIndexed { i, _ -> i % step == 0 } + listOfNotNull(points.lastOrNull()?.takeIf { (points.size - 1) % step != 0 })
        return picked.joinToString(";") { String.format(java.util.Locale.US, "%.5f,%.5f", it.lat, it.lon) }
    }

    fun decodeRoute(s: String?): List<LatLon> = s.orEmpty().split(';').mapNotNull { p ->
        val parts = p.split(',')
        val la = parts.getOrNull(0)?.toDoubleOrNull()
        val lo = parts.getOrNull(1)?.toDoubleOrNull()
        if (la != null && lo != null) LatLon(la, lo) else null
    }

    /** Πότε ξαναστέλνουμε θέση: κάθε 3 s σε κίνηση, κάθε 30 s σταματημένοι. */
    fun shouldSend(sinceLastMs: Long, speedKmh: Float?, forced: Boolean): Boolean =
        forced || sinceLastMs >= (if ((speedKmh ?: 0f) >= 5f) 3_000L else 30_000L)
}

/**
 * Σύνδεση με την παρέα μέσω Firebase Realtime Database.
 * groups/{κωδικός}/members/{uid}: η θέση του καθενός (γράφει μόνο ο ίδιος).
 * groups/{κωδικός}/voice/{id}: σύντομα φωνητικά (walkie-talkie), τα σβήνει ο αποστολέας μετά από 2 λεπτά.
 */
object GroupLink {
    var available by mutableStateOf(false)
        private set
    var code by mutableStateOf<String?>(null)
        private set
    var members by mutableStateOf<List<Member>>(emptyList())
        private set
    var myUid by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var name by mutableStateOf("Αναβάτης")
        private set
    var color by mutableStateOf(0xFFFF8A1F.toInt())
        private set
    var sharing by mutableStateOf(true)
        private set
    /** Πώς μιλάς: true = με τη φωνή (χωρίς κουμπί), false = κρατάς πατημένο το κουμπί. */
    var voxMode by mutableStateOf(true)
        private set
    /** Ευαισθησία VOX 1..5. */
    var voxSensitivity by mutableStateOf(3)
        private set
    /**
     * Walkie-talkie ανοιχτό. Ξεκινάει ΠΑΝΤΑ κλειστό: το μικρόφωνο ακούει μόνο αφού πατήσεις το κουμπί του
     * στον χάρτη, και κλείνει με ένα ακόμα πάτημα ή όταν βγεις από την παρέα.
     */
    var micOn by mutableStateOf(false)
    private var vox: VoxEngine? = null

    fun setVoice(mode: Boolean, sensitivity: Int) {
        voxMode = mode
        voxSensitivity = sensitivity.coerceIn(1, 5)
        prefs.edit().putBoolean("vox", voxMode).putInt("vox_sens", voxSensitivity).apply()
        vox?.sensitivity = voxSensitivity
    }

    /** Στάθμη μικροφώνου 0..1 και αν στέλνει τώρα (για την οθόνη). */
    val voxLevel get() = vox?.level ?: 0f
    val voxTalking get() = vox?.talking == true

    /** Ξεκινάει/σταματάει το VOX ανάλογα με παρέα, ρύθμιση, μικρόφωνο και άδεια. Το καλεί ο χάρτης. */
    fun updateVox(permitted: Boolean) {
        val want = available && code != null && voxMode && micOn && permitted
        if (want) {
            val v = vox ?: VoxEngine(appContext) { bytes, ms -> sendVoice(bytes, ms) }.also { vox = it }
            v.sensitivity = voxSensitivity
            v.start()
        } else {
            vox?.stop()
        }
    }

    /** Όσο παίζει ηχητικό από άλλον, το VOX δεν ακούει. */
    internal fun setPlaying(on: Boolean) {
        vox?.paused = on
    }

    /** Ποιος μιλάει τώρα (για το μήνυμα στην οθόνη). */
    var speaking by mutableStateOf<String?>(null)
    /** Διαφορά ρολογιού κινητού με server, για το «πριν 4′». */
    var serverOffsetMs by mutableLongStateOf(0L)
        private set

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private var group: DatabaseReference? = null
    private var keys: GroupCrypto.Keys? = null
    private var membersListener: ValueEventListener? = null
    private var voiceListener: ChildEventListener? = null
    private var joinedAt = 0L
    private var lastSentAt = 0L
    private var lastCleanupAt = 0L
    private var lastRouteKey: String? = null
    private var player: VoicePlayer? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Θέσεις παλιότερες από αυτό σβήνονται αυτόματα (από όποιο κινητό της παρέας τις βρει). */
    const val STALE_MEMBER_MS = 30 * 60_000L
    const val STALE_VOICE_MS = 2 * 60_000L

    fun now() = System.currentTimeMillis() + serverOffsetMs

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("group", Context.MODE_PRIVATE)
        name = prefs.getString("name", null) ?: "Αναβάτης"
        color = prefs.getInt("color", 0xFFFF8A1F.toInt())
        sharing = prefs.getBoolean("sharing", true)
        voxMode = prefs.getBoolean("vox", true)
        voxSensitivity = prefs.getInt("vox_sens", 3)
        available = runCatching { FirebaseApp.initializeApp(appContext) != null || FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)
        if (!available) return
        player = VoicePlayer(appContext)
        // Σύνδεση με τη βάση ΜΟΝΟ όσο είσαι σε παρέα (όριο Spark: 100 ταυτόχρονες συνδέσεις για όλους τους χρήστες).
        runCatching { FirebaseDatabase.getInstance().goOffline() }
        // παλιός 8ψήφιος κωδικός (πριν την κρυπτογράφηση): δεν ισχύει πια
        prefs.getString("code", null)?.let { c -> if (GroupCodes.normalize(c) != null) connect(c) else prefs.edit().remove("code").apply() }
    }

    private var offsetListener: ValueEventListener? = null

    fun setProfile(newName: String, newColor: Int) {
        name = newName.trim().take(20).ifBlank { "Αναβάτης" }
        color = newColor
        prefs.edit().putString("name", name).putInt("color", color).apply()
        lastSentAt = 0L
    }

    fun changeSharing(on: Boolean) {
        sharing = on
        prefs.edit().putBoolean("sharing", on).apply()
        if (!on) myRef()?.removeValue()
        lastSentAt = 0L
        lastRouteKey = null
    }

    fun create() = join(GroupCodes.newCode())

    /** Επιστρέφει false αν ο κωδικός δεν είναι έγκυρος. */
    fun join(input: String): Boolean {
        val c = GroupCodes.normalize(input) ?: return false
        if (c == code) return true
        leave()
        prefs.edit().putString("code", c).apply()
        connect(c)
        return true
    }

    fun leave() {
        micOn = false
        vox?.stop()
        val pending = myRef()?.removeValue()
        detach()
        prefs.edit().remove("code").apply()
        code = null
        // Αποσύνδεση από τη βάση όταν φύγει το «σβήσε τη θέση μου» (ή αμέσως αν δεν υπάρχει).
        val off = { if (code == null) runCatching { FirebaseDatabase.getInstance().goOffline() } }
        if (pending != null) pending.addOnCompleteListener { off() } else off()
        keys = null
        members = emptyList()
    }

    private fun detach() {
        group?.let { g ->
            membersListener?.let { g.child("m").removeEventListener(it) }
            voiceListener?.let { g.child("v").removeEventListener(it) }
        }
        offsetListener?.let { runCatching { FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset").removeEventListener(it) } }
        offsetListener = null
        group = null
        membersListener = null
        voiceListener = null
    }

    private fun myRef(): DatabaseReference? {
        val uid = myUid ?: return null
        return group?.child("m")?.child(uid)
    }

    private fun connect(c: String) {
        if (!available) return
        code = c
        error = null
        // Το κλειδί βγαίνει αργά (επίτηδες), οπότε σε άλλο νήμα.
        Thread {
            val k = runCatching { GroupCrypto.derive(c) }.getOrNull()
            main.post {
                if (code != c) return@post
                if (k == null) { error = "Κρυπτογράφηση: δεν ξεκίνησε"; return@post }
                keys = k
                val auth = FirebaseAuth.getInstance()
                val go = { uid: String -> myUid = uid; attach(k) }
                val user = auth.currentUser
                if (user != null) go(user.uid)
                else auth.signInAnonymously()
                    .addOnSuccessListener { r -> r.user?.uid?.let(go) }
                    .addOnFailureListener { e -> error = "Σύνδεση: ${e.message}" }
            }
        }.start()
    }

    private fun attach(k: GroupCrypto.Keys) {
        detach()
        val db = FirebaseDatabase.getInstance()
        db.goOnline()
        offsetListener = db.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { serverOffsetMs = (s.getValue(Double::class.java) ?: 0.0).toLong() }
            override fun onCancelled(e: DatabaseError) = Unit
        })
        val g = db.getReference("g").child(k.path)
        group = g
        joinedAt = now()
        lastSentAt = 0L
        lastRouteKey = null
        membersListener = g.child("m").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                members = s.children.mapNotNull { parse(k, it) }
                error = null
            }

            override fun onCancelled(e: DatabaseError) { error = "Παρέα: ${e.message}" }
        })
        voiceListener = g.child("v").orderByChild("t").startAt((joinedAt - 5_000).toDouble())
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(s: DataSnapshot, prev: String?) {
                    val from = s.child("from").getValue(String::class.java) ?: return
                    if (from == myUid) return
                    val audio = GroupCrypto.decrypt(k, s.child("d").getValue(String::class.java)) ?: return
                    val who = GroupCrypto.decryptText(k, s.child("n").getValue(String::class.java)) ?: "Κάποιος"
                    runCatching { player?.play(audio, who) }
                }

                override fun onChildChanged(s: DataSnapshot, prev: String?) = Unit
                override fun onChildRemoved(s: DataSnapshot) = Unit
                override fun onChildMoved(s: DataSnapshot, prev: String?) = Unit
                override fun onCancelled(e: DatabaseError) = Unit
            })
        cleanup()
    }

    private fun parse(k: GroupCrypto.Keys, s: DataSnapshot): Member? {
        val uid = s.key ?: return null
        val t = s.child("t").getValue(Long::class.java) ?: 0L
        if (now() - t > STALE_MEMBER_MS) return null
        val json = GroupCrypto.decryptText(k, s.child("p").getValue(String::class.java)) ?: return null
        val o = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return null
        val route = GroupCrypto.decryptText(k, s.child("r").getValue(String::class.java))
        fun d(key: String) = if (o.has(key) && !o.isNull(key)) o.getDouble(key) else null
        val dLat = d("dLat")
        val dLon = d("dLon")
        return Member(
            uid = uid,
            name = o.optString("name", "Αναβάτης"),
            color = o.optLong("color", 0xFFFF8A1F).toInt(),
            position = LatLon(d("lat") ?: return null, d("lon") ?: return null),
            bearing = d("brg")?.toFloat(),
            speedKmh = d("kmh")?.toFloat(),
            t = t,
            destName = if (o.has("dName") && !o.isNull("dName")) o.getString("dName") else null,
            dest = if (dLat != null && dLon != null) LatLon(dLat, dLon) else null,
            etaMs = if (o.has("eta") && !o.isNull("eta")) o.getLong("eta") else null,
            route = GroupCodes.decodeRoute(route),
        )
    }

    /** Από την οθόνη του χάρτη σε κάθε στίγμα GPS. Στέλνει μόνο όσο συχνά χρειάζεται. */
    fun publish(loc: Location, nav: NavShare?) {
        val ref = myRef() ?: return
        val k = keys ?: return
        if (!sharing) return
        val routeKey = nav?.let { "${it.dest.lat},${it.dest.lon},${it.route.size}" }
        val forced = routeKey != lastRouteKey
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else null
        val since = SystemClock.elapsedRealtime() - lastSentAt
        if (!GroupCodes.shouldSend(since, kmh, forced)) return
        lastSentAt = SystemClock.elapsedRealtime()
        val o = org.json.JSONObject()
            .put("name", name)
            .put("color", color.toLong() and 0xFFFFFFFFL)
            .put("lat", loc.latitude)
            .put("lon", loc.longitude)
        if (loc.hasBearing()) o.put("brg", loc.bearing.toDouble())
        kmh?.let { o.put("kmh", it.toDouble()) }
        nav?.let { n ->
            o.put("eta", n.etaMs).put("dLat", n.dest.lat).put("dLon", n.dest.lon)
            n.destName?.let { o.put("dName", it) }
        }
        val v = HashMap<String, Any?>()
        v["p"] = GroupCrypto.encryptText(k, o.toString())
        v["t"] = ServerValue.TIMESTAMP
        if (forced) {
            v["r"] = nav?.let { GroupCrypto.encryptText(k, GroupCodes.encodeRoute(it.route)) }
            lastRouteKey = routeKey
        }
        ref.updateChildren(v)
        if (SystemClock.elapsedRealtime() - lastCleanupAt > 5 * 60_000) cleanup()
    }

    /** Walkie-talkie: στέλνει ένα σύντομο ηχητικό (m4a), κρυπτογραφημένο. */
    fun sendVoice(bytes: ByteArray, durationMs: Long) {
        val g = group ?: return
        val uid = myUid ?: return
        val k = keys ?: return
        if (bytes.isEmpty() || bytes.size > 140_000) return
        g.child("v").push().setValue(
            mapOf(
                "from" to uid,
                "t" to ServerValue.TIMESTAMP,
                "n" to GroupCrypto.encryptText(k, name),
                "d" to GroupCrypto.encrypt(k, bytes),
            ),
        )
    }

    /**
     * Σβήνει ό,τι έμεινε: θέσεις παλιότερες από 30 λεπτά (π.χ. κάποιος έκλεισε την εφαρμογή)
     * και φωνητικά παλιότερα από 2 λεπτά. Το κάνει όποιο κινητό της παρέας είναι ανοιχτό.
     */
    private fun cleanup() {
        val g = group ?: return
        lastCleanupAt = SystemClock.elapsedRealtime()
        val n = now()
        g.child("m").get().addOnSuccessListener { s ->
            s.children.forEach { c ->
                val t = c.child("t").getValue(Long::class.java) ?: 0L
                if (n - t > STALE_MEMBER_MS) c.ref.removeValue()
            }
        }
        g.child("v").orderByChild("t").endAt((n - STALE_VOICE_MS).toDouble()).get().addOnSuccessListener { s ->
            s.children.forEach { it.ref.removeValue() }
        }
    }
}
