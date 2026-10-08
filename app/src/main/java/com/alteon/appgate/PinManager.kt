package com.alteon.appgate

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores the parent PIN as a salted PBKDF2 hash and enforces lockouts after wrong guesses. */
class PinManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pin", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains(KEY_HASH)

    fun lockedUntil(): Long = prefs.getLong(KEY_LOCK_UNTIL, 0L)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, encode(salt))
            .putString(KEY_HASH, encode(hash(pin, salt)))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    fun verify(pin: String, now: Long = System.currentTimeMillis()): Result {
        val until = lockedUntil()
        if (now < until) return Result.LockedOut(until)

        val salt = prefs.getString(KEY_SALT, null)?.let(::decode) ?: return Result.Wrong
        val expected = prefs.getString(KEY_HASH, null)?.let(::decode) ?: return Result.Wrong

        if (MessageDigest.isEqual(hash(pin, salt), expected)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
            return Result.Ok
        }

        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val lock = LockoutPolicy.lockoutMillis(fails)
        val lockUntil = if (lock > 0) now + lock else 0L
        prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCK_UNTIL, lockUntil).apply()
        return if (lock > 0) Result.LockedOut(lockUntil) else Result.Wrong
    }

    sealed class Result {
        object Ok : Result()
        object Wrong : Result()
        data class LockedOut(val untilMillis: Long) : Result()
    }

    companion object {
        const val MIN_LENGTH = 4
        private const val KEY_HASH = "hash"
        private const val KEY_SALT = "salt"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val ITERATIONS = 20_000
        private const val KEY_BITS = 256

        // PBKDF2WithHmacSHA1 is available back to API 25; the SHA256 variant needs API 26.
        private fun hash(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        }

        private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)
    }
}
