package com.moto.files.data.trash

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.DocumentsContract
import com.moto.files.data.files.TreeEntry
import java.io.File
import java.util.UUID

private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

data class TrashedFile(val id: String, val name: String, val mime: String, val parent: Uri, val deletedAt: Long)

private class TrashDb(context: Context) : SQLiteOpenHelper(context, "trash.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trash (id TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, parent TEXT NOT NULL, deleted_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/** App-private backup is fully copied before SAF original is removed; never delete a source on copy failure. */
class TrashRepository(private val context: Context) {
    private val db = TrashDb(context)
    private val directory = File(context.filesDir, "recycle").apply { mkdirs() }
    private fun payload(id: String) = File(directory, id)

    @Synchronized
    fun list(): List<TrashedFile> = buildList {
        db.readableDatabase.query("trash", null, null, null, null, null, "deleted_at DESC").use { cursor ->
            while (cursor.moveToNext()) add(TrashedFile(cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("mime")),
                Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow("parent"))), cursor.getLong(cursor.getColumnIndexOrThrow("deleted_at"))))
        }
    }

    @Synchronized
    fun put(entry: TreeEntry, parent: Uri): Result<Unit> = runCatching {
        require(!entry.isDirectory) { "Folder deletion is not supported yet" }
        val id = UUID.randomUUID().toString()
        val file = payload(id)
        try {
            context.contentResolver.openInputStream(entry.uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read source")
            check(file.length() == entry.length || entry.length == 0L) { "Incomplete backup" }
            val values = ContentValues().apply {
                put("id", id); put("name", entry.name); put("mime", entry.mimeType ?: "application/octet-stream")
                put("parent", parent.toString()); put("deleted_at", System.currentTimeMillis())
            }
            check(db.writableDatabase.insertOrThrow("trash", null, values) != -1L)
            try {
                check(DocumentsContract.deleteDocument(context.contentResolver, entry.uri)) { "Provider refused deletion" }
            } catch (failure: Exception) {
                db.writableDatabase.delete("trash", "id=?", arrayOf(id))
                throw failure
            }
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    @Synchronized
    fun restore(item: TrashedFile): Result<Unit> = runCatching {
        val file = payload(item.id)
        check(file.isFile) { "Backup is missing" }
        val parent = documentParent(item.parent)
        val target = DocumentsContract.createDocument(context.contentResolver, parent, item.mime, item.name)
            ?: error("Destination unavailable or permission revoked")
        try {
            file.inputStream().use { input ->
                context.contentResolver.openOutputStream(target, "w")?.use { output -> input.copyTo(output) }
                    ?: error("Cannot write restored file")
            }
        } catch (failure: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            throw failure
        }
        // Keep the backup if removing the metadata fails, so the user can retry.
        check(db.writableDatabase.delete("trash", "id=?", arrayOf(item.id)) == 1)
        file.delete()
    }

    @Synchronized
    fun remove(item: TrashedFile): Result<Unit> = runCatching {
        val file = payload(item.id)
        check(!file.exists() || file.delete()) { "Could not remove backup" }
        check(db.writableDatabase.delete("trash", "id=?", arrayOf(item.id)) == 1)
    }

    @Synchronized
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        list().filter { it.deletedAt <= now - RETENTION_MS }.forEach { if (remove(it).isSuccess) removed++ }
        return removed
    }

    private fun documentParent(uri: Uri): Uri = if (DocumentsContract.isDocumentUri(context, uri)) uri else
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
}
