package com.screenrefresh.controller.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.screenrefresh.controller.R
import com.screenrefresh.controller.data.AppDatabase
import com.screenrefresh.controller.data.WhitelistEntity
import com.screenrefresh.controller.root.DeviceConfig
import com.screenrefresh.controller.root.RefreshRateController
import com.screenrefresh.controller.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppDetectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppDetectionService"
        private const val CHANNEL_ID = "refresh_rate_control"
        private const val NOTIFICATION_ID = 1001

        val isRunning = MutableStateFlow(false)
        val currentForegroundApp = MutableStateFlow("")

        private var instance: AppDetectionAccessibilityService? = null
        fun getInstance(): AppDetectionAccessibilityService? = instance
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshController = RefreshRateController()
    private lateinit var database: AppDatabase

    private var availableRates: List<Int> = emptyList()
    private var currentDetectedApp: String = ""
    private var isProcessing = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)

        createNotificationChannel()
        scope.launch {
            initializeService()
        }
    }

    private suspend fun initializeService() {
        val hasRoot = RootShell.isRootAvailable()
        if (!hasRoot) {
            Log.w(TAG, "Root not available")
            return
        }

        refreshController.initDefaultRate()

        val config = DeviceConfig.detectRefreshRates(this)
        availableRates = DeviceConfig.getAvailableTiers(config.supportedRates)

        if (availableRates.isEmpty()) {
            availableRates = config.supportedRates.filter { it >= 60 }
        }

        Log.d(TAG, "Available rates: $availableRates")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning.value = true
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (isProcessing) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == currentDetectedApp) return
        if (packageName.startsWith("com.screenrefresh.controller")) return

        currentDetectedApp = packageName
        currentForegroundApp.value = packageName

        scope.launch {
            handleAppChange(packageName)
        }
    }

    private suspend fun handleAppChange(packageName: String) {
        isProcessing = true
        try {
            val isWhite = database.whitelistDao().isWhitelisted(packageName)

            if (isWhite) {
                val appName = getAppName(packageName)
                Log.d(TAG, "Whitelisted app opened: $appName ($packageName)")
                startRefreshRateStepping()
            } else {
                if (refreshController.isStepping.value) {
                    refreshController.stopStepping()
                    refreshController.resetToDefault()
                }
            }
        } finally {
            isProcessing = false
        }
    }

    private suspend fun startRefreshRateStepping() {
        if (availableRates.isEmpty()) return

        val intervalMs = loadStepInterval()
        refreshController.startStepping(
            scope = scope,
            availableRates = availableRates,
            stepIntervalMs = intervalMs,
            onStep = { rate ->
                Log.d(TAG, "Stepped to ${rate}Hz")
            },
            onComplete = {
                Log.d(TAG, "Stepping complete")
            },
            onError = { err ->
                Log.e(TAG, "Stepping error: $err")
            }
        )
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val app = pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
            app
        } catch (e: Exception) {
            packageName
        }
    }

    private fun loadStepInterval(): Long {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getLong("step_interval_ms", 3000L)
    }

    fun resetToDefaultRefreshRate() {
        scope.launch {
            refreshController.stopStepping()
            refreshController.resetToDefault()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning.value = false
        refreshController.stopStepping()
        scope.launch {
            refreshController.resetToDefault()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
