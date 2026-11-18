package uz.vohid.download.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val kind: Kind = Kind.VIDEO_MP4,
    /** 0 — eng yaxshi. */
    val height: Int = 0,
    val parallel: Int = 2,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = false,
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val notifAsked: Boolean = false,
)

object Keys {
    val THEME = stringPreferencesKey("theme")
    val DYNAMIC = booleanPreferencesKey("dynamic_color")
    val KIND = stringPreferencesKey("kind")
    val HEIGHT = intPreferencesKey("height")
    val PARALLEL = intPreferencesKey("parallel")
    val EMBED_META = booleanPreferencesKey("embed_metadata")
    val EMBED_THUMB = booleanPreferencesKey("embed_thumbnail")
    val AUTO_UPDATE = booleanPreferencesKey("auto_update")
    val LAST_UPDATE = longPreferencesKey("last_update_check")
    val NOTIF_ASKED = booleanPreferencesKey("notif_asked")
}

class SettingsRepository(private val context: Context) {

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.theme,
            dynamicColor = p[Keys.DYNAMIC] ?: defaults.dynamicColor,
            kind = p[Keys.KIND]?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
                ?: defaults.kind,
            height = p[Keys.HEIGHT] ?: defaults.height,
            parallel = (p[Keys.PARALLEL] ?: defaults.parallel).coerceIn(1, 3),
            embedMetadata = p[Keys.EMBED_META] ?: defaults.embedMetadata,
            embedThumbnail = p[Keys.EMBED_THUMB] ?: defaults.embedThumbnail,
            autoUpdate = p[Keys.AUTO_UPDATE] ?: defaults.autoUpdate,
            lastUpdateCheck = p[Keys.LAST_UPDATE] ?: defaults.lastUpdateCheck,
            notifAsked = p[Keys.NOTIF_ASKED] ?: defaults.notifAsked,
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
