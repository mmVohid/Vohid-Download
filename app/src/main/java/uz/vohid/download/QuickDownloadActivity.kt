package uz.vohid.download

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.vohid.download.core.Downloader
import uz.vohid.download.ui.extractUrl

/**
 * Ko'rinmas «Tezkor yuklash» oynasi: havolani ulashish menyusidan (yoki ikonka shortcut'i orqali
 * buferdan) olib, ilovani ochmasdan sozlamalardagi standart format/sifat bilan orqa fonda yuklaydi.
 */
class QuickDownloadActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) {
            handle(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    // Bufer faqat oyna fokusga ega bo'lgandan keyin o'qiladi (Android 10+ cheklovi).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else {
                null
            }
            handle(text)
        }
    }

    private fun handle(text: String?) {
        if (handled) return
        handled = true
        val url = extractUrl(text)
        if (url == null) {
            Toast.makeText(this, R.string.quick_no_link, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.quick_started, Toast.LENGTH_SHORT).show()
            AppGraph.scope.launch {
                // Sovuq ishga tushishda sozlamalar yuklanishini kutamiz.
                val s = AppGraph.settings.flow.first()
                Downloader.enqueueQuick(url, s)
            }
        }
        finish()
    }
}
