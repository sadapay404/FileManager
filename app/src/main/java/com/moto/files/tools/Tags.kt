package com.moto.files.tools

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.moto.files.model.FsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "file_tags")
data class TagRow(@PrimaryKey val path: String, val tags: String)

@Dao
interface TagDao {
    @Query("SELECT * FROM file_tags WHERE path = :path") suspend fun get(path: String): TagRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(row: TagRow)
    @Query("SELECT * FROM file_tags") suspend fun all(): List<TagRow>
    @Query("DELETE FROM file_tags WHERE path = :path") suspend fun del(path: String)
}

@Database(entities = [TagRow::class], version = 1)
abstract class TagDb : RoomDatabase() { abstract fun dao(): TagDao }

/** Custom-tag store (Room) backing smart search. */
class TagStore private constructor(context: Context) {
    private val db = Room.databaseBuilder(context, TagDb::class.java, "tags.db").build()

    suspend fun set(path: String, tags: Set<String>) = withContext(Dispatchers.IO) {
        db.dao().put(TagRow(path, tags.joinToString(",")))
    }

    suspend fun of(path: String): Set<String> = withContext(Dispatchers.IO) {
        db.dao().get(path)?.tags?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    suspend fun remove(path: String) = withContext(Dispatchers.IO) { db.dao().del(path) }

    suspend fun all(): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        db.dao().all().associate { it.path to it.tags.split(',').filter(String::isNotBlank).toSet() }
    }

    companion object {
        @Volatile
        private var instance: TagStore? = null
        fun get(context: Context): TagStore =
            instance ?: synchronized(this) {
                instance ?: TagStore(context.applicationContext).also { instance = it }
            }
    }
}

/** Smart search: name, extension, size range, date range, custom tag. */
object SmartSearch {

    data class Query(
        val text: String = "",
        val ext: String? = null,
        val minBytes: Long? = null,
        val maxBytes: Long? = null,
        val fromMs: Long? = null,
        val toMs: Long? = null,
        val tag: String? = null
    )

    fun run(items: List<FsEntry>, q: Query, tags: Map<String, Set<String>>): List<FsEntry> =
        items.filter {
            (q.text.isBlank() || it.name.contains(q.text, true)) &&
                (q.ext == null || it.ext.equals(q.ext.removePrefix("."), true)) &&
                (q.minBytes == null || it.size >= q.minBytes) &&
                (q.maxBytes == null || it.size <= q.maxBytes) &&
                (q.fromMs == null || it.modified >= q.fromMs) &&
                (q.toMs == null || it.modified <= q.toMs) &&
                (q.tag == null || tags[it.path]?.contains(q.tag) == true)
        }
}
