package com.sortsnap.domain

import java.util.Locale

object Formatting {
    /** "812 KB", "4.2 MB", "1.3 GB". Uses 1024-based units like Android's Files app. */
    fun bytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return if (value >= 100 || unit == 0) {
            String.format(locale, "%.0f %s", value, units[unit])
        } else {
            String.format(locale, "%.1f %s", value, units[unit])
        }
    }

    fun percent(confidence: Float): String = "${(confidence.coerceIn(0f, 1f) * 100).toInt()}%"
}
