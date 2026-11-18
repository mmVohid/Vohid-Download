package uz.vohid.download.core

import android.app.Application
import android.os.SystemClock
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.vohid.download.AppGraph
import uz.vohid.download.R
import uz.vohid.download.data.AppSettings
import uz.vohid.download.data.DownloadItem
import uz.vohid.download.data.DownloadSpec
import uz.vohid.download.data.Kind
import uz.vohid.download.data.Status
import uz.vohid.download.data.Store
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/** Yuklashlar navbati va bajarilishi. Holat [items] orqali kuzatiladi. */
object Downloader {

    private lateinit var app: Application
    private lateinit var storeFile: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val jobs = HashMap<String, Job>()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val speedRegex = Regex("""\bat\s+([\d.]+\s?[KMGT]?i?B/s)""")

    fun init(application: Application) {
        app = application
        storeFile = File(app.filesDir, "downloads.json")
        // Ilova yopilganda tugallanmagan yuklashlar "uzilgan" deb belgilanadi (qayta urinish mumkin).
        _items.value = Store.load(storeFile).map {
            if (it.status.isActive) {
                it.copy(status = Status.FAILED, message = app.getString(R.string.err_interrupted))
            } else {
                it
            }
        }
        scope.launch {
            tmpRoot().deleteRecursively()
            persist()
        }
    }

    fun enqueue(specs: List<DownloadSpec>) {
        if (specs.isEmpty()) return
        val now = System.currentTimeMillis()
        val created = specs.mapIndexed { i, s ->
            DownloadItem(
                id = UUID.randomUUID().toString(),
                url = s.url,
                title = s.title,
                uploader = s.uploader,
                thumbnail = s.thumbnail,
                durationSec = s.durationSec,
                kind = s.kind,
                height = s.height,
                status = Status.QUEUED,
                createdAt = now + i,
            )
        }
        _items.update { it + created }
        persist()
        DownloadService.start(app)
        pump()
    }

    /** Tezkor yuklash: ma'lumot olmasdan, sozlamalardagi standart format/sifat bilan. */
    fun enqueueQuick(url: String, s: AppSettings) {
        enqueue(
            listOf(
                DownloadSpec(
                    url = url,
                    title = url,
                    uploader = null,
                    thumbnail = null,
                    durationSec = null,
                    kind = s.kind,
                    height = if (s.kind.isAudio) 0 else s.height,
                ),
            ),
        )
    }

    fun cancel(id: String) {
        val item = find(id) ?: return
        if (item.status != Status.QUEUED && item.status != Status.RUNNING) return
        update(id) {
            it.copy(
                status = Status.CANCELED,
                speed = null,
                etaSec = -1,
                message = null,
                finishedAt = System.currentTimeMillis(),
            )
        }
        if (item.status == Status.RUNNING) YoutubeDL.getInstance().destroyProcessById(id)
        persist()
    }

    fun cancelAll() {
        _items.value
            .filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
            .forEach { cancel(it.id) }
    }

    fun retry(id: String) {
        val item = find(id) ?: return
        if (item.status != Status.FAILED && item.status != Status.CANCELED) return
        update(id) {
            it.copy(
                status = Status.QUEUED,
                progress = 0f,
                speed = null,
                etaSec = -1,
                message = null,
                createdAt = System.currentTimeMillis(),
            )
        }
        persist()
        DownloadService.start(app)
        pump()
    }

    fun remove(id: String, deleteFile: Boolean) {
        val item = find(id) ?: return
        if (item.status.isActive) return
        if (deleteFile) item.fileUri?.let { uri -> scope.launch { Publisher.delete(app, uri) } }
        _items.update { list -> list.filter { it.id != id } }
        persist()
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.status.isActive } }
        persist()
    }

    // ---------------------------------------------------------------- ichki qismlar

    private fun find(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun persist() {
        scope.launch { Store.save(storeFile, _items.value) }
    }

    private fun tmpRoot(): File = File(app.getExternalFilesDir(null) ?: app.filesDir, "tmp")

    /** Bo'sh joy bo'lsa navbatdagi yuklashlarni ishga tushiradi. */
    private fun pump() {
        synchronized(lock) {
            val limit = AppGraph.current.parallel.coerceIn(1, 3)
            var running = jobs.size
            if (running >= limit) return
            val next = _items.value
                .filter { it.status == Status.QUEUED && it.id !in jobs }
                .sortedBy { it.createdAt }
            for (item in next) {
                if (running >= limit) break
                jobs[item.id] = scope.launch {
                    try {
                        process(item.id)
                    } finally {
                        synchronized(lock) { jobs.remove(item.id) }
                        pump()
                    }
                }
                running++
            }
        }
    }

    private suspend fun process(id: String) {
        val start = find(id) ?: return
        if (start.status != Status.QUEUED) return
        update(id) {
            it.copy(status = Status.RUNNING, progress = 0f, speed = null, etaSec = -1, message = null)
        }
        val dir = File(tmpRoot(), id).apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            Engine.awaitReady()
            // Dvigatel tayyorlanayotganda bekor qilingan bo'lishi mumkin.
            if (find(id)?.status != Status.RUNNING) return
            val request = buildRequest(start, dir)

            var stage = 0
            var postProcessing = false
            var lastPush = 0L
            var lastShown = -1f

            YoutubeDL.getInstance().execute(request, id) { percent, eta, line ->
                // Jarayon ro'yxatga olinishidan oldin bekor qilingan bo'lsa, shu yerda to'xtatamiz.
                if (find(id)?.status == Status.CANCELED) {
                    YoutubeDL.getInstance().destroyProcessById(id)
                    return@execute
                }
                if (line.startsWith("[download] Destination:")) {
                    stage++
                    return@execute
                }
                val isPost = line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") ||
                    line.startsWith("[VideoRemuxer]") || line.startsWith("[VideoConvertor]")
                if (isPost && !postProcessing) {
                    postProcessing = true
                    update(id) {
                        if (it.status == Status.RUNNING) {
                            it.copy(status = Status.SAVING, progress = max(it.progress, 0.9f), speed = null, etaSec = -1)
                        } else {
                            it
                        }
                    }
                    return@execute
                }
                if (postProcessing || !line.startsWith("[download]")) return@execute

                val frac = (percent / 100f).coerceIn(0f, 1f)
                // Video + audio ikki bosqichda yuklanadi: video ~90%, audio ~10%.
                val overall = when {
                    start.kind.isAudio -> frac
                    stage <= 1 -> frac * 0.9f
                    else -> 0.9f + frac * 0.1f
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastPush < 400 && abs(overall - lastShown) < 0.01f) return@execute
                lastPush = now
                lastShown = overall
                val speed = speedRegex.find(line)?.groupValues?.get(1)?.replace(" ", "")
                update(id) {
                    if (it.status != Status.RUNNING) {
                        it
                    } else {
                        it.copy(progress = max(it.progress, overall), speed = speed ?: it.speed, etaSec = eta)
                    }
                }
            }

            val current = find(id)
            if (current == null || !current.status.isActive) return // bekor qilingan

            update(id) {
                it.copy(status = Status.SAVING, progress = max(it.progress, 0.95f), speed = null, etaSec = -1)
            }
            val file = pickOutput(dir)
                ?: throw IllegalStateException(app.getString(R.string.err_no_output))
            val published = Publisher.publish(app, file)
            update(id) {
                it.copy(
                    title = if (it.title == it.url) published.name.substringBeforeLast('.') else it.title,
                    status = Status.COMPLETED,
                    progress = 1f,
                    speed = null,
                    etaSec = -1,
                    message = null,
                    fileUri = published.uri.toString(),
                    fileName = published.name,
                    sizeBytes = published.size,
                    finishedAt = System.currentTimeMillis(),
                )
            }
            find(id)?.let { Notifications.done(app, it) }
        } catch (e: YoutubeDL.CanceledException) {
            // Foydalanuvchi bekor qilgan — holat cancel() ichida belgilangan.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val cur = find(id)
            if (cur != null && (cur.status == Status.RUNNING || cur.status == Status.SAVING)) {
                val msg = friendlyError(app, e.message)
                update(id) {
                    it.copy(
                        status = Status.FAILED,
                        message = msg,
                        speed = null,
                        etaSec = -1,
                        finishedAt = System.currentTimeMillis(),
                    )
                }
                find(id)?.let { Notifications.failed(app, it) }
            }
        } finally {
            dir.deleteRecursively()
            Store.save(storeFile, _items.value)
        }
    }

    private fun pickOutput(dir: File): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in Publisher.mediaExt }
            ?.maxByOrNull { it.length() }

    private fun buildRequest(item: DownloadItem, dir: File): YoutubeDLRequest {
        val s = AppGraph.current
        val req = YoutubeDLRequest(item.url)
        req.addOption("-o", File(dir, "%(title).100s.%(ext)s").absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--newline")
        req.addOption("--windows-filenames")
        req.addOption("--no-mtime")
        req.addOption("--socket-timeout", "20")
        req.addOption("--retries", "10")
        req.addOption("--fragment-retries", "10")
        req.addOption("--concurrent-fragments", "4")

        val h = item.height
        val videoSelector = if (h > 0) "bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b" else "bv*+ba/b"

        when (item.kind) {
            Kind.VIDEO_MP4 -> {
                req.addOption("-f", videoSelector)
                // Avval balandlik, so'ng H.264/AAC (moslik uchun), mavjud bo'lmasa — boshqa kodek.
                req.addOption("-S", "res,vcodec:h264,acodec:m4a")
                req.addOption("--merge-output-format", "mp4")
            }
            Kind.VIDEO_MKV -> {
                req.addOption("-f", videoSelector)
                req.addOption("--merge-output-format", "mkv")
            }
            Kind.AUDIO_MP3 -> {
                req.addOption("-f", "ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", "0")
            }
            Kind.AUDIO_M4A -> {
                req.addOption("-f", "ba[ext=m4a]/ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "m4a")
                req.addOption("--audio-quality", "0")
            }
        }
        if (s.embedMetadata) req.addOption("--embed-metadata")
        if (s.embedThumbnail && item.kind.isAudio) {
            req.addOption("--embed-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
        }
        return req
    }
}
