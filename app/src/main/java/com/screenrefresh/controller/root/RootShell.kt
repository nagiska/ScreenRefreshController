package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object RootShell {

    private const val TAG = "RootShell"

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            !line.isNullOrBlank()
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

    suspend fun executeCommands(commands: List<String>): List<ShellResult> = withContext(Dispatchers.IO) {
        commands.map { executeCommand(it) }
    }
}

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String
)
