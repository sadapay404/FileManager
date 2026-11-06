package com.moto.files.data.security

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultItem(val id: String, val name: String, val mime: String)

/** AES-256-GCM; IV is unique per file. Metadata names are app-private but not encrypted. */
class VaultRepository(private val context: Context) {
    private val directory = File(context.filesDir, "vault").apply { mkdirs() }
    private val index = File(directory, "index.json")
    private val random = SecureRandom()
    @Synchronized fun items(): List<VaultItem> = read().map {
        VaultItem(it.getString("id"), it.getString("name"), it.getString("mime"))
    }
    private fun read(): List<JSONObject> {
        if (!index.exists()) return emptyList()
        val array = JSONArray(index.readText())
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun save(items: List<JSONObject>) {
        val temp = File(directory, "index.tmp")
        temp.writeText(JSONArray(items).toString())
        check(temp.renameTo(index)) { "Could not save Vault index" }
    }
    @Synchronized fun importFile(uri: Uri, name: String, mime: String, key: ByteArray): Result<Unit> = runCatching {
        require(key.size == 32)
        val id = UUID.randomUUID().toString()
        val file = File(directory, id)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { raw -> raw.write(iv); CipherOutputStream(raw, cipher).use { input.copyTo(it) } }
            } ?: error("Cannot read selected file")
            val updated = read() + JSONObject().put("id", id).put("name", name).put("mime", mime)
            save(updated)
        } catch (failure: Exception) { file.delete(); throw failure }
    }
    /** Image previews are decrypted in memory only; never written as plaintext cache files. */
    @Synchronized fun imageBytes(item: VaultItem, key: ByteArray): ByteArray {
        val file = File(directory, item.id)
        require(file.length() <= 32L * 1024 * 1024) { "Image is too large for in-memory preview" }
        file.inputStream().use { raw ->
            val iv = ByteArray(12)
            java.io.DataInputStream(raw).readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            return CipherInputStream(raw, cipher).use { it.readBytes() }
        }
    }
    /** Explicit export to a user-selected document, with authenticated decryption. */
    @Synchronized fun exportFile(item: VaultItem, key: ByteArray, target: Uri): Result<Unit> = runCatching {
        val source = File(directory, item.id)
        source.inputStream().use { raw ->
            val iv = ByteArray(12)
            java.io.DataInputStream(raw).readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            CipherInputStream(raw, cipher).use { decrypted ->
                context.contentResolver.openOutputStream(target, "w")?.use { decrypted.copyTo(it) }
                    ?: error("Cannot write destination")
            }
        }
    }.onFailure { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) } }
    @Synchronized fun remove(item: VaultItem): Result<Unit> = runCatching {
        check(File(directory, item.id).delete()) { "Could not remove encrypted file" }
        save(read().filterNot { it.getString("id") == item.id })
    }
}
