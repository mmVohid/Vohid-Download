package uz.vohid.download

import android.app.Application
import uz.vohid.download.core.Downloader
import uz.vohid.download.core.Engine
import uz.vohid.download.core.Notifications

class VohidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.ensureChannels(this)
        Downloader.init(this)
        Engine.start(this)
    }
}
