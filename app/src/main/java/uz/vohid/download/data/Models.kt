package uz.vohid.download.data

/** Yuklash turi (format). */
enum class Kind(val ext: String, val isAudio: Boolean) {
    VIDEO_MP4("mp4", false),
    VIDEO_MKV("mkv", false),
    AUDIO_MP3("mp3", true),
    AUDIO_M4A("m4a", true);

    val label: String get() = ext.uppercase()
}

enum class Status {
    QUEUED, RUNNING, SAVING, COMPLETED, FAILED, CANCELED;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == SAVING
}

/** Playlist ichidagi (yoki yagona) video. */
data class MediaEntry(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Long?,
    val uploader: String?,
)

/** Havola tahlilidan keyin olingan ma'lumot. */
data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val isPlaylist: Boolean,
    val entries: List<MediaEntry>,
    /** Mavjud video balandliklari (bo'sh bo'lsa — noma'lum). */
    val heights: List<Int>,
    /** Balandlik -> taxminiy hajm (bayt). */
    val sizeByHeight: Map<Int, Long>,
) {
    val maxHeight: Int get() = heights.maxOrNull() ?: 0

    /** Tanlangan sifat uchun taxminiy hajm. */
    fun estimatedSize(height: Int): Long? {
        if (sizeByHeight.isEmpty()) return null
        val limit = if (height == 0) Int.MAX_VALUE else height
        val key = sizeByHeight.keys.filter { it <= limit }.maxOrNull() ?: return null
        return sizeByHeight[key]
    }
}

/** Yuklashni boshlash uchun so'rov. */
data class DownloadSpec(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val kind: Kind,
    /** 0 — eng yaxshi. */
    val height: Int,
)

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val kind: Kind,
    val height: Int,
    val status: Status,
    val progress: Float = 0f,
    val speed: String? = null,
    val etaSec: Long = -1,
    val message: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long,
    val finishedAt: Long = 0,
)

val STANDARD_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
