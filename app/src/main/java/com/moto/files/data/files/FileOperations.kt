package com.moto.files.data.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.UUID

/** Operates only on SAF document URIs; errors are returned so UI never reports false success. */
class FileOperations(private val context: Context) {
    fun rename(entry: TreeEntry, name: String): Result<Uri> = runCatching {
        require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name) { "Invalid name" }
        DocumentsContract.renameDocument(context.contentResolver, entry.uri, name.trim()) ?: error("Rename failed")
    }

    fun copy(entry: TreeEntry, folder: Uri, move: Boolean): Result<Unit> = runCatching {
        require(!entry.isDirectory) { "Folder copy is not supported yet" }
        val targetFolder = if (DocumentsContract.isDocumentUri(context, folder)) folder else
            DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        require(targetFolder != entry.uri) { "Cannot copy onto itself" }
        // Use a temporary name while streaming so a failed copy does not leave a plausible finished file.
        val tempName = ".${UUID.randomUUID()}.part"
        val temporary = DocumentsContract.createDocument(context.contentResolver, targetFolder,
            entry.mimeType ?: "application/octet-stream", tempName) ?: error("Could not create destination")
        var finished = false
        try {
            context.contentResolver.openInputStream(entry.uri)?.use { input ->
                context.contentResolver.openOutputStream(temporary, "w")?.use { output -> input.copyTo(output) }
                    ?: error("Could not write destination")
            } ?: error("Could not read source")
            DocumentsContract.renameDocument(context.contentResolver, temporary, entry.name)
                ?: error("Could not finish copy")
            finished = true
            if (move) check(DocumentsContract.deleteDocument(context.contentResolver, entry.uri)) {
                "Copied, but source could not be removed"
            }
        } catch (failure: Exception) {
            // Never discard a completed copy if source deletion fails.
            if (!finished) runCatching { DocumentsContract.deleteDocument(context.contentResolver, temporary) }
            throw failure
        }
    }

    fun permanentlyDelete(entry: TreeEntry): Result<Unit> = runCatching {
        require(!entry.isDirectory) { "Folder deletion is not supported yet" }
        check(DocumentsContract.deleteDocument(context.contentResolver, entry.uri)) { "Provider refused deletion" }
    }
}
