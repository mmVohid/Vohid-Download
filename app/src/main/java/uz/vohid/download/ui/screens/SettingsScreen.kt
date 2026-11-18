@file:OptIn(ExperimentalMaterial3Api::class)

package uz.vohid.download.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.vohid.download.BuildConfig
import uz.vohid.download.R
import uz.vohid.download.data.Keys
import uz.vohid.download.data.Kind
import uz.vohid.download.data.STANDARD_HEIGHTS
import uz.vohid.download.data.ThemeMode
import uz.vohid.download.ui.MainViewModel

private enum class SettingsDialog { THEME, KIND, QUALITY, PARALLEL }

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val version by vm.ytdlpVersion.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        )

        // --- Ko'rinish
        GroupTitle(stringResource(R.string.set_appearance))
        val themeName = when (s.theme) {
            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
            ThemeMode.DARK -> stringResource(R.string.theme_dark)
        }
        SettingRow(
            icon = Icons.Rounded.DarkMode,
            title = stringResource(R.string.set_theme),
            subtitle = themeName,
            onClick = { dialog = SettingsDialog.THEME },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.set_dynamic),
                subtitle = stringResource(R.string.set_dynamic_sub),
                checked = s.dynamicColor,
                onChange = { vm.setPref(Keys.DYNAMIC, it) },
            )
        }

        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        // --- Yuklash
        GroupTitle(stringResource(R.string.set_downloads))
        SettingRow(
            icon = if (s.kind.isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.Videocam,
            title = stringResource(R.string.set_default_format),
            subtitle = s.kind.label,
            onClick = { dialog = SettingsDialog.KIND },
        )
        SettingRow(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.set_default_quality),
            subtitle = if (s.height == 0) stringResource(R.string.quality_best) else "${s.height}p",
            onClick = { dialog = SettingsDialog.QUALITY },
        )
        SettingRow(
            icon = Icons.Rounded.Speed,
            title = stringResource(R.string.set_parallel),
            subtitle = "${s.parallel}",
            onClick = { dialog = SettingsDialog.PARALLEL },
        )
        SwitchRow(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.set_metadata),
            subtitle = stringResource(R.string.set_metadata_sub),
            checked = s.embedMetadata,
            onChange = { vm.setPref(Keys.EMBED_META, it) },
        )
        SwitchRow(
            icon = Icons.Rounded.Audiotrack,
            title = stringResource(R.string.set_thumb),
            subtitle = stringResource(R.string.set_thumb_sub),
            checked = s.embedThumbnail,
            onChange = { vm.setPref(Keys.EMBED_THUMB, it) },
        )

        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        // --- Yuklovchi (yt-dlp)
        GroupTitle(stringResource(R.string.set_engine))
        SettingRow(
            icon = Icons.Rounded.SystemUpdate,
            title = stringResource(R.string.set_update),
            subtitle = stringResource(R.string.set_version, version ?: "—"),
            onClick = if (updating) null else ({ vm.updateEngine() }),
            trailing = if (updating) {
                ({ CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp) })
            } else {
                null
            },
        )
        SwitchRow(
            icon = Icons.Rounded.Update,
            title = stringResource(R.string.set_autoupdate),
            subtitle = stringResource(R.string.set_autoupdate_sub),
            checked = s.autoUpdate,
            onChange = { vm.setPref(Keys.AUTO_UPDATE, it) },
        )

        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        // --- Haqida
        GroupTitle(stringResource(R.string.set_about))
        SettingRow(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        )
        Text(
            text = stringResource(R.string.about_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.about_credits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.set_theme),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = s.theme,
            onSelect = { vm.setPref(Keys.THEME, it.name) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.KIND -> ChoiceDialog(
            title = stringResource(R.string.set_default_format),
            options = Kind.entries.map { it to it.label },
            selected = s.kind,
            onSelect = { vm.setPref(Keys.KIND, it.name) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.QUALITY -> ChoiceDialog(
            title = stringResource(R.string.set_default_quality),
            options = (listOf(0) + STANDARD_HEIGHTS).map {
                it to if (it == 0) stringResource(R.string.quality_best) else "${it}p"
            },
            selected = s.height,
            onSelect = { vm.setPref(Keys.HEIGHT, it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.PARALLEL -> ChoiceDialog(
            title = stringResource(R.string.set_parallel),
            options = listOf(1, 2, 3).map { it to "$it" },
            selected = s.parallel,
            onSelect = { vm.setPref(Keys.PARALLEL, it) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(value)
                                    onDismiss()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}
