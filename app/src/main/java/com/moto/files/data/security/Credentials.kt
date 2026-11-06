package com.moto.files.data.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Credential verifier stores only salted, stretched hashes. Never stores PINs/passwords. */
class Credentials(context: Context) {
    private val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    fun configured(vault: Boolean = false) = prefs.contains(if (vault) "vault_hash" else "app_hash")
    fun type(vault: Boolean = false): String = prefs.getString(if (vault) "vault_type" else "app_type", "PIN") ?: "PIN"
    fun separateVault() = prefs.getBoolean("vault_separate", false)
    fun biometricEnabled() = prefs.getBoolean("biometric", false)
    fun setBiometric(enabled: Boolean) { prefs.edit().putBoolean("biometric", enabled).apply() }

    fun set(secret: String, type: String, vault: Boolean = false) {
        require(type in listOf("PIN", "Password", "Pattern"))
        require(if (type == "Password") secret.length >= 8 else secret.length >= 6) { "Use at least ${if (type == "Password") 8 else 6} characters or points" }
        val prefix = if (vault) "vault" else "app"
        val salt = ByteArray(32).also(random::nextBytes)
        val hash = derive(secret, salt)
        prefs.edit().putString("${prefix}_salt", encode(salt)).putString("${prefix}_hash", encode(hash))
            .putString("${prefix}_type", type).apply()
    }

    fun useSeparateVault(value: Boolean) {
        require(!value || configured(true)) { "Set up a separate Vault credential first" }
        prefs.edit().putBoolean("vault_separate", value).apply()
    }

    fun verify(secret: String, vault: Boolean = false): Boolean {
        val prefix = if (vault) "vault" else "app"
        val salt = prefs.getString("${prefix}_salt", null) ?: return false
        val expected = prefs.getString("${prefix}_hash", null) ?: return false
        return try { MessageDigest.isEqual(derive(secret, decode(salt)), decode(expected)) } catch (_: Exception) { false }
    }

    /** A different context string prevents the verifier and Vault encryption keys being interchangeable. */
    fun vaultKey(secret: String, vault: Boolean): ByteArray {
        require(verify(secret, vault)) { "Incorrect credential" }
        val salt = decode(prefs.getString(if (vault) "vault_salt" else "app_salt", null)!!)
        return derive("moto-vault-v1:$secret", salt)
    }

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
