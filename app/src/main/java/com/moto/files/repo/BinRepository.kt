package com.moto.files.repo

import android.content.Context
import com.moto.files.model.BinItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Recycle Bin. delete() moves files into app-private storage + a JSON manifest;
 * deleteForever() skips the bin entirely; a WorkManager worker purges entries older
 * than the configured retention (default 30 days). Manifest writes are mutex-guarded.
 */
class BinRepository private constructor(context: Context) {

    private val dir = File(context.filesDir, "bin")
    private val manifest = File(context.filesDir, "bin_manifest.json")
    private val mutex = Mutex()

    init { dir.mkdirs() }

    suspend fun delete(f: File): BinItem? = withContext(Dispatchers.IO) {
        if (!f.exists()) return@withContext null
        val item = BinItem(
            id = UUID.randomUUID().toString(),
            originalPath = f.absolutePath,
            storedPath = File(dir, UUID.randomUUID().toString()).absolutePath,
            name = f.name,
            size = sizeOf(f),
            deletedAt = System.currentTimeMillis(),
            isDir = f.isDirectory
        )
        val dst = File(item.storedPath)
        dst.parentFile?.mkdirs()
        val moved = f.renameTo(dst) || run { copy(f, dst); rm(f); true }
        if (moved) {
            append(item)
            item
        } else null
    }

    /** Permanent delete — bypasses the bin. Only reachable from the overflow menu. */
    suspend fun deleteForever(f: File): Boolean = withContext(Dispatchers.IO) {
        if (!f.exists()) return@withContext false
        dropWhere { it.originalPath == f.absolutePath }
        rm(f)
    }

    suspend fun restore(item: BinItem): Boolean = withContext(Dispatchers.IO) {
        val src = File(item.storedPath)
        val dst = File(item.originalPath)
        if (!src.exists()) return@withContext false
        dst.parentFile?.mkdirs()
        val ok = src.renameTo(dst) || run { copy(src, dst); rm(src); true }
        if (ok) dropWhere { it.id == item.id }
        ok
    }

    suspend fun empty(): Int = withContext(Dispatchers.IO) {
        val items = read()
        items.forEach { if (File(it.storedPath).exists()) rm(File(it.storedPath)) }
        write(emptyList())
        items.size
    }

    suspend fun purgeOlderThan(days: Int): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val items = read()
        val old = items.filter { it.deletedAt < cutoff }
        old.forEach { rm(File(it.storedPath)) }
        write(items.filter { it.deletedAt >= cutoff })
        old.size
    }

    suspend fun list(): List<BinItem> =
        withContext(Dispatchers.IO) { read().sortedByDescending { it.deletedAt } }

    // ---- manifest ----

    private fun read(): List<BinItem> {
        if (!manifest.exists()) return emptyList()
        val a = JSONArray(manifest.readText())
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            BinItem(
                o.getString("id"), o.getString("orig"), o.getString("stored"),
                o.getString("name"), o.getLong("size"), o.getLong("at"), o.getBoolean("dir")
            )
        }
    }

    private suspend fun append(item: BinItem) = mutex.withLock {
        val a = JSONArray(if (manifest.exists()) manifest.readText() else "[]")
        a.put(item.json())
        manifest.writeText(a.toString())
    }

    private suspend fun write(items: List<BinItem>) = mutex.withLock {
        val a = JSONArray()
        items.forEach { a.put(it.json()) }
        manifest.writeText(a.toString())
    }

    private suspend fun dropWhere(pred: (BinItem) -> Boolean) {
        write(read().filterNot(pred))
    }

    private fun BinItem.json() = JSONObject().apply {
        put("id", id); put("orig", originalPath); put("stored", storedPath)
        put("name", name); put("size", size); put("at", deletedAt); put("dir", isDir)
    }

    private fun sizeOf(f: File): Long =
        if (f.isDirectory) f.listFiles()?.sumOf { sizeOf(it) } ?: 0L else f.length()

    private fun copy(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copy(it, File(dst, it.name)) }
        } else src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun rm(f: File) {
        if (f.isDirectory) f.listFiles()?.forEach { rm(it) }
        f.delete()
    }

    companion object {
        @Volatile
        private var instance: BinRepository? = null
        fun get(context: Context): BinRepository =
            instance ?: synchronized(this) {
                instance ?: BinRepository(context.applicationContext).also { instance = it }
            }
    }
}
