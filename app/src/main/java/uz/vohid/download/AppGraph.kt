package uz.vohid.download

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.vohid.download.data.AppSettings
import uz.vohid.download.data.SettingsRepository

/** Ilova darajasidagi umumiy obyektlar (oddiy DI o'rniga). */
object AppGraph {
    lateinit var app: Application
        private set
    lateinit var settings: SettingsRepository
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Sozlamalarning oxirgi holati (fon oqimlari uchun sinxron o'qish). */
    @Volatile
    var current: AppSettings = AppSettings()
        private set

    fun init(application: Application) {
        app = application
        settings = SettingsRepository(application)
        scope.launch { settings.flow.collect { current = it } }
    }
}
