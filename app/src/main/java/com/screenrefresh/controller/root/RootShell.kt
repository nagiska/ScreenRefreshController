package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
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

    // Execute commands via persistent su shell (write to stdin)
    private suspend fun suExec(script: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            stdin.writeBytes("$script\n")
            stdin.writeBytes("echo __EXIT__$?\n")
            stdin.writeBytes("exit\n")
            stdin.flush()
            stdin.close()

            val output = stdout.readText().trim()
            val error = stderr.readText().trim()
            process.waitFor()

            val exitMarker = output.substringAfterLast("__EXIT__").trim()
            val exitCode = exitMarker.toIntOrNull() ?: -1
            val cleanOutput = output.substringBeforeLast("__EXIT__").trim()

            ShellResult(exitCode == 0, cleanOutput, error)
        } catch (e: Exception) {
            Log.e(TAG, "su exec failed: $script", e)
            ShellResult(false, "", e.message ?: "error")
        }
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val line = r.readLine()
            p.waitFor()
            !line.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    suspend fun executeCommand(command: String): ShellResult = suExec(command)

    suspend fun executeCommandWithDebug(
        label: String,
        command: String
    ): ShellDebugEntry = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val result = suExec(command)
            val elapsed = System.currentTimeMillis() - startTime
            ShellDebugEntry(
                method = "$label (${elapsed}ms)",
                command = command,
                success = result.success,
                output = result.output.ifEmpty { "(empty)" },
                error = result.error.ifEmpty { "(none)" }
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
