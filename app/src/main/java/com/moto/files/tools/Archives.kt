package com.moto.files.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.io.OutputStream

/** ZIP / 7z / TAR create + extract, with zip-slip protection. */
object Archives {

    enum class Kind { ZIP, SEVENZ, TAR }

    fun kindOf(f: File): Kind? = when (f.extension.lowercase()) {
        "zip" -> Kind.ZIP
        "7z" -> Kind.SEVENZ
        "tar" -> Kind.TAR
        else -> null
    }

    suspend fun create(files: List<File>, dest: File, kind: Kind): Boolean =
        withContext(Dispatchers.IO) {
            try {
                when (kind) {
                    Kind.ZIP -> ZipArchiveOutputStream(dest.outputStream()).use { z ->
                        files.forEach { zip(it, it.name, z) }
                    }
                    Kind.TAR -> TarArchiveOutputStream(dest.outputStream()).use { t ->
                        files.forEach { tar(it, it.name, t) }
                    }
                    Kind.SEVENZ -> SevenZOutputFile(dest).use { s ->
                        files.forEach { seven(it, it.name, s) }
                    }
                }
                dest.exists() && dest.length() > 0
            } catch (e: Exception) { false }
        }

    suspend fun extract(archive: File, into: File): Boolean = withContext(Dispatchers.IO) {
        try {
            into.mkdirs()
            when (kindOf(archive)) {
                Kind.ZIP -> ZipArchiveInputStream(archive.inputStream()).use { z ->
                    var e = z.nextZipEntry
                    while (e != null) { entry(into, e.name) { o -> z.copyTo(o) }; e = z.nextZipEntry }
                }
                Kind.TAR -> TarArchiveInputStream(archive.inputStream()).use { t ->
                    var e = t.nextTarEntry
                    while (e != null) { entry(into, e.name) { o -> t.copyTo(o) }; e = t.nextTarEntry }
                }
                Kind.SEVENZ -> SevenZFile(archive).use { s ->
                    var e = s.nextEntry
                    while (e != null) {
                        entry(into, e.name) { o ->
                            val buf = ByteArray(64 * 1024)
                            var n = s.read(buf)
                            while (n != -1) { o.write(buf, 0, n); n = s.read(buf) }
                        }
                        e = s.nextEntry
                    }
                }
                null -> return@withContext false
            }
            true
        } catch (e: Exception) { false }
    }

    private inline fun entry(into: File, name: String, write: (OutputStream) -> Unit) {
        val out = File(into, name)
        if (!out.canonicalPath.startsWith(into.canonicalPath + File.separator)) return
        if (name.endsWith("/")) { out.mkdirs(); return }
        out.parentFile?.mkdirs()
        out.outputStream().use(write)
    }

    private fun zip(f: File, path: String, z: ZipArchiveOutputStream) {
        if (f.isDirectory) {
            z.putArchiveEntry(ZipArchiveEntry("$path/")); z.closeArchiveEntry()
            f.listFiles()?.forEach { zip(it, "$path/${it.name}", z) }
        } else {
            z.putArchiveEntry(ZipArchiveEntry(path))
            f.inputStream().use { it.copyTo(z) }
            z.closeArchiveEntry()
        }
    }

    private fun tar(f: File, path: String, t: TarArchiveOutputStream) {
        t.putArchiveEntry(TarArchiveEntry(f, path))
        if (f.isFile) f.inputStream().use { it.copyTo(t) }
        t.closeArchiveEntry()
        if (f.isDirectory) f.listFiles()?.forEach { tar(it, "$path/${it.name}", t) }
    }

    private fun seven(f: File, path: String, s: SevenZOutputFile) {
        s.putArchiveEntry(s.createArchiveEntry(f, path))
        if (f.isFile) f.inputStream().use { it.copyTo(s) }
        s.closeArchiveEntry()
        if (f.isDirectory) f.listFiles()?.forEach { seven(it, "$path/${it.name}", s) }
    }
}
