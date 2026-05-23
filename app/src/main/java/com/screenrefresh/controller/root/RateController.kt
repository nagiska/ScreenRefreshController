package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    /** display Hz → miui_refresh_rate value needed */
    private val fwd = mapOf(120 to 120, 132 to 144, 144 to 156, 156 to 165, 165 to 165)
    /** miui_refresh_rate value → display Hz */
    private val rev = mapOf(120 to 120, 144 to 132, 156 to 144, 165 to 156)

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        val raw = r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
        rev[raw] ?: raw
    }

    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        val setVal = fwd[targetHz] ?: targetHz
        Log.d(TAG, "setRate $targetHz → miui=$setVal")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        entries.add(RootExecutor.executeWithDebug("miui",  "settings put secure miui_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("peak",  "settings put secure peak_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("user",  "settings put secure user_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("s-pk",  "settings put system peak_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("s-mi",  "settings put system min_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("s-mx",  "settings put system max_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("s-ur",  "settings put system user_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("g-mi",  "settings put global min_refresh_rate $setVal"))
        entries.add(RootExecutor.executeWithDebug("g-mx",  "settings put global max_refresh_rate $setVal"))

        // For 165Hz: extra attempt via oneplus key + SF modeId guess
        if (targetHz == 165) {
            entries.add(RootExecutor.executeWithDebug("op-165",
                "settings put system oneplus_screen_refresh_rate 165"))
            for (guess in listOf(1, 5, 6, 7)) {
                entries.add(RootExecutor.executeWithDebug("sf-$guess",
                    "service call SurfaceFlinger 1035 i32 $guess"))
            }
        }

        entries.add(RootExecutor.executeWithDebug("verify", "settings get secure miui_refresh_rate"))
        val ok = entries.any { it.success }
        lastDebugEntries = entries
        ok
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("cur-miui",
            "settings get secure miui_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("cur-peak",
            "settings get secure peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("mi-modes",
            "dumpsys display 2>/dev/null | grep -oE 'modeId [0-9]+|fps=[0-9.]+'"))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }
    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }
    fun clearDebug() { lastDebugEntries = emptyList() }
}
