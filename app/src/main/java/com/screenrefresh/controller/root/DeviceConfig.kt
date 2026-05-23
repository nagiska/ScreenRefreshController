package com.screenrefresh.controller.root

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceConfig {

    data class RefreshRateInfo(
        val supportedRates: List<Int>,
        val currentRate: Float,
        val defaultRate: Float
    )

    suspend fun detectRefreshRates(context: Context): RefreshRateInfo {
        val display = getDefaultDisplay(context)
        val supportedModes = display?.supportedModes ?: emptyArray()
        val rates = supportedModes
            .map { it.refreshRate.toInt() }
            .distinct()
            .sorted()

        val currentRate = display?.refreshRate ?: 60f

        val defaultRate = detectDefaultRate(context, rates)

        return RefreshRateInfo(
            supportedRates = rates,
            currentRate = currentRate,
            defaultRate = defaultRate
        )
    }

    private fun getDefaultDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return dm?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private fun detectDefaultRate(context: Context, supportedRates: List<Int>): Float {
        val defaultSysfs = readSysfsDefault()
        if (defaultSysfs > 0) return defaultSysfs.toFloat()

        val defaultSettings = readSettingsDefault()
        if (defaultSettings > 0) return defaultSettings.toFloat()

        return supportedRates.firstOrNull()?.toFloat() ?: 60f
    }

    private fun readSysfsDefault(): Int {
        val paths = listOf(
            "/sys/class/graphics/fb0/fps",
            "/sys/devices/virtual/graphics/fb0/fps"
        )
        for (path in paths) {
            try {
                val content = java.io.File(path).readText().trim().toIntOrNull()
                if (content != null && content > 0) return content
            } catch (_: Exception) {}
        }
        return 0
    }

    private fun readSettingsDefault(): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "sh", "-c", "settings get global user_refresh_rate 2>/dev/null || echo 0"
            ))
            val output = process.inputStream.bufferedReader().readText().trim()
            output.toFloatOrNull()?.toInt() ?: 0
        } catch (_: Exception) { 0 }
    }

    suspend fun parseDtboRefreshRates(): List<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val result = RootShell.executeCommand(
                    "dd if=/dev/block/by-name/dtbo bs=1k count=512 2>/dev/null | strings | grep -iE 'fps|rate|refresh' | grep -oE '[0-9]{2,3}' || true"
                )
                if (result.success) {
                    result.output.lines()
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 30..240 }
                        .distinct()
                        .sorted()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
