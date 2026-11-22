package app.motonav.group

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Κρυπτογράφηση από άκρη σε άκρη για την παρέα.
 * Από τον κωδικό της παρέας (που δεν φεύγει ποτέ από τα κινητά) βγαίνουν δύο πράγματα με PBKDF2:
 *  - το «όνομα» της παρέας στη βάση (32 hex), για να μη φαίνεται ο κωδικός,
 *  - το κλειδί AES-256, με το οποίο κλειδώνονται θέσεις, διαδρομές, ονόματα και φωνητικά (AES-GCM).
 * Η Google (Firebase) βλέπει μόνο κρυπτογραφημένα κομμάτια, ώρες και μεγέθη.
 */
object GroupCrypto {
    private const val ITERATIONS = 150_000
    private val random = SecureRandom()

    class Keys(val path: String, internal val key: SecretKeySpec)

    private fun pbkdf2(code: String, salt: String, bytes: Int): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt.toByteArray(Charsets.UTF_8), ITERATIONS, bytes * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Αργό επίτηδες (~0,5 s): γίνεται μία φορά όταν μπαίνεις στην παρέα. */
    fun derive(code: String): Keys {
        val path = pbkdf2(code, "motonav/v1/path", 16).joinToString("") { "%02x".format(it) }
        val key = SecretKeySpec(pbkdf2(code, "motonav/v1/key", 32), "AES")
        return Keys(path, key)
    }

    fun encrypt(keys: Keys, plain: ByteArray): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keys.key, GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plain))
    }

    /** null αν δεν είναι από την παρέα (λάθος κλειδί ή πειραγμένο). */
    fun decrypt(keys: Keys, data: String?): ByteArray? = runCatching {
        val all = Base64.getDecoder().decode(data ?: return null)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keys.key, GCMParameterSpec(128, all, 0, 12))
        c.doFinal(all, 12, all.size - 12)
    }.getOrNull()

    fun encryptText(keys: Keys, s: String) = encrypt(keys, s.toByteArray(Charsets.UTF_8))
    fun decryptText(keys: Keys, s: String?) = decrypt(keys, s)?.toString(Charsets.UTF_8)
}
