@file:OptIn(ExperimentalMaterial3Api::class)

package uz.vohid.download.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.vohid.download.R
import uz.vohid.download.data.DownloadItem
import uz.vohid.download.data.Status
import uz.vohid.download.ui.MainViewModel
import uz.vohid.download.ui.formatBytes
import uz.vohid.download.ui.formatDuration

@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val active = remember(items) { items.filter { it.status.isActive }.sortedBy { it.createdAt } }
    val finished = remember(items) {
        items.filter { !it.status.isActive }.sortedByDescending { maxOf(it.finishedAt, it.createdAt) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (active.isNotEmpty()) {
                TextButton(onClick = vm::cancelAll) { Text(stringResource(R.string.cancel_all)) }
            }
            if (finished.isNotEmpty()) {
                IconButton(onClick = vm::clearFinished) {
                    Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_finished))
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.CloudDownload,
                title = stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (active.isNotEmpty()) {
                    item(key = "h_active") { SectionLabel(stringResource(R.string.section_active)) }
                    items(active, key = { it.id }) { DownloadCard(it, vm) }
                }
                if (finished.isNotEmpty()) {
                    item(key = "h_finished") { SectionLabel(stringResource(R.string.section_finished)) }
                    items(finished, key = { it.id }) { DownloadCard(it, vm) }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, vm: MainViewModel) {
    var menu by remember { mutableStateOf(false) }
    val completed = item.status == Status.COMPLETED
    val onClick: () -> Unit = { if (completed) vm.open(item) }

    Card(
        onClick = onClick,
        enabled = completed,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(
                url = item.thumbnail,
                isAudio = item.kind.isAudio,
                durationSec = item.durationSec,
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(16f / 9f),
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val failed = item.status == Status.FAILED
                Text(
                    text = statusText(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when (item.status) {
                    Status.QUEUED, Status.SAVING -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                    )
                    Status.RUNNING -> if (item.progress <= 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape),
                        )
                    }
                    else -> Unit
                }
            }

            when (item.status) {
                Status.QUEUED, Status.RUNNING -> IconButton(onClick = { vm.cancel(item) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.cd_cancel))
                }
                Status.FAILED, Status.CANCELED -> IconButton(onClick = { vm.retry(item) }) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.cd_retry))
                }
                Status.COMPLETED -> IconButton(onClick = { vm.open(item) }) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.menu_open))
                }
                Status.SAVING -> Spacer(Modifier.width(8.dp))
            }

            Column {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (completed) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_open)) },
                            onClick = { menu = false; vm.open(item) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_share)) },
                            onClick = { menu = false; vm.share(item) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_link)) },
                        onClick = { menu = false; vm.copyLink(item) },
                    )
                    if (!item.status.isActive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_remove)) },
                            onClick = { menu = false; vm.remove(item, deleteFile = false) },
                        )
                        if (completed) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.menu_delete_file),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = { menu = false; vm.remove(item, deleteFile = true) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(item: DownloadItem): String = when (item.status) {
    Status.QUEUED -> stringResource(R.string.status_queued)
    Status.RUNNING -> {
        val eta = if (item.etaSec > 0) stringResource(R.string.status_eta, formatDuration(item.etaSec)) else null
        listOfNotNull("${(item.progress * 100).toInt()}%", item.speed, eta).joinToString(" · ")
    }
    Status.SAVING -> stringResource(R.string.status_saving)
    Status.COMPLETED -> {
        val quality = if (!item.kind.isAudio && item.height > 0) "${item.height}p" else null
        val size = formatBytes(item.sizeBytes).ifBlank { null }
        listOfNotNull(item.kind.label, quality, size).joinToString(" · ")
    }
    Status.FAILED -> item.message ?: stringResource(R.string.err_generic)
    Status.CANCELED -> stringResource(R.string.status_canceled)
}
