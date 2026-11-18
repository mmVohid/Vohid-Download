@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package uz.vohid.download.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.vohid.download.R
import uz.vohid.download.core.EngineState
import uz.vohid.download.data.Kind
import uz.vohid.download.data.MediaEntry
import uz.vohid.download.data.MediaInfo
import uz.vohid.download.data.STANDARD_HEIGHTS
import uz.vohid.download.ui.MainViewModel
import uz.vohid.download.ui.Phase
import uz.vohid.download.ui.formatBytes
import uz.vohid.download.ui.formatDuration

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val state by vm.home.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val ready = engine is EngineState.Ready
    val info = state.info

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { Header() }

            item(key = "input") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = vm::onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.url_label)) },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        trailingIcon = {
                            if (state.url.isEmpty()) {
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.let { vm.onUrlChange(it) }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, stringResource(R.string.cd_paste))
                                }
                            } else {
                                IconButton(onClick = { vm.onUrlChange("") }) {
                                    Icon(Icons.Rounded.Clear, stringResource(R.string.cd_clear))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            focus.clearFocus()
                            vm.analyze()
                        }),
                        shape = RoundedCornerShape(20.dp),
                    )
                    Button(
                        onClick = {
                            focus.clearFocus()
                            vm.analyze()
                        },
                        enabled = state.url.isNotBlank() && state.phase != Phase.LOADING && ready,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (state.phase == Phase.LOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.loading_info))
                        } else {
                            Text(stringResource(R.string.btn_analyze))
                        }
                    }
                }
            }

            when (val e = engine) {
                is EngineState.Initializing -> item(key = "engine") {
                    Banner(
                        text = stringResource(R.string.engine_starting),
                        loading = true,
                    )
                }
                is EngineState.Failed -> item(key = "engine") {
                    Banner(
                        text = stringResource(R.string.engine_failed) + "\n" + e.message.take(160),
                        isError = true,
                        actionLabel = stringResource(R.string.btn_retry),
                        onAction = vm::retryEngine,
                    )
                }
                else -> Unit
            }

            when (state.phase) {
                Phase.IDLE -> item(key = "how") { HowItWorks() }
                Phase.LOADING -> item(key = "loading") { LoadingSkeleton() }
                Phase.ERROR -> item(key = "error") {
                    Banner(
                        text = state.error.orEmpty(),
                        isError = true,
                        actionLabel = stringResource(R.string.btn_retry),
                        onAction = { vm.analyze() },
                    )
                }
                Phase.READY -> if (info != null) {
                    item(key = "media") {
                        MediaCard(
                            info = info,
                            canTakeSingle = info.isPlaylist &&
                                (state.url.contains("v=") || state.url.contains("youtu.be/")),
                            onTakeSingle = { vm.analyze(noPlaylist = true) },
                        )
                    }
                    item(key = "options") {
                        OptionsCard(
                            info = info,
                            kind = state.kind,
                            height = state.height,
                            onKind = vm::setKind,
                            onHeight = vm::setHeight,
                        )
                    }
                    if (info.isPlaylist) {
                        item(key = "playlist_head") {
                            PlaylistHeader(
                                total = info.entries.size,
                                selected = state.selected.size,
                                onToggleAll = vm::toggleAll,
                            )
                        }
                        items(info.entries, key = { it.id }) { entry ->
                            EntryRow(
                                entry = entry,
                                checked = entry.id in state.selected,
                                isAudio = state.kind.isAudio,
                                onToggle = { vm.toggleEntry(entry.id) },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.phase == Phase.READY && info != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val quality = if (state.kind.isAudio || state.height == 0) {
                        null
                    } else {
                        "${state.height}p"
                    }
                    Text(
                        text = listOfNotNull(state.kind.label, quality).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val count = state.selected.size
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val needsAsk = Build.VERSION.SDK_INT >= 33 &&
                                !settings.notifAsked &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            if (needsAsk) {
                                vm.markNotifAsked()
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.download()
                        },
                        enabled = count > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (info?.isPlaylist == true) {
                                stringResource(R.string.btn_download_n, count)
                            } else {
                                stringResource(R.string.btn_download)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = content)
            } else if (isError) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.how_title), style = MaterialTheme.typography.titleMedium)
            listOf(R.string.how_1, R.string.how_2, R.string.how_3).forEachIndexed { index, res ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = stringResource(R.string.how_sites),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    val description = stringResource(R.string.loading_info)
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column(
        modifier = Modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(color),
        )
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
    }
}

@Composable
private fun MediaCard(
    info: MediaInfo,
    canTakeSingle: Boolean,
    onTakeSingle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Thumbnail(
            url = info.thumbnail,
            isAudio = false,
            durationSec = info.durationSec,
            corner = 0,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = if (info.isPlaylist) {
                stringResource(R.string.playlist_count, info.entries.size)
            } else {
                info.uploader.orEmpty()
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canTakeSingle) {
                TextButton(onClick = onTakeSingle) { Text(stringResource(R.string.only_this_video)) }
            }
        }
    }
}

@Composable
private fun OptionsCard(
    info: MediaInfo,
    kind: Kind,
    height: Int,
    onKind: (Kind) -> Unit,
    onHeight: (Int) -> Unit,
) {
    val isAudio = kind.isAudio
    val heights = remember(info) {
        val available = if (info.heights.isEmpty()) STANDARD_HEIGHTS else STANDARD_HEIGHTS.filter { it <= info.maxHeight }
        listOf(0) + available
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(stringResource(R.string.section_format))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isAudio,
                    onClick = { if (isAudio) onKind(Kind.VIDEO_MP4) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.kind_video)) },
                )
                SegmentedButton(
                    selected = isAudio,
                    onClick = { if (!isAudio) onKind(Kind.AUDIO_MP3) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.kind_audio)) },
                )
            }

            val formats = if (isAudio) listOf(Kind.AUDIO_MP3, Kind.AUDIO_M4A) else listOf(Kind.VIDEO_MP4, Kind.VIDEO_MKV)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { onKind(k) },
                        label = { Text(k.label) },
                    )
                }
            }

            AnimatedVisibility(visible = !isAudio) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.section_quality))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        heights.forEach { h ->
                            val name = if (h == 0) stringResource(R.string.quality_best) else "${h}p"
                            val size = info.estimatedSize(h)?.let { formatBytes(it) }
                            FilterChip(
                                selected = height == h,
                                onClick = { onHeight(h) },
                                label = { Text(if (size.isNullOrBlank()) name else "$name · ~$size") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(total: Int, selected: Int, onToggleAll: () -> Unit) {
    val state = when {
        selected == 0 -> ToggleableState.Off
        selected == total -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox, onClick = onToggleAll)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = null, modifier = Modifier.padding(12.dp))
        Text(
            text = stringResource(R.string.select_all),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.selected_count, selected, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(entry: MediaEntry, checked: Boolean, isAudio: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp, end = 4.dp))
        Thumbnail(
            url = entry.thumbnail,
            isAudio = isAudio,
            durationSec = entry.durationSec,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = entry.uploader ?: entry.durationSec?.let { formatDuration(it) }
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
