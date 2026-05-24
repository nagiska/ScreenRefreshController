package com.screenrefresh.controller.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.screenrefresh.controller.R
import com.screenrefresh.controller.ScreenRefreshApp
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.RootExecutor
import com.screenrefresh.controller.root.Stepper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppDetectionService : AccessibilityService() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stepperJob: Job? = null
    private var currentPackage: String = ""

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        try {
            startForeground(1, buildNotification("监控中"))
        } catch (e: Exception) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm?.notify(1, buildNotification("SF失败: ${e.message}"))
        }
        scope.launch { RateController.scanModes() }
    }

    private var eventCount = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        eventCount++
        val pkg = event.packageName?.toString() ?: return
        if (pkg.isEmpty() || pkg == currentPackage) return
        currentPackage = pkg

        // Log to notification so user can see detection working
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(1, buildNotification("检测: $pkg (#$eventCount)"))

        checkApp(pkg)
    }

    private fun checkApp(pkg: String) {
        scope.launch {
            try {
                val db = ScreenRefreshApp.instance.db
                val entity = db.whitelistDao().getByPackage(pkg)
                if (entity != null) {
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    nm?.notify(1, buildNotification("${entity.appName} → ${entity.targetRate}Hz"))
                    startStepping(entity.targetRate)
                } else {
                    stopStepping()
                    RateController.resetTo120()
                }
            } catch (e: Exception) {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm?.notify(1, buildNotification("错误: ${e.message}"))
            }
        }
    }

    private suspend fun startStepping(targetRate: Int) {
        stepperJob?.cancel()
        val chain = Stepper.getChain(targetRate)
        stepperJob = scope.launch {
            try {
                for (rate in chain) {
                    RateController.setRate(rate)
                    if (rate != chain.last()) delay(2000)
                }
            } catch (e: Exception) {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm?.notify(1, buildNotification("步进失败: ${e.message}"))
            }
        }
    }

    private fun stopStepping() {
        stepperJob?.cancel()
        stepperJob = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        _isRunning.value = false
        scope.launch { RateController.resetTo120() }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "rate_service", "刷新率监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "监控白名单应用并自动调节刷新率" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, com.screenrefresh.controller.MainActivity::class.java)
        val pi = android.app.PendingIntent.getActivity(this, 0, openIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "rate_service")
                .setContentTitle("刷新率控制")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("刷新率控制")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        }
    }
}
