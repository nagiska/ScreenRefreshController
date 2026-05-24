package com.screenrefresh.controller.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.screenrefresh.controller.R
import com.screenrefresh.controller.ScreenRefreshApp
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.Stepper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppDetectionService : AccessibilityService() {

    companion object {
        val isRunning = MutableStateFlow(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stepperJob: Job? = null
    private var daemonJob: Job? = null
    private var lastPkg = ""
    private var curRate = 120

    // Floating window
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var rateText: TextView? = null
    private var rateBtns: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel("rate", "刷新率", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning.value = true
        try { startForeground(1, notif("监控中")) } catch (_: Exception) {}
        showOverlay()
        scope.launch {
            RateController.scanModes()
            updateOverlayRates(RateController.getAvailableRates())
            curRate = RateController.getCurrentRate(this@AppDetectionService)
            updateOverlayRate(curRate)
        }
        startDaemon()
    }

    // ── Floating window ──

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 8)
            setBackgroundColor(0xE6FFFFFF.toInt())
            alpha = 0.92f
        }

        rateText = TextView(this).apply {
            text = "120 Hz"
            textSize = 18f
            setTextColor(0xFF1677FF.toInt())
            setPadding(4, 0, 4, 2)
            setOnClickListener { curRate = 120; scope.launch { RateController.setRate(120) } }
        }
        overlayView?.addView(rateText)

        rateBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        overlayView?.addView(rateBtns)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0; y = 120
        }

        windowManager?.addView(overlayView, params)
    }

    private fun updateOverlayRate(rate: Int) {
        curRate = rate
        mainHandler.post {
            rateText?.text = "✓ $rate Hz"
        }
    }

    private fun updateOverlayRates(rates: List<Int>) {
        mainHandler.post {
            rateBtns?.removeAllViews()
            rates.sorted().forEach { rate ->
                val btn = TextView(this).apply {
                    text = "$rate"
                    textSize = 11f
                    setPadding(10, 6, 10, 6)
                    setBackgroundColor(0xFFEEEEEF.toInt())
                    setTextColor(0xFF1A1A1A.toInt())
                    setOnClickListener {
                        scope.launch {
                            RateController.setRate(rate)
                            val newRate = RateController.getCurrentRate(this@AppDetectionService)
                            updateOverlayRate(newRate)
                        }
                    }
                }
                rateBtns?.addView(btn)
            }
        }
    }

    private fun hideOverlay() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null
    }

    // ── Daemon ──

    private fun startDaemon() {
        daemonJob?.cancel()
        daemonJob = scope.launch {
            while (isActive) {
                try {
                    val rate = RateController.getCurrentRate(this@AppDetectionService)
                    if (rate != curRate) updateOverlayRate(rate)

                    val pkg = getForegroundPackage()
                    if (pkg != null && pkg != lastPkg) {
                        lastPkg = pkg
                        val entity = ScreenRefreshApp.instance.db.whitelistDao().getByPackage(pkg)
                        if (entity != null) {
                            stepTo(entity.targetRate)
                        } else {
                            stepperJob?.cancel()
                            RateController.resetTo120()
                        }
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private suspend fun getForegroundPackage(): String? {
        val r = RateController.suExec("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'")
        for (pat in listOf(
            Regex("""u0\s+([\w.]+)/"""),
            Regex("""([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+)/"""),
        )) {
            val m = pat.find(r.output)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private suspend fun stepTo(targetHz: Int) {
        stepperJob?.cancel()
        val chain = Stepper.getChain(targetHz)
        stepperJob = scope.launch {
            try {
                for (rate in chain) {
                    RateController.setRate(rate)
                    updateOverlayRate(RateController.getCurrentRate(this@AppDetectionService))
                    if (rate != chain.last()) delay(2000)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        daemonJob?.cancel()
        stepperJob?.cancel()
        hideOverlay()
        scope.launch { RateController.resetTo120() }
        isRunning.value = false
        super.onDestroy()
    }

    private fun notif(text: String): Notification {
        val pi = android.app.PendingIntent.getActivity(this, 0,
            Intent(this, com.screenrefresh.controller.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, "rate").setContentTitle("刷新率")
                .setContentText(text).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi).setOngoing(true).build()
        else @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("刷新率")
                .setContentText(text).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi).setOngoing(true).build()
    }
}
