package com.screenrefresh.controller.root

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
            .map { it.refreshRate.roundToInt() }
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
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "sh", "-c", "settings get global user_refresh_rate 2>/dev/null || " +
                "settings get global peak_refresh_rate 2>/dev/null || echo 0"
            ))
            val output = process.inputStream.bufferedReader().readText().trim()
            val rate = output.toFloatOrNull()
            if (rate != null && rate > 0 && supportedRates.contains(rate.roundToInt())) rate
            else supportedRates.firstOrNull()?.toFloat() ?: 60f
        } catch (_: Exception) {
            supportedRates.firstOrNull()?.toFloat() ?: 60f
        }
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
                } else emptyList()
            } catch (_: Exception) { emptyList() }
        }
    }
}
