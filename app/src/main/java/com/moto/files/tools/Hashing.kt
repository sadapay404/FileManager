package com.moto.files.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Streaming checksums: MD5 / SHA-1 / SHA-256. */
object Hashing {

    enum class Algo(val transformation: String) {
        MD5("MD5"), SHA1("SHA-1"), SHA256("SHA-256")
    }

    suspend fun of(file: File, algo: Algo): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance(algo.transformation)
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
