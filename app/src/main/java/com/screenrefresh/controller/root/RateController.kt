package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    private var hzToModeId: Map<Int, Int> = emptyMap()

    fun getAvailableRates(): List<Int> = hzToModeId.keys.sorted()

    suspend fun scanModes() = withContext(Dispatchers.IO) {
        val result = suExec("dumpsys display | grep 'DisplayModeRecord'")
        val pattern = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
        val map = mutableMapOf<Int, Int>()
        result.output.lines().forEach { line ->
            pattern.find(line)?.let { m ->
                val id  = m.groupValues[1].toIntOrNull() ?: return@let
                val fps = m.groupValues[4].toFloatOrNull()?.toInt() ?: return@let
                if (fps in 30..300) map[fps] = id
            }
        }
        hzToModeId = map
        Log.d(TAG, "modeMap: $map")
    }

    suspend fun getCurrentRate(ctx: android.content.Context): Int {
        return try {
            val dm = ctx.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            dm.getDisplay(0)?.refreshRate?.toInt() ?: 120
        } catch (_: Exception) { 120 }
    }

    /** Same as reference APK: su pipe, no stdin close */
    suspend fun suExec(script: String): RootExecutor.Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin  = DataOutputStream(process.outputStream)
            stdin.writeBytes("$script\nexit\n")
            stdin.flush()
            // DO NOT close stdin — reference APK doesn't, killing it breaks KSU
            process.waitFor()
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            RootExecutor.Result(out.isNotEmpty(), out, "", "su")
        } catch (e: Exception) {
            RootExecutor.Result(false, "", e.message ?: "err", "su")
        }
    }

    /** Same as reference APK's setRefreshRate(dumpsysModeId, targetHz) */
    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        val modeId = hzToModeId[targetHz]
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        val script = buildString {
            if (modeId != null) appendLine("service call SurfaceFlinger 1035 i32 ${modeId - 1}")
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
        }

        val result = suExec(script.trimEnd())
        entries.add(RootExecutor.DebugEntry("setRate",
            script.replace("\n"," → ").take(160),
            result.success, result.output.ifEmpty { "(empty)" }, "", 0, "su"))
        lastDebugEntries = entries
        result.success
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        scanModes()
        entries.add(RootExecutor.DebugEntry("modes", "scan", hzToModeId.isNotEmpty(),
            hzToModeId.toString().ifEmpty { "(empty)" }, "", 0, ""))
        entries.add(RootExecutor.DebugEntry("cur", "display", true,
            "using Display.getRefreshRate()", "", 0, ""))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        suExec("uname -r").output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
