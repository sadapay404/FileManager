package com.moto.files.ui.viewer

import android.webkit.MimeTypeMap
import java.util.Locale

enum class ContentKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, EXTERNAL }

/** SAF providers may omit a MIME type or report application/octet-stream. */
object FileTypes {
    private val images = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff", "dng", "raw")
    private val videos = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "ts", "mpeg", "mpg", "flv", "wmv")
    private val audio = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "wma", "aiff", "mid", "midi")
    private val text = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml", "ini", "kt", "java", "py")
    fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun mime(name: String, reported: String?): String {
        if (!reported.isNullOrBlank() && reported != "application/octet-stream") return reported
        val ext = extension(name)
        return when {
            ext == "pdf" -> "application/pdf"
            ext == "apk" -> "application/vnd.android.package-archive"
            ext == "heic" -> "image/heic"
            ext == "heif" -> "image/heif"
            ext == "mkv" -> "video/x-matroska"
            ext == "flac" -> "audio/flac"
            ext == "7z" -> "application/x-7z-compressed"
            ext == "tar" -> "application/x-tar"
            ext in text -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
    fun kind(name: String, mime: String?): ContentKind {
        val type = mime(name, mime)
        val ext = extension(name)
        return when {
            type == "application/pdf" || ext == "pdf" -> ContentKind.PDF
            type.startsWith("image/") || ext in images -> ContentKind.IMAGE
            type.startsWith("video/") || ext in videos -> ContentKind.VIDEO
            type.startsWith("audio/") || ext in audio -> ContentKind.AUDIO
            type.startsWith("text/") || ext in text -> ContentKind.TEXT
            else -> ContentKind.EXTERNAL
        }
    }
}
