package uz.vohid.download.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.vohid.download.AppGraph
import uz.vohid.download.R
import uz.vohid.download.core.Downloader
import uz.vohid.download.core.Engine
import uz.vohid.download.core.EngineState
import uz.vohid.download.core.Publisher
import uz.vohid.download.core.UpdateResult
import uz.vohid.download.core.cleanError
import uz.vohid.download.core.friendlyError
import uz.vohid.download.data.AppSettings
import uz.vohid.download.data.DownloadItem
import uz.vohid.download.data.DownloadSpec
import uz.vohid.download.data.Keys
import uz.vohid.download.data.Kind
import uz.vohid.download.data.MediaInfo

enum class Phase { IDLE, LOADING, READY, ERROR }

data class HomeState(
    val url: String = "",
    val phase: Phase = Phase.IDLE,
    val info: MediaInfo? = null,
    val error: String? = null,
    val kind: Kind = Kind.VIDEO_MP4,
    /** 0 — eng yaxshi. */
    val height: Int = 0,
    val selected: Set<String> = emptySet(),
)

sealed interface UiEvent {
    data class Queued(val count: Int) : UiEvent
    data class Text(val text: String) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settings: StateFlow<AppSettings> =
        AppGraph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppGraph.current)
    val items: StateFlow<List<DownloadItem>> = Downloader.items
    val engine: StateFlow<EngineState> = Engine.state
    val ytdlpVersion: StateFlow<String?> = Engine.version

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var fetchJob: Job? = null

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    // ------------------------------------------------------------ navigatsiya

    fun selectTab(index: Int) {
        _tab.value = index
    }

    // ------------------------------------------------------------ bosh sahifa

    fun onUrlChange(text: String) {
        fetchJob?.cancel()
        _home.update {
            if (it.url == text) it else it.copy(url = text, phase = Phase.IDLE, info = null, error = null)
        }
    }

    /** Boshqa ilovadan ulashilgan matnni qabul qiladi va darhol tahlil qiladi. */
    fun receiveSharedText(text: String?) {
        val url = extractUrl(text) ?: return
        _tab.value = 0
        _home.update { it.copy(url = url, phase = Phase.IDLE, info = null, error = null) }
        analyze()
    }

    fun analyze(noPlaylist: Boolean = false) {
        val url = extractUrl(_home.value.url)
        if (url == null) {
            _home.update { it.copy(phase = Phase.ERROR, error = str(R.string.err_bad_url)) }
            return
        }
        fetchJob?.cancel()
        _home.update { it.copy(url = url, phase = Phase.LOADING, error = null, info = null) }
        fetchJob = viewModelScope.launch {
            try {
                val info = Engine.fetchInfo(url, noPlaylist)
                val s = settings.value
                val height = if (s.height == 0 || info.heights.isEmpty() || s.height <= info.maxHeight) s.height else 0
                _home.update {
                    it.copy(
                        phase = Phase.READY,
                        info = info,
                        error = null,
                        kind = s.kind,
                        height = height,
                        selected = info.entries.map { e -> e.id }.toSet(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _home.update {
                    it.copy(phase = Phase.ERROR, info = null, error = friendlyError(getApplication(), e.message))
                }
            }
        }
    }

    fun retryEngine() = Engine.retryInit()

    fun setKind(kind: Kind) = _home.update { it.copy(kind = kind) }

    fun setHeight(height: Int) = _home.update { it.copy(height = height) }

    fun toggleEntry(id: String) = _home.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun toggleAll() = _home.update { st ->
        val all = st.info?.entries?.map { e -> e.id }?.toSet().orEmpty()
        st.copy(selected = if (st.selected.size == all.size) emptySet() else all)
    }

    fun download() {
        val h = _home.value
        val info = h.info ?: return
        val chosen = info.entries.filter { it.id in h.selected }
        if (chosen.isEmpty()) return
        val specs = chosen.map {
            DownloadSpec(
                url = it.url,
                title = it.title,
                uploader = it.uploader ?: info.uploader,
                thumbnail = it.thumbnail ?: info.thumbnail,
                durationSec = it.durationSec,
                kind = h.kind,
                height = if (h.kind.isAudio) 0 else h.height,
            )
        }
        Downloader.enqueue(specs)
        _home.value = HomeState(kind = h.kind, height = h.height)
        _events.tryEmit(UiEvent.Queued(specs.size))
    }

    // ------------------------------------------------------------ yuklamalar

    fun cancel(item: DownloadItem) = Downloader.cancel(item.id)
    fun cancelAll() = Downloader.cancelAll()
    fun retry(item: DownloadItem) = Downloader.retry(item.id)
    fun remove(item: DownloadItem, deleteFile: Boolean) = Downloader.remove(item.id, deleteFile)
    fun clearFinished() = Downloader.clearFinished()

    fun open(item: DownloadItem) {
        val ctx: Context = getApplication()
        val uri = item.fileUri?.let { Uri.parse(it) }
        if (uri == null || !Publisher.exists(ctx, uri)) {
            _events.tryEmit(UiEvent.Text(str(R.string.file_missing)))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, Publisher.mimeOf(ctx, uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _events.tryEmit(UiEvent.Text(str(R.string.no_app)))
        }
    }

    fun share(item: DownloadItem) {
        val ctx: Context = getApplication()
        val uri = item.fileUri?.let { Uri.parse(it) }
        if (uri == null || !Publisher.exists(ctx, uri)) {
            _events.tryEmit(UiEvent.Text(str(R.string.file_missing)))
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(Publisher.mimeOf(ctx, uri))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            _events.tryEmit(UiEvent.Text(str(R.string.no_app)))
        }
    }

    fun copyLink(item: DownloadItem) {
        val ctx: Context = getApplication()
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("url", item.url))
        // Android 13+ nusxalash haqida o'zi xabar beradi.
        if (Build.VERSION.SDK_INT < 33) _events.tryEmit(UiEvent.Text(str(R.string.link_copied)))
    }

    // ------------------------------------------------------------ sozlamalar

    fun <T> setPref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { AppGraph.settings.set(key, value) }
    }

    fun markNotifAsked() = setPref(Keys.NOTIF_ASKED, true)

    fun updateEngine() {
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            try {
                val r = Engine.updateYtDlp()
                val v = Engine.version.value.orEmpty()
                _events.tryEmit(
                    UiEvent.Text(
                        if (r == UpdateResult.UPDATED) str(R.string.update_done, v) else str(R.string.update_latest),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val reason = cleanError(e.message).ifBlank { e.message.orEmpty() }
                _events.tryEmit(UiEvent.Text(str(R.string.update_failed, reason)))
            } finally {
                _updating.value = false
            }
        }
    }
}

private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

/** Matn ichidan birinchi havolani ajratadi; "youtu.be/xyz" kabi sxemasiz kiritishni ham tushunadi. */
internal fun extractUrl(text: String?): String? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    urlRegex.find(t)?.let { return it.value.trimEnd(')', ']', '.', ',') }
    if (!t.contains(' ') && t.contains('.') && !t.startsWith(".") && !t.endsWith(".")) return "https://$t"
    return null
}
