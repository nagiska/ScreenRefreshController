package com.screenrefresh.controller.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val fps: Int
)

object DeviceConfig {

    suspend fun scanDisplayModesFromDumpsys(): List<DisplayModeInfo> = withContext(Dispatchers.IO) {
        val result = RootShell.executeCommand(
            "dumpsys display | grep 'DisplayModeRecord'"
        )
        if (!result.success) return@withContext emptyList()

        val pattern = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
        result.output.lines().mapNotNull { line ->
            pattern.find(line)?.let { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val w = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val h = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                val fps = match.groupValues[4].toFloatOrNull()?.toInt() ?: return@mapNotNull null
                if (fps in 30..300) DisplayModeInfo(id, w, h, fps) else null
            }
        }
    }

    suspend fun scanDrmModes(): List<Int> = withContext(Dispatchers.IO) {
        val result = RootShell.executeCommand(
            "cat /sys/class/drm/*/modes 2>/dev/null | grep -oE '@[0-9]+' | " +
            "grep -oE '[0-9]+' || true"
        )
        result.output.lines()
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 30..300 }
            .distinct()
            .sorted()
    }

    suspend fun parseDtboRefreshRates(): List<Int> = withContext(Dispatchers.IO) {
        try {
            // Method 1: Direct dtbo partition dump + string parsing
            val result = RootShell.executeCommand(
                "dd if=/dev/block/by-name/dtbo bs=1k count=1024 2>/dev/null | " +
                "strings | grep -oiE 'fps|FPS|refresh|rate' -A1 | " +
                "grep -oE '[0-9]{2,3}' || true"
            )
            if (result.success) {
                val rates = result.output.lines()
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 30..240 }
                    .distinct()
                    .sorted()
                if (rates.isNotEmpty()) return@withContext rates
            }

            // Method 2: Try reading drm modes
            val drmRates = scanDrmModes()
            if (drmRates.isNotEmpty()) return@withContext drmRates

            // Method 3: Read current FPS from sysfs
            val current = RootShell.executeCommand(
                "cat /sys/class/graphics/fb0/fps 2>/dev/null || " +
                "cat /sys/devices/virtual/graphics/fb0/fps 2>/dev/null || echo 60"
            )
            return@withContext listOf(current.output.trim().toIntOrNull() ?: 60)

        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getCurrentSysfsFps(): Int = withContext(Dispatchers.IO) {
        val result = RootShell.executeCommand(
            "cat /sys/class/graphics/fb0/fps 2>/dev/null || " +
            "cat /sys/devices/virtual/graphics/fb0/fps 2>/dev/null || echo 0"
        )
        result.output.trim().toIntOrNull() ?: 0
    }
}
