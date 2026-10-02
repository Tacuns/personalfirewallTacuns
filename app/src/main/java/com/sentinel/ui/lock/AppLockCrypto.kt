package com.sentinel.ui.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password verification for App Lock.
 *
 * The password itself is NEVER stored. Only a random per-user salt and a PBKDF2
 * derivation of the password are kept, and unlocking re-derives and compares.
 * There is nothing to decrypt, so no key management and no encrypted-storage
 * library is needed — androidx.security-crypto is fully deprecated as of 1.1.0.
 *
 * PBKDF2-HMAC-SHA256 with a unique 16-byte salt, per the OWASP Password Storage
 * Cheat Sheet. Comparison uses MessageDigest.isEqual, which is constant-time and
 * therefore not vulnerable to timing analysis.
 */
object AppLockCrypto {

    // OWASP's current recommendation for PBKDF2-HMAC-SHA256. Deliberately slow:
    // it costs the user well under a second once, and costs an attacker dearly
    // per guess. Always call verify()/create() off the main thread.
    private const val ITERATIONS  = 600_000
    private const val KEY_BITS    = 256
    private const val SALT_BYTES  = 16
    private const val ALGORITHM   = "PBKDF2WithHmacSHA256"

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    // Recovery-code alphabet with 0/O/1/I/L removed, so a hand-copied code cannot be
    // mistyped into a different valid-looking one.
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val CODE_GROUPS   = 4
    private const val CODE_GROUP_LEN = 4

    /** Generates a one-time recovery code, e.g. "H7KP-29XM-BQRT-4FDW". */
    fun generateRecoveryCode(): String {
        val rnd = SecureRandom()
        return (0 until CODE_GROUPS).joinToString("-") {
            (0 until CODE_GROUP_LEN)
                .map { CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)] }
                .joinToString("")
        }
    }

    /**
     * Username and password are folded into ONE hash, so both are required and neither
     * is recoverable from storage. A wrong username and a wrong password are therefore
     * indistinguishable to an attacker — the app can only say "wrong credentials".
     * The NUL separator prevents "ab"+"c" colliding with "a"+"bc".
     */
    fun combine(username: String, password: String): String =
        username.trim().lowercase() + "\u0000" + password

    /** Security answers and recovery codes are matched case- and spacing-insensitively. */
    fun normalizeAnswer(answer: String): String =
        answer.trim().lowercase().replace(Regex("\\s+"), " ")

    fun normalizeCode(code: String): String =
        code.trim().uppercase().replace("-", "").replace(" ", "")

    /** Every rule the password must satisfy. Returns true only if all pass. */
    fun isValid(password: String): Boolean =
        password.length >= MIN_LENGTH &&
        password.length <= MAX_LENGTH &&
        password.any { it.isUpperCase() } &&
        password.any { it.isLowerCase() } &&
        password.any { it.isDigit() } &&
        password.any { !it.isLetterOrDigit() && !it.isWhitespace() }

    /** Derives a fresh salted record for a new password. Format: "<saltB64>:<hashB64>". */
    fun create(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        return "${salt.b64()}:${hash.b64()}"
    }

    /** True when [password] reproduces the stored record. Malformed records return false. */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return try {
            val salt     = Base64.getDecoder().decode(parts[0])
            val expected = Base64.getDecoder().decode(parts[1])
            MessageDigest.isEqual(derive(password, salt), expected)
        } catch (e: Exception) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            // Clears the char[] copy PBEKeySpec holds internally.
            spec.clearPassword()
        }
    }

    // java.util.Base64 (API 26+, minSdk is 29) rather than android.util.Base64 — the
    // Android class is unavailable in JVM unit tests, which would make this untestable.
    private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)
}
