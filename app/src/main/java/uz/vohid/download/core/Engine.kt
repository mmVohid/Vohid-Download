package uz.vohid.download.core

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.vohid.download.AppGraph
import uz.vohid.download.data.Keys
import uz.vohid.download.data.MediaEntry
import uz.vohid.download.data.MediaInfo
import uz.vohid.download.data.longOrNull
import uz.vohid.download.data.strOrNull

sealed interface EngineState {
    data object Initializing : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

enum class UpdateResult { UPDATED, UP_TO_DATE }

/** yt-dlp + FFmpeg ni ishga tushirish, havolani tahlil qilish va yangilash. */
object Engine {

    private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

    private lateinit var app: Application
    private val initMutex = Mutex()

    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    fun start(application: Application) {
        app = application
        retryInit()
    }

    fun retryInit() {
        AppGraph.scope.launch(Dispatchers.IO) {
            var ok = false
            initMutex.withLock {
                if (_state.value is EngineState.Ready) {
                    ok = true
                    return@withLock
                }
                _state.value = EngineState.Initializing
                try {
                    YoutubeDL.init(app)
                    FFmpeg.init(app)
                    _version.value = YoutubeDL.version(app)
                    _state.value = EngineState.Ready
                    ok = true
                } catch (e: Throwable) {
                    _state.value = EngineState.Failed(e.message ?: e.toString())
                }
            }
            if (ok) autoUpdateIfNeeded()
        }
    }

    suspend fun awaitReady() {
        val s = state.first { it !is EngineState.Initializing }
        if (s is EngineState.Failed) throw IllegalStateException(s.message)
    }

    private suspend fun autoUpdateIfNeeded() {
        val s = AppGraph.settings.flow.first()
        if (!s.autoUpdate) return
        if (System.currentTimeMillis() - s.lastUpdateCheck < UPDATE_INTERVAL_MS) return
        runCatching { updateYtDlp() }
    }

    suspend fun updateYtDlp(): UpdateResult = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
        _version.value = YoutubeDL.version(app)
        AppGraph.settings.set(Keys.LAST_UPDATE, System.currentTimeMillis())
        if (status == YoutubeDL.UpdateStatus.DONE) UpdateResult.UPDATED else UpdateResult.UP_TO_DATE
    }

    /** Havola haqida ma'lumot oladi (video yoki playlist). Bekor qilinsa jarayon to'xtatiladi. */
    suspend fun fetchInfo(url: String, noPlaylist: Boolean): MediaInfo {
        awaitReady()
        val out = runInterruptible(Dispatchers.IO) {
            val req = YoutubeDLRequest(url)
            req.addOption("--dump-single-json")
            req.addOption("--flat-playlist")
            req.addOption("--no-warnings")
            req.addOption("--socket-timeout", "20")
            if (noPlaylist) req.addOption("--no-playlist")
            YoutubeDL.getInstance().execute(req).out
        }
        return withContext(Dispatchers.Default) { parseInfo(out, url) }
    }
}

private class FormatRow(val height: Int?, val hasVideo: Boolean, val hasAudio: Boolean, val size: Long?)

internal fun parseInfo(raw: String, requested: String): MediaInfo {
    val line = raw.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
        ?: throw IllegalStateException("Unsupported URL: empty response")
    val j = JSONObject(line)

    val title = j.strOrNull("title") ?: "—"
    val uploader = j.strOrNull("uploader") ?: j.strOrNull("channel") ?: j.strOrNull("uploader_id")
    val thumb = thumbnailOf(j)
    val duration = j.longOrNull("duration")
    val pageUrl = j.strOrNull("webpage_url") ?: requested

    val entriesArr = j.optJSONArray("entries")
    if (entriesArr != null) {
        val entries = ArrayList<MediaEntry>()
        for (i in 0 until entriesArr.length()) {
            val e = entriesArr.optJSONObject(i) ?: continue
            val u = entryUrl(e) ?: continue
            entries += MediaEntry(
                id = i.toString(),
                url = u,
                title = e.strOrNull("title") ?: u,
                thumbnail = thumbnailOf(e),
                durationSec = e.longOrNull("duration"),
                uploader = e.strOrNull("uploader") ?: e.strOrNull("channel"),
            )
        }
        if (entries.isEmpty()) throw IllegalStateException("Unsupported URL: no entries")
        return MediaInfo(
            url = pageUrl,
            title = title,
            uploader = uploader,
            thumbnail = thumb ?: entries.firstNotNullOfOrNull { it.thumbnail },
            durationSec = null,
            isPlaylist = true,
            entries = entries,
            heights = emptyList(),
            sizeByHeight = emptyMap(),
        )
    }

    val rows = ArrayList<FormatRow>()
    val formats = j.optJSONArray("formats")
    if (formats != null) {
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val h = f.longOrNull("height")?.toInt()?.takeIf { it > 0 }
            val v = f.strOrNull("vcodec")
            val a = f.strOrNull("acodec")
            rows += FormatRow(
                height = h,
                hasVideo = v?.let { it != "none" } ?: (h != null),
                hasAudio = a != "none",
                size = f.longOrNull("filesize") ?: f.longOrNull("filesize_approx"),
            )
        }
    }
    val audioBest = rows.filter { !it.hasVideo && it.hasAudio }.mapNotNull { it.size }.maxOrNull() ?: 0L
    val sizes = HashMap<Int, Long>()
    for (r in rows) {
        val h = r.height ?: continue
        val s = r.size ?: continue
        if (!r.hasVideo) continue
        val total = s + if (r.hasAudio) 0L else audioBest
        if (total > (sizes[h] ?: 0L)) sizes[h] = total
    }
    val heights = rows.filter { it.hasVideo }.mapNotNull { it.height }.distinct().sortedDescending()

    val entry = MediaEntry(
        id = "0",
        url = pageUrl,
        title = title,
        thumbnail = thumb,
        durationSec = duration,
        uploader = uploader,
    )
    return MediaInfo(
        url = pageUrl,
        title = title,
        uploader = uploader,
        thumbnail = thumb,
        durationSec = duration,
        isPlaylist = false,
        entries = listOf(entry),
        heights = heights,
        sizeByHeight = sizes,
    )
}

private fun thumbnailOf(j: JSONObject): String? {
    j.strOrNull("thumbnail")?.let { return it }
    val arr = j.optJSONArray("thumbnails") ?: return null
    for (i in arr.length() - 1 downTo 0) {
        arr.optJSONObject(i)?.strOrNull("url")?.let { return it }
    }
    return null
}

private fun entryUrl(e: JSONObject): String? {
    val u = e.strOrNull("url") ?: e.strOrNull("webpage_url")
    if (u != null && (u.startsWith("http://") || u.startsWith("https://"))) return u
    val id = e.strOrNull("id")
    val ie = e.strOrNull("ie_key")?.lowercase()
    if (id != null && ie == "youtube") return "https://www.youtube.com/watch?v=$id"
    return null
}
