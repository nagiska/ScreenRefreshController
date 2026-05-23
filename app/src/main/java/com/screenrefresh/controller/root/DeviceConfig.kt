package com.screenrefresh.controller.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceConfig {

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

            // Method 2: Try reading from sysfs available modes
            val sysfs = RootShell.executeCommand(
                "cat /sys/class/drm/*/modes 2>/dev/null | grep -oE '@[0-9]+' | " +
                "grep -oE '[0-9]+' || true"
            )
            if (sysfs.success) {
                val rates = sysfs.output.lines()
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 30..240 }
                    .distinct()
                    .sorted()
                if (rates.isNotEmpty()) return@withContext rates
            }

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
