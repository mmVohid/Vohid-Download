package uz.vohid.download.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/** Yuklangan faylni umumiy xotiraga (Movies/Music → VohidDownload) MediaStore orqali joylaydi. */
object Publisher {

    data class Published(val uri: Uri, val name: String, val size: Long)

    private val audioExt = setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav", "weba")
    val mediaExt: Set<String> = audioExt + setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "3gp")

    fun publish(ctx: Context, file: File): Published {
        val ext = file.extension.lowercase()
        val isAudio = ext in audioExt
        val mime = mimeFor(ext, isAudio)
        val resolver = ctx.contentResolver

        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) +
            "/VohidDownload"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output stream")
            out.use { o -> file.inputStream().use { it.copyTo(o, 256 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        val finalName = resolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: file.name

        return Published(uri, finalName, file.length())
    }

    fun mimeOf(ctx: Context, uri: Uri): String =
        ctx.contentResolver.getType(uri) ?: "video/*"

    fun exists(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    fun delete(ctx: Context, uriString: String) {
        runCatching { ctx.contentResolver.delete(Uri.parse(uriString), null, null) }
    }

    private fun mimeFor(ext: String, isAudio: Boolean): String {
        val prefix = if (isAudio) "audio/" else "video/"
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?.takeIf { it.startsWith(prefix) }
            ?.let { return it }
        return when (ext) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "opus", "ogg", "oga", "weba" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            else -> if (isAudio) "audio/mpeg" else "video/mp4"
        }
    }
}
