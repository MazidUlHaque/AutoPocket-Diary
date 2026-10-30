package com.mazidulhaque.autopocket.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mazidulhaque.autopocket.BuildConfig
import java.security.MessageDigest

/** Checks that this copy of the app was signed with the developer's official key. */
object AppIntegrity {
    enum class Status { OFFICIAL, UNOFFICIAL, DEVELOPMENT_BUILD }

    fun status(context: Context): Status {
        val expected = BuildConfig.SIGNING_CERT_SHA256
        if (expected.isBlank()) return Status.DEVELOPMENT_BUILD
        return if (certificateSha256(context).equals(expected, ignoreCase = true)) Status.OFFICIAL else Status.UNOFFICIAL
    }

    /** SHA-256 fingerprint of the signing certificate, e.g. "AB:CD:...". */
    @SuppressLint("PackageManagerGetSignatures")
    fun certificateSha256(context: Context): String? {
        val pm = context.packageManager
        val name = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signatures?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }
}
