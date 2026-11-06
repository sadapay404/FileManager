package com.moto.files.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaEntry(val id: Long, val uri: Uri, val name: String, val dateSeconds: Long, val bucket: String, val isVideo: Boolean)

/** Queries only the media types the user granted. Never resolves filesystem paths from a content URI. */
class MediaRepository(private val context: Context) {
    suspend fun load(images: Boolean, videos: Boolean): List<MediaEntry> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        buildList {
            if (images) addAll(query(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false))
            if (videos) addAll(query(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true))
        }.sortedWith(compareByDescending<MediaEntry> { it.dateSeconds }.thenByDescending { it.id })
    }

    private fun query(resolver: ContentResolver, collection: Uri, video: Boolean): List<MediaEntry> {
        val id = MediaStore.MediaColumns._ID
        val name = MediaStore.MediaColumns.DISPLAY_NAME
        val date = MediaStore.MediaColumns.DATE_ADDED
        val bucket = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        val result = mutableListOf<MediaEntry>()
        try {
            resolver.query(collection, arrayOf(id, name, date, bucket), null, null, "$date DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(id)
                val nameColumn = cursor.getColumnIndexOrThrow(name)
                val dateColumn = cursor.getColumnIndexOrThrow(date)
                val bucketColumn = cursor.getColumnIndexOrThrow(bucket)
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn)
                    result += MediaEntry(mediaId, ContentUris.withAppendedId(collection, mediaId),
                        cursor.getString(nameColumn) ?: "Untitled", cursor.getLong(dateColumn),
                        cursor.getString(bucketColumn) ?: "Other", video)
                }
            }
        } catch (_: SecurityException) {
            // A partial media grant may be revoked between permission check and query.
        }
        return result
    }
}
