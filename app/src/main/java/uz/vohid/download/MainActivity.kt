package uz.vohid.download

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.vohid.download.ui.MainViewModel
import uz.vohid.download.ui.UiEvent
import uz.vohid.download.ui.screens.DownloadsScreen
import uz.vohid.download.ui.screens.HomeScreen
import uz.vohid.download.ui.screens.SettingsScreen
import uz.vohid.download.ui.theme.VohidTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Qayta yaratilganda (ekran burilishi) bir xil intentni takror qayta ishlamaslik uchun.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            VohidTheme(mode = settings.theme, dynamicColor = settings.dynamicColor) {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> vm.receiveSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
            ACTION_OPEN_DOWNLOADS -> vm.selectTab(1)
        }
    }

    companion object {
        const val ACTION_OPEN_DOWNLOADS = "uz.vohid.download.OPEN_DOWNLOADS"
    }
}

private data class TabSpec(@StringRes val label: Int, val icon: ImageVector)

@Composable
private fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val tab by vm.tab.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val activeCount = items.count { it.status.isActive }
    val snackbar = remember { SnackbarHostState() }

    val tabs = remember {
        listOf(
            TabSpec(R.string.nav_home, Icons.Rounded.Home),
            TabSpec(R.string.nav_downloads, Icons.Rounded.Download),
            TabSpec(R.string.nav_settings, Icons.Rounded.Settings),
        )
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is UiEvent.Queued -> {
                    val result = snackbar.showSnackbar(
                        message = ctx.getString(R.string.queued_snack, event.count),
                        actionLabel = ctx.getString(R.string.snack_view),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.selectTab(1)
                }
                is UiEvent.Text -> snackbar.showSnackbar(event.text)
            }
        }
    }

    BackHandler(enabled = tab != 0) { vm.selectTab(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, spec ->
                    val label = stringResource(spec.label)
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { vm.selectTab(index) },
                        icon = {
                            if (index == 1 && activeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$activeCount") } }) {
                                    Icon(spec.icon, contentDescription = null)
                                }
                            } else {
                                Icon(spec.icon, contentDescription = null)
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            Crossfade(targetState = tab, label = "tabs") { t ->
                when (t) {
                    0 -> HomeScreen(vm)
                    1 -> DownloadsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
