package com.moto.files.model

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM }

enum class HomeMode { FILES, GALLERY }

enum class LockKind { NONE, PIN, PASSWORD, PATTERN }

enum class Category {
    DOWNLOADS, IMAGES, VIDEOS, AUDIO, DOCUMENTS, APK, LARGE
}

data class FsEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val mime: String? = null
) {
    val ext: String get() = name.substringAfterLast('.', "").uppercase()
}

data class Media(
    val id: Long,
    val uri: String,
    val name: String,
    val mime: String,
    val dateMs: Long,
    val size: Long,
    val isVideo: Boolean
)

data class Album(
    val name: String,
    val cover: String?,
    val items: List<Media>
)

data class BinItem(
    val id: String,
    val originalPath: String,
    val storedPath: String,
    val name: String,
    val size: Long,
    val deletedAt: Long,
    val isDir: Boolean
) {
    val daysLeft: Int
        get() = (BIN_DAYS - (System.currentTimeMillis() - deletedAt) / 86_400_000L)
            .coerceIn(0, BIN_DAYS).toInt()

    companion object { const val BIN_DAYS = 30 }
}

data class LockSettings(
    val kind: LockKind = LockKind.NONE,
    val hash: String? = null,
    val salt: String? = null,
    val biometric: Boolean = false,
    val vaultSeparate: Boolean = false,
    val vaultHash: String? = null,
    val vaultSalt: String? = null
)

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val gridColumns: Int = 4,
    val showHidden: Boolean = false,
    val binDays: Int = BinItem.BIN_DAYS,
    val sharePort: Int = 8080
)
