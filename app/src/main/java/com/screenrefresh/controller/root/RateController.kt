package com.screenrefresh.controller.root

import android.content.Context
import android.hardware.display.DisplayManager
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

    /** Same as reference APK: dumpsys display → grep DisplayModeRecord → regex parse */
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

    /** Same as reference APK: DisplayManager.getDisplay(0).getRefreshRate() */
    suspend fun getCurrentRate(ctx: Context): Int = withContext(Dispatchers.IO) {
        try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.getDisplay(0)?.refreshRate?.toInt() ?: 120
        } catch (_: Exception) { 120 }
    }

    /** Same as reference APK's setRefreshRate(dumpsysModeId, targetHz) */
    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        val modeId = hzToModeId[targetHz]
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        val script = buildString {
            if (modeId != null) {
                val index = modeId - 1   // CRITICAL: reference APK uses modeId - 1
                appendLine("service call SurfaceFlinger 1035 i32 $index")
            }
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
        }

        val result = suExec(script.trimEnd())
        entries.add(RootExecutor.DebugEntry(
            "setRate", script.replace("\n", " → ").take(200),
            result.success, result.output.ifEmpty { "(empty)" },
            result.error.ifEmpty { "(none)" }, 0, "su"
        ))

        lastDebugEntries = entries
        result.success
    }

    /** Persistent su pipe — same as reference APK */
    private suspend fun suExec(command: String): RootExecutor.Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin  = DataOutputStream(process.outputStream)
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            stdin.writeBytes("$command\nexit\n")
            stdin.flush()
            stdin.close()

            val out = stdout.readText().trim()
            val err = stderr.readText().trim()
            process.waitFor()
            RootExecutor.Result(out.isNotEmpty() || err.isEmpty(), out, err, "su")
        } catch (e: Exception) {
            RootExecutor.Result(false, "", e.message ?: "err", "su")
        }
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        scanModes()
        entries.add(RootExecutor.DebugEntry("modes", "dumpsys scan", hzToModeId.isNotEmpty(),
            hzToModeId.toString().ifEmpty { "(empty)" }, "", 0, ""))
        entries.add(RootExecutor.DebugEntry("cur", "settings get", true,
            suExec("settings get secure miui_refresh_rate").output.trim(), "", 0, ""))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        suExec("uname -r").output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
