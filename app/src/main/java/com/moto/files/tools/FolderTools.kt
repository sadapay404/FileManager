package com.moto.files.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.moto.files.data.files.TreeEntry
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Tools operate solely on document URIs within a user-granted SAF tree. */
class FolderTools(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun hash(uri: Uri, algorithm: String): String {
        require(algorithm == "MD5" || algorithm == "SHA-256")
        val digest = MessageDigest.getInstance(algorithm)
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Cannot read file")
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun duplicates(files: List<TreeEntry>): List<List<TreeEntry>> = files.filter { !it.isDirectory && it.length > 0 }
        .groupBy { it.length }.values.filter { it.size > 1 }
        .flatMap { sameSize -> sameSize.groupBy { hash(it.uri, "SHA-256") }.values.filter { it.size > 1 } }

    private fun parent(folder: Uri): Uri = if (DocumentsContract.isDocumentUri(context, folder)) folder else
        DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))

    private fun uniqueName(folder: Uri, name: String): String {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder,
            if (DocumentsContract.isDocumentUri(context, folder)) DocumentsContract.getDocumentId(folder) else DocumentsContract.getTreeDocumentId(folder))
        val names = mutableSetOf<String>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        if (name !in names) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if (base == name) "" else ".${name.substringAfterLast('.')}"
        var index = 1
        while ("$base ($index)$ext" in names) index++
        return "$base ($index)$ext"
    }

    fun zip(files: List<TreeEntry>, folder: Uri, title: String = "Archive.zip"): Uri {
        require(files.isNotEmpty() && files.all { !it.isDirectory }) { "Choose files, not folders" }
        val target = DocumentsContract.createDocument(resolver, parent(folder), "application/zip", uniqueName(folder, title))
            ?: error("Cannot create ZIP")
        try {
            resolver.openOutputStream(target, "w")?.use { output -> ZipOutputStream(output).use { zip ->
                files.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name.substringAfterLast('/')))
                    resolver.openInputStream(file.uri)?.use { it.copyTo(zip) } ?: error("Cannot read ${file.name}")
                    zip.closeEntry()
                }
            } } ?: error("Cannot write ZIP")
            return target
        } catch (failure: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, target) }; throw failure }
    }

    /** Flat extraction only; rejects unsafe paths instead of following ZIP paths or overwriting. */
    fun unzip(archive: TreeEntry, folder: Uri): Int {
        require(archive.name.endsWith(".zip", true))
        var count = 0
        var totalBytes = 0L
        resolver.openInputStream(archive.uri)?.use { source -> ZipInputStream(source).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) { zip.closeEntry(); continue }
                val name = entry.name
                require(name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != "..") {
                    "ZIP contains nested or unsafe paths; nothing further was extracted"
                }
                require(count < 1000) { "Archive contains too many files" }
                val target = DocumentsContract.createDocument(resolver, parent(folder), "application/octet-stream", uniqueName(folder, name))
                    ?: error("Cannot create $name")
                try {
                    resolver.openOutputStream(target, "w")?.use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = 0L
                        while (true) {
                            val length = zip.read(buffer)
                            if (length < 0) break
                            bytes += length
                            totalBytes += length
                            require(totalBytes <= 1024L * 1024 * 1024) { "Archive exceeds 1 GB expanded" }
                            require(bytes <= 512L * 1024 * 1024) { "Archive item exceeds 512 MB" }
                            output.write(buffer, 0, length)
                        }
                    } ?: error("Cannot write $name")
                    count++
                } catch (failure: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, target) }; throw failure }
                zip.closeEntry()
            }
        } } ?: error("Cannot read ZIP")
        return count
    }

    /** JPEG export does not change the original; intentionally avoids lossy recompression of HEIC/PNG in place. */
    fun compressImage(file: TreeEntry, folder: Uri, quality: Int = 78): Uri {
        require(file.mimeType?.startsWith("image/") == true)
        val bytes = resolver.openInputStream(file.uri)?.use { it.readBytes() } ?: error("Cannot read image")
        require(bytes.size <= 40 * 1024 * 1024) { "Image too large to decode safely" }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Unsupported image")
        val name = uniqueName(folder, file.name.substringBeforeLast('.') + " compressed.jpg")
        val target = DocumentsContract.createDocument(resolver, parent(folder), "image/jpeg", name) ?: error("Cannot create image")
        try {
            resolver.openOutputStream(target, "w")?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output))
            } ?: error("Cannot write image")
            return target
        } catch (failure: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, target) }; throw failure }
        finally { bitmap.recycle() }
    }
}
