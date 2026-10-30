package com.mazidulhaque.autopocket.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file format: "APDBAK01" + salt (16) + iv (12) + AES-256-GCM(gzip(data)).
 * The key comes from the user's backup password (PBKDF2-SHA256), so the file is useless without it,
 * and GCM means a changed or damaged file is rejected instead of half-read.
 */
object BackupCrypto {
    class NotABackup : Exception("This is not an AutoPocket Diary backup file.")
    class WrongPassword : Exception("Wrong password, or the file is damaged.")

    const val MIN_PASSWORD = 8
    const val MAX_FILE_BYTES = 50 * 1024 * 1024

    private val MAGIC = "APDBAK01".toByteArray()
    private const val ITERATIONS = 210_000
    // Refuse to unpack more than this (protects against "zip bomb" files).
    private const val MAX_PLAIN_BYTES = 200 * 1024 * 1024

    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(zipped)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val header = MAGIC.size + 16 + 12
        if (file.size < header + 16 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackup()
        val salt = file.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = file.copyOfRange(MAGIC.size + 16, header)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val zipped = try {
            cipher.doFinal(file, header, file.size - header)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(zipped)).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_PLAIN_BYTES) throw NotABackup()
            }
        }
        return out.toByteArray()
    }

    private fun key(password: CharArray, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded,
        "AES",
    )
}
