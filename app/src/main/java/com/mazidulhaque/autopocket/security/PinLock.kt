package com.mazidulhaque.autopocket.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The hidden zone's PIN. Only a slow, salted hash of the PIN is stored, never the PIN itself.
 * Wrong guesses lock the PIN out for longer and longer.
 */
class PinLock(context: Context) {
    sealed interface Result {
        data object Ok : Result
        data class Wrong(val triesBeforeLockout: Int) : Result
        data class LockedOut(val secondsLeft: Long) : Result
    }

    private val prefs = context.getSharedPreferences("hidden_lock", Context.MODE_PRIVATE)

    val isSetUp get() = prefs.contains(HASH)

    var phoneLockAllowed: Boolean
        get() = prefs.getBoolean(PHONE_LOCK, true)
        set(v) { prefs.edit().putBoolean(PHONE_LOCK, v).apply() }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(SALT, salt.b64())
            .putString(HASH, hash(pin, salt, ITERATIONS).b64())
            .putInt(ITER, ITERATIONS)
            .putInt(FAILS, 0)
            .putLong(LOCKED_UNTIL, 0)
            .apply()
    }

    fun check(pin: String): Result {
        lockoutSecondsLeft().let { if (it > 0) return Result.LockedOut(it) }
        val salt = prefs.getString(SALT, null)?.unb64() ?: return Result.Wrong(0)
        val expected = prefs.getString(HASH, null)?.unb64() ?: return Result.Wrong(0)
        val actual = hash(pin, salt, prefs.getInt(ITER, ITERATIONS))
        if (MessageDigest.isEqual(expected, actual)) {
            prefs.edit().putInt(FAILS, 0).putLong(LOCKED_UNTIL, 0).apply()
            return Result.Ok
        }
        val fails = prefs.getInt(FAILS, 0) + 1
        val edit = prefs.edit().putInt(FAILS, fails)
        if (fails >= FREE_TRIES) {
            // 30 s after 5 wrong tries, then doubling each time, up to 1 hour.
            val seconds = minOf(30L shl (fails - FREE_TRIES).coerceAtMost(7), 3600L)
            edit.putLong(LOCKED_UNTIL, System.currentTimeMillis() + seconds * 1000)
            edit.apply()
            return Result.LockedOut(seconds)
        }
        edit.apply()
        return Result.Wrong(FREE_TRIES - fails)
    }

    fun lockoutSecondsLeft(): Long =
        ((prefs.getLong(LOCKED_UNTIL, 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /** Forgets the PIN (after the hidden zone has been erased). */
    fun clear() = prefs.edit().clear().apply()

    private fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256))
            .encoded

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 12
        private const val FREE_TRIES = 5
        private const val ITERATIONS = 120_000
        private const val SALT = "salt"
        private const val HASH = "hash"
        private const val ITER = "iterations"
        private const val FAILS = "fails"
        private const val LOCKED_UNTIL = "locked_until"
        private const val PHONE_LOCK = "phone_lock_allowed"
    }
}
