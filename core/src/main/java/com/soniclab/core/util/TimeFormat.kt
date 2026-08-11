package com.soniclab.core.util

import java.util.Locale

object TimeFormat {
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
