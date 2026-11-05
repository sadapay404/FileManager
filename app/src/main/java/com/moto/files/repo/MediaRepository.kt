package com.moto.files.repo

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.moto.files.model.Album
import com.moto.files.model.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MediaStore gallery: timeline plus album/month groupings. */
class MediaRepository private constructor(private val context: Context) {

    private val uri get() = MediaStore.Files.getContentUri("external")

    suspend fun all(): List<Media> = withContext(Dispatchers.IO) {
        val cols = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE
        )
        val where = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}" +
            " OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        // Effective date: DATE_TAKEN when present, else DATE_ADDED (seconds → ms).
        val sort = "CASE WHEN ${MediaStore.Files.FileColumns.DATE_TAKEN} > 0" +
            " THEN ${MediaStore.Files.FileColumns.DATE_TAKEN}" +
            " ELSE ${MediaStore.Files.FileColumns.DATE_ADDED} * 1000 END DESC"

        context.contentResolver.query(uri, cols, where, null, sort)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val mime = c.getString(iMime) ?: ""
                    add(
                        Media(
                            id = id,
                            uri = ContentUris.withAppendedId(uri, id).toString(),
                            name = c.getString(iName) ?: "",
                            mime = mime,
                            dateMs = c.getLong(iTaken).takeIf { it > 0 } ?: c.getLong(iAdded) * 1000L,
                            size = c.getLong(iSize),
                            isVideo = mime.startsWith("video/")
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    suspend fun albums(): List<Album> = withContext(Dispatchers.IO) {
        all().groupBy { it.uri.substringAfterLast("/Android/").substringBefore('/')
            .ifEmpty { "Recents" } }
            .map { (name, items) -> Album(name, items.firstOrNull()?.uri, items) }
            .sortedByDescending { it.items.size }
    }

    suspend fun memories(): List<Album> = withContext(Dispatchers.IO) {
        val m = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        all().filter { !it.isVideo }.groupBy {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMs }
            "${m[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
        }.map { (name, items) -> Album("Memories · $name", items.firstOrNull()?.uri, items) }
            .sortedByDescending { it.items.firstOrNull()?.dateMs ?: 0L }
    }

    companion object {
        @Volatile
        private var instance: MediaRepository? = null
        fun get(context: Context): MediaRepository =
            instance ?: synchronized(this) {
                instance ?: MediaRepository(context.applicationContext).also { instance = it }
            }
    }
}
