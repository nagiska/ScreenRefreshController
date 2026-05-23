package com.screenrefresh.controller.root

import android.util.Log

data class DisplayMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val fps: Int
)

object DisplayModes {

    private const val TAG = "DisplayModes"
    private const val DUMPSYS_CMD = "dumpsys display 2>/dev/null | grep 'DisplayModeRecord'"
    private val RECORD_PATTERN = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")

    suspend fun scanModes(): List<DisplayMode> {
        val result = RootExecutor.execute(DUMPSYS_CMD)
        if (!result.success || result.output.isBlank()) {
            Log.w(TAG, "dumpsys display failed or empty")
            return emptyList()
        }

        val modes = mutableListOf<DisplayMode>()
        for (line in result.output.lines()) {
            val match = RECORD_PATTERN.find(line)
            if (match != null) {
                val id = match.groupValues[1].toIntOrNull() ?: continue
                val w = match.groupValues[2].toIntOrNull() ?: continue
                val h = match.groupValues[3].toIntOrNull() ?: continue
                val fps = match.groupValues[4].toFloatOrNull()?.toInt() ?: continue
                if (fps in 30..300) modes.add(DisplayMode(id, w, h, fps))
            }
        }
        return modes.sortedBy { it.id }
    }

    fun findModeId(modes: List<DisplayMode>, targetFps: Int): Int? {
        return modes.firstOrNull { it.fps == targetFps }?.id
    }
}
