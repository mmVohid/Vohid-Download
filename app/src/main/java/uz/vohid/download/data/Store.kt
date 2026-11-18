package uz.vohid.download.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Yuklamalar tarixini JSON faylda saqlash. */
object Store {

    private const val MAX_FINISHED = 300

    fun load(file: File): List<DownloadItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    fromJson(o)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(file: File, items: List<DownloadItem>) {
        runCatching {
            val finished = items.filter { !it.status.isActive }
                .sortedByDescending { it.createdAt }
                .take(MAX_FINISHED)
                .map { it.id }
                .toSet()
            val arr = JSONArray()
            items.filter { it.status.isActive || it.id in finished }.forEach { arr.put(toJson(it)) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    private fun toJson(i: DownloadItem) = JSONObject().apply {
        put("id", i.id)
        put("url", i.url)
        put("title", i.title)
        put("uploader", i.uploader ?: JSONObject.NULL)
        put("thumbnail", i.thumbnail ?: JSONObject.NULL)
        put("duration", i.durationSec ?: JSONObject.NULL)
        put("kind", i.kind.name)
        put("height", i.height)
        put("status", i.status.name)
        put("message", i.message ?: JSONObject.NULL)
        put("fileUri", i.fileUri ?: JSONObject.NULL)
        put("fileName", i.fileName ?: JSONObject.NULL)
        put("size", i.sizeBytes)
        put("createdAt", i.createdAt)
        put("finishedAt", i.finishedAt)
    }

    private fun fromJson(o: JSONObject): DownloadItem? = runCatching {
        DownloadItem(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title", ""),
            uploader = o.strOrNull("uploader"),
            thumbnail = o.strOrNull("thumbnail"),
            durationSec = if (o.isNull("duration")) null else o.optLong("duration"),
            kind = Kind.valueOf(o.getString("kind")),
            height = o.optInt("height", 0),
            status = Status.valueOf(o.getString("status")),
            progress = if (o.optString("status") == Status.COMPLETED.name) 1f else 0f,
            message = o.strOrNull("message"),
            fileUri = o.strOrNull("fileUri"),
            fileName = o.strOrNull("fileName"),
            sizeBytes = o.optLong("size", 0),
            createdAt = o.optLong("createdAt", 0),
            finishedAt = o.optLong("finishedAt", 0),
        )
    }.getOrNull()
}

/** JSON null / yo'q / bo'sh qiymatni null qaytaradi (org.json "null" matnini bermasligi uchun). */
fun JSONObject.strOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

fun JSONObject.longOrNull(key: String): Long? {
    if (isNull(key)) return null
    val d = optDouble(key)
    return if (d.isNaN()) null else d.toLong()
}
