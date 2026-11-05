package com.moto.files.security

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM vault encryption. The PBKDF2 password is the user's vault credential
 * (never the stored salt), all operations stream in 64 KB chunks.
 */
object Crypto {

    private const val ITER = 120_000
    private const val TAG = 128

    private fun key(secret: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITER, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun encryptFile(src: File, dst: File, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret, salt), GCMParameterSpec(TAG, iv))
        dst.outputStream().use { out ->
            out.write(salt); out.write(iv)
            src.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } != -1) out.write(cipher.update(buf, 0, n))
                out.write(cipher.doFinal())
            }
        }
    }

    /** Streaming decrypt; GCM tag is verified when the stream is read to EOF. */
    fun decryptStream(src: File, secret: String): InputStream {
        val input = src.inputStream()
        val salt = ByteArray(16); val iv = ByteArray(12)
        input.read(salt); input.read(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(secret, salt), GCMParameterSpec(TAG, iv))
        return CipherInputStream(input, cipher)
    }

    /** Streaming encrypt into a caller-owned stream; does not close [out]. */
    fun encryptTo(src: File, out: OutputStream, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret, salt), GCMParameterSpec(TAG, iv))
        out.write(salt); out.write(iv)
        src.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) out.write(cipher.update(buf, 0, n))
            out.write(cipher.doFinal())
        }
        out.flush()
    }
}
