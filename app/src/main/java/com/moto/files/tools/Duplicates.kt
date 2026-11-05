package com.moto.files.tools

import com.moto.files.model.FsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Duplicate finder (size bucket → single SHA-256 pass) + cache-junk scan for the cleaner. */
object Duplicates {

    data class Group(val hash: String, val files: List<File>)

    suspend fun find(inFiles: List<File>): List<Group> = withContext(Dispatchers.IO) {
        inFiles.filter { it.isFile && it.length() > 0 }
            .groupBy { it.length() }
            .filterValues { it.size > 1 }
            .flatMap { (_, sameSize) ->
                sameSize.map { it to Hashing.of(it, Hashing.Algo.SHA256) }
                    .groupBy({ it.second }, { it.first })
                    .filter { it.value.size > 1 }
                    .map { (hash, files) -> Group(hash, files) }
            }
    }

    suspend fun junk(cacheDirs: List<File>): List<FsEntry> = withContext(Dispatchers.IO) {
        cacheDirs.flatMap { it.walkTopDown().filter { f -> f.isFile }.toList() }
            .map { FsEntry(it.absolutePath, it.name, false, it.length(), it.lastModified(), null) }
    }

    fun reclaimable(groups: List<Group>): Long =
        groups.sumOf { g -> g.files.sumOf { it.length() } - (g.files.firstOrNull()?.length() ?: 0L) }
}
