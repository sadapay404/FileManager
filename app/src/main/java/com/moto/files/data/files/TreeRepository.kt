package com.moto.files.data.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TreeEntry(val uri: Uri, val name: String, val isDirectory: Boolean, val mimeType: String?, val length: Long)

/** SAF-scoped browsing: child document URIs retain the user's persisted tree grant. */
class TreeRepository(private val context: Context) {
    suspend fun list(folder: Uri): Result<List<TreeEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, if (DocumentsContract.isDocumentUri(context, folder)) DocumentsContract.getDocumentId(folder) else DocumentsContract.getTreeDocumentId(folder))
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE)
            val result = mutableListOf<TreeEntry>()
            context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val mime = cursor.getString(2)
                    result += TreeEntry(DocumentsContract.buildDocumentUriUsingTree(folder, id),
                        cursor.getString(1) ?: "Untitled", mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        mime, if (cursor.isNull(3)) 0 else cursor.getLong(3))
                }
            } ?: error("Folder is unavailable")
            result.sortedWith(compareByDescending<TreeEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }
}
