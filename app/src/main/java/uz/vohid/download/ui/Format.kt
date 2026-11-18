package uz.vohid.download.ui

import java.util.Locale

fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return when {
        i == 0 -> "$bytes B"
        v >= 100 -> String.format(Locale.US, "%.0f %s", v, units[i])
        else -> String.format(Locale.US, "%.1f %s", v, units[i])
    }
}
