package com.moto.files.repo

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import com.moto.files.model.Category
import com.moto.files.model.FsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Directory listing, categories, and file operations (rename/move/copy). */
class FileRepository private constructor(private val context: Context) {

    val internal: File get() = Environment.getExternalStorageDirectory()

    /** Removable SD card is the SECOND external files dir (index 0 is always primary). */
    val sdCard: File?
        get() = context.getExternalFilesDirs(null).getOrNull(1)
            ?.parentFile?.parentFile?.parentFile

    suspend fun list(dir: File, showHidden: Boolean): List<FsEntry> = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.map { f ->
                FsEntry(
                    path = f.absolutePath,
                    name = f.name,
                    isDir = f.isDirectory,
                    size = if (f.isFile) f.length() else 0L,
                    modified = f.lastModified(),
                    mime = if (f.isFile) mime(f) else null
                )
            }
            ?.sortedWith(compareByDescending<FsEntry> { it.isDir }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    suspend fun byCategory(cat: Category, root: File = internal): List<FsEntry> =
        withContext(Dispatchers.IO) {
            walk(root).whenCategory(cat).map { f ->
                FsEntry(f.absolutePath, f.name, false, f.length(), f.lastModified(), mime(f))
            }
        }

    suspend fun rename(f: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        f.renameTo(File(f.parentFile, newName))
    }

    suspend fun move(files: List<File>, into: File): Boolean = withContext(Dispatchers.IO) {
        into.mkdirs()
        files.all { f -> f.renameTo(File(into, f.name)) || transfer(f, File(into, f.name), true) }
    }

    suspend fun copy(files: List<File>, into: File): Boolean = withContext(Dispatchers.IO) {
        into.mkdirs()
        files.all { f -> transfer(f, File(into, f.name), false) }
    }

    suspend fun storage(root: File = internal): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val st = StatFs(root.absolutePath)
        (st.totalBytes - st.availableBytes) to st.availableBytes
    }

    /** Recursive walk skipping hidden entries and the Android system dir (keeps it fast). */
    private fun walk(root: File, out: MutableList<File> = mutableListOf()): List<File> {
        root.listFiles()?.forEach { f ->
            if (f.name.startsWith(".")) return@forEach
            if (f.isDirectory) {
                if (f.name != "Android") walk(f, out)
            } else out.add(f)
        }
        return out
    }

    private fun List<File>.whenCategory(cat: Category): List<File> = filter { f ->
        when (cat) {
            Category.DOWNLOADS -> f.absolutePath.contains("/Download/")
            Category.APK -> f.extension.equals("apk", true)
            Category.LARGE -> f.length() > 100L * 1024 * 1024
            Category.IMAGES -> mime(f)?.startsWith("image/") == true
            Category.VIDEOS -> mime(f)?.startsWith("video/") == true
            Category.AUDIO -> mime(f)?.startsWith("audio/") == true
            Category.DOCUMENTS -> mime(f) in DOC_MIMES || f.extension.uppercase() in DOC_EXTS
        }
    }

    private fun mime(f: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())

    private fun transfer(src: File, dst: File, deleteSrc: Boolean): Boolean = try {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { transfer(it, File(dst, it.name), deleteSrc) }
        } else {
            src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        }
        if (deleteSrc) src.deleteRecursively() else true
    } catch (e: Exception) { false }

    companion object {
        private val DOC_MIMES = setOf(
            "application/pdf", "application/msword", "text/plain",
            "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        private val DOC_EXTS = setOf("PDF","DOC","DOCX","XLS","XLSX","PPT","PPTX","TXT","CSV","EPUB")

        @Volatile
        private var instance: FileRepository? = null
        fun get(context: Context): FileRepository =
            instance ?: synchronized(this) {
                instance ?: FileRepository(context.applicationContext).also { instance = it }
            }
    }
}
