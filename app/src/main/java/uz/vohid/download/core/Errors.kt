package uz.vohid.download.core

import android.content.Context
import uz.vohid.download.R

/** yt-dlp stderr matnidan oxirgi "ERROR:" qatorini ajratib oladi. */
fun cleanError(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return ""
    val lines = text.lines()
    val line = lines.lastOrNull { it.contains("ERROR:") }
        ?: lines.lastOrNull { it.isNotBlank() }
        ?: return ""
    return line.substringAfter("ERROR:")
        .trim()
        .replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
        .take(300)
}

/** Texnik xatoni foydalanuvchiga tushunarli qisqa matnga aylantiradi. */
fun friendlyError(ctx: Context, raw: String?): String {
    val cleaned = cleanError(raw)
    val l = (raw.orEmpty() + " " + cleaned).lowercase()
    val resId = when {
        "unsupported url" in l -> R.string.err_unsupported
        "confirm you" in l && "bot" in l -> R.string.err_bot
        "private video" in l || "login required" in l ||
            "members-only" in l || "members only" in l -> R.string.err_login
        "confirm your age" in l || "age-restricted" in l || "age restricted" in l -> R.string.err_age
        "not available in your country" in l || "blocked it in your country" in l ||
            ("geo" in l && "restrict" in l) -> R.string.err_geo
        "video unavailable" in l || "has been removed" in l ||
            "no longer available" in l || "does not exist" in l -> R.string.err_unavailable
        "http error 429" in l || "too many requests" in l -> R.string.err_rate
        "requested format is not available" in l -> R.string.err_format
        "urlopen error" in l || "name or service not known" in l ||
            "temporary failure in name resolution" in l || "network is unreachable" in l ||
            "timed out" in l || "unable to download webpage" in l -> R.string.err_network
        else -> null
    }
    if (resId != null) return ctx.getString(resId)
    return cleaned.ifBlank { ctx.getString(R.string.err_generic) }
}
