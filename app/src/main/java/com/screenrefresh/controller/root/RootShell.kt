package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object RootShell {

    private const val TAG = "RootShell"

    data class ShellDebugEntry(
        val method: String,
        val command: String,
        val success: Boolean,
        val output: String,
        val error: String
    )

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val methods = listOf(
                arrayOf("which", "su"),
                arrayOf("/system/xbin/su", "--version"),
                arrayOf("/system/bin/su", "--version")
            )
            for (cmd in methods) {
                val p = Runtime.getRuntime().exec(cmd)
                val r = BufferedReader(InputStreamReader(p.inputStream))
                val line = r.readLine()
                p.waitFor()
                if (!line.isNullOrBlank()) return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    suspend fun executeCommand(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
            val output = stdout.readText().trim()
            val error = stderr.readText().trim()
            val exitCode = process.waitFor()
            ShellResult(exitCode == 0, output, error)
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
            ShellResult(false, "", e.message ?: "Unknown error")
        }
    }

    suspend fun executeCommandWithDebug(
        label: String,
        command: String
    ): ShellDebugEntry = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
            val output = stdout.readText().trim()
            val error = stderr.readText().trim()
            val exitCode = process.waitFor()
            val elapsed = System.currentTimeMillis() - startTime
            ShellDebugEntry(
                method = "$label (${elapsed}ms)",
                command = command,
                success = exitCode == 0,
                output = output.ifEmpty { "(empty)" },
                error = error.ifEmpty { "(none)" }
            )
        } catch (e: Exception) {
            ShellDebugEntry(
                method = label,
                command = command,
                success = false,
                output = "",
                error = e.message ?: "Unknown error"
            )
        }
    }
}

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String
)
