package com.moto.files.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.moto.files.model.LockKind
import com.moto.files.model.LockSettings
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * In-app credential store. Nothing here touches the OS keystore or the system lock screen:
 * credentials are salted SHA-256 hashes in EncryptedSharedPreferences, and the UI is ours.
 * Includes brute-force lockout after [MAX_FAILS] wrong attempts.
 */
class LockManager private constructor(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "locks",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Volatile
    private var unlocked = false

    val settings: LockSettings
        get() = LockSettings(
            kind = LockKind.valueOf(prefs.getString("kind", LockKind.NONE.name)!!),
            hash = prefs.getString("hash", null),
            salt = prefs.getString("salt", null),
            biometric = prefs.getBoolean("bio", false),
            vaultSeparate = prefs.getBoolean("vsep", false),
            vaultHash = prefs.getString("vhash", null),
            vaultSalt = prefs.getString("vsalt", null)
        )

    val lockEnabled: Boolean get() = settings.kind != LockKind.NONE
    val isUnlocked: Boolean get() = unlocked

    fun markUnlocked() { unlocked = true; resetFails() }
    fun lock() { unlocked = false }

    // ---- brute-force protection ----

    fun lockoutLeftMs(): Long =
        (prefs.getLong("lockout_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun recordFail() {
        val fails = prefs.getInt("fails", 0) + 1
        val e = prefs.edit().putInt("fails", fails)
        if (fails >= MAX_FAILS) {
            e.putInt("fails", 0)
                .putLong("lockout_until", System.currentTimeMillis() + LOCKOUT_MS)
        }
        e.apply()
    }

    private fun resetFails() =
        prefs.edit().putInt("fails", 0).putLong("lockout_until", 0L).apply()

    // ---- credentials ----

    fun setCredential(kind: LockKind, secret: String) {
        val salt = salt()
        prefs.edit()
            .putString("kind", kind.name)
            .putString("salt", salt)
            .putString("hash", digest(salt, secret))
            .apply()
        resetFails()
    }

    fun check(secret: String, s: LockSettings = settings): Boolean {
        val salt = s.salt ?: return false
        val expected = s.hash ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), digest(salt, secret).toByteArray())
    }

    /** Lockout-aware check for the app lock UI. */
    fun authenticate(secret: String): Boolean {
        if (lockoutLeftMs() > 0) return false
        val ok = check(secret)
        if (ok) resetFails() else recordFail()
        return ok
    }

    fun setBiometric(on: Boolean) = prefs.edit().putBoolean("bio", on).apply()

    /**
     * Vault credential. When [secret] is null the existing vault credential is kept
     * (so flipping the "separate vault PIN" switch never bricks the vault).
     */
    fun setVaultCredential(separate: Boolean, secret: String? = null) {
        val e = prefs.edit().putBoolean("vsep", separate)
        if (secret != null) {
            val salt = salt()
            e.putString("vsalt", salt).putString("vhash", digest(salt, secret))
        }
        e.apply()
    }

    val hasVaultCredential: Boolean
        get() = !settings.vaultSeparate || settings.vaultHash != null

    fun checkVault(secret: String): Boolean {
        if (lockoutLeftMs() > 0) return false
        val s = settings
        val ok = if (s.vaultSeparate) {
            s.vaultSalt != null && s.vaultHash != null &&
                MessageDigest.isEqual(
                    s.vaultHash.toByteArray(), digest(s.vaultSalt, secret).toByteArray())
        } else check(secret, s)
        if (ok) resetFails() else recordFail()
        return ok
    }

    private fun salt(): String =
        Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    private fun digest(salt: String, secret: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray())
        return Base64.getEncoder().encodeToString(md.digest(secret.toByteArray()))
    }

    companion object {
        const val MAX_FAILS = 5
        const val LOCKOUT_MS = 30_000L

        @Volatile
        private var instance: LockManager? = null
        fun get(context: Context): LockManager =
            instance ?: synchronized(this) {
                instance ?: LockManager(context.applicationContext).also { instance = it }
            }
    }
}
