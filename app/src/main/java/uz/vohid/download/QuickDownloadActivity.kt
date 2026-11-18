package uz.vohid.download

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.vohid.download.core.Downloader
import uz.vohid.download.ui.extractUrl
import uz.vohid.download.ui.theme.VohidTheme

/**
 * Ulashish menyusidagi yagona «Vohid Download» nuqtasi.
 * Havola ulashilganda ikki tanlov chiqadi: fonda yuklash yoki ilovani ochish.
 * Ikonka shortcut'i (ACTION != SEND) esa buferdagi havolani darhol fonda yuklaydi.
 */
class QuickDownloadActivity : ComponentActivity() {

    private var handled = false
    private var sharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            handled = true
            setContent { Chooser() }
        }
    }

    // Shortcut: bufer faqat oyna fokusga ega bo'lgandan keyin o'qiladi (Android 10+ cheklovi).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else {
                null
            }
            startBackground(text)
        }
    }

    private fun startBackground(text: String?) {
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

    private fun openApp() {
        val open = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, sharedText)
        startActivity(open)
        finish()
    }

    @Composable
    private fun Chooser() {
        val s by AppGraph.settings.flow.collectAsState(initial = AppGraph.current)
        VohidTheme(mode = s.theme, dynamicColor = s.dynamicColor) {
            AlertDialog(
                onDismissRequest = { finish() },
                title = { Text(stringResource(R.string.app_name)) },
                text = {
                    Column {
                        Text(
                            text = extractUrl(sharedText) ?: sharedText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        ChoiceRow(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.share_bg),
                            subtitle = stringResource(R.string.share_bg_sub),
                            onClick = { startBackground(sharedText) },
                        )
                        ChoiceRow(
                            icon = Icons.Rounded.Home,
                            title = stringResource(R.string.share_open),
                            subtitle = stringResource(R.string.share_open_sub),
                            onClick = { openApp() },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { finish() }) { Text(stringResource(R.string.dialog_cancel)) }
                },
            )
        }
    }

    @Composable
    private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
