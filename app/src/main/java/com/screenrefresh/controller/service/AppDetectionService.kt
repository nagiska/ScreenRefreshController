package com.screenrefresh.controller.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppDetectionService : AccessibilityService() {

    companion object {
        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning
        private const val ACTION_TOGGLE = "com.screenrefresh.TOGGLE_OVERLAY"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var manualOverride = false
    private val handler = Handler(Looper.getMainLooper())
    private var stepperJob: Job? = null
    private var daemonJob: Job? = null
    private var lastPkg = ""
    private var lastTargetHz = 120
    private var curRate = 120

    // Floating window
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var overlayVisible = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (overlayVisible) hideOverlay() else showOverlay()
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel("rate", "刷新率", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(toggleReceiver, IntentFilter(ACTION_TOGGLE), RECEIVER_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(toggleReceiver, IntentFilter(ACTION_TOGGLE))
        } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        try { startForeground(1, buildToggleNotification()) } catch (_: Exception) {}
        scope.launch {
            RateController.scanModes()
            curRate = RateController.getCurrentRate(this@AppDetectionService)
        }
        startDaemon()
    }

    // ── Notification ──

    private fun buildToggleNotification(): Notification {
        val toggleIntent = Intent(ACTION_TOGGLE).setPackage(packageName)
        val pi = PendingIntent.getBroadcast(this, 0, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openIntent = PendingIntent.getActivity(this, 1,
            Intent(this, com.screenrefresh.controller.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, "rate").setContentTitle("刷新率控制")
                .setContentText(if (overlayVisible) "悬浮窗已开启 · 点击关闭" else "点击开启悬浮窗")
                .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true)
                .setContentIntent(openIntent).addAction(0,
                    if (overlayVisible) "关闭浮窗" else "开启浮窗", pi).build()
        else @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("刷新率控制")
                .setContentText(if (overlayVisible) "悬浮窗已开启 · 点击关闭" else "点击开启悬浮窗")
                .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true)
                .setContentIntent(openIntent).addAction(0,
                    if (overlayVisible) "关闭浮窗" else "开启浮窗", pi).build()
    }

    // ── Floating window ──

    private fun showOverlay() {
        if (overlayVisible) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val rateText = TextView(this).apply {
            text = "✓ $curRate Hz"; textSize = 18f
            setTextColor(0xFF1677FF.toInt()); setPadding(8, 6, 8, 2)
        }
        val buttonsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val closeBtn = TextView(this).apply {
            text = "✕"; textSize = 14f; setPadding(14, 6, 14, 6)
            setTextColor(0xFFFF4D4F.toInt()); setOnClickListener { hideOverlay() }
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 6)
            setBackgroundColor(0xE6FFFFFF.toInt()); alpha = 0.93f
        }
        overlayView?.addView(rateText)
        overlayView?.addView(buttonsRow)
        overlayView?.addView(closeBtn)

        updateOverlayRates(buttonsRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 200 }

        // Drag support
        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(overlayView, params)
        overlayVisible = true
        try { startForeground(1, buildToggleNotification()) } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null; overlayVisible = false
        try { startForeground(1, buildToggleNotification()) } catch (_: Exception) {}
    }

    private fun updateOverlayRateRaw(rate: Int) {
        curRate = rate
        if (!overlayVisible) return
        handler.post {
            (overlayView?.getChildAt(0) as? TextView)?.text = "✓ $rate Hz"
            val row = overlayView?.getChildAt(1) as? LinearLayout
            row?.let { updateOverlayRates(it) }
        }
    }

    private fun updateOverlayRates(row: LinearLayout) {
        row.removeAllViews()
        val rates = RateController.getAvailableRates()
        rates.sorted().forEach { rate ->
            val btn = TextView(this).apply {
                text = "$rate"; textSize = 11f; setPadding(10, 6, 10, 6)
                setBackgroundColor(if (rate == curRate) 0xFF1677FF.toInt() else 0xFFEEEEEF.toInt())
                setTextColor(if (rate == curRate) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
                setOnClickListener {
                    scope.launch {
                        manualOverride = true
                        RateController.setRate(rate)
                        // Read back from settings for consistency
                        delay(300)
                        val r = RateController.suExec("settings get secure miui_refresh_rate")
                        val raw = r.output.trim().toFloatOrNull()?.toInt() ?: rate
                        curRate = raw; updateOverlayRateRaw(raw)
                        delay(2000)
                        manualOverride = false
                    }
                }
            }
            row.addView(btn)
        }
    }

    // ── Daemon (foreground app detection + auto-step) ──

    private var steppingActive = false
    private var stepDir = 0 // 0=none, 1=up, -1=down
    private var currentStepChain = emptyList<Int>()
    private var currentStepIdx = -1

    private fun startDaemon() {
        daemonJob?.cancel()
        daemonJob = scope.launch {
            while (isActive) {
                try {
                    // Skip daemon work during manual override
                    if (manualOverride) { delay(2000); continue }

                    val rate = RateController.suExec("settings get secure miui_refresh_rate")
                    val raw = rate.output.trim().toFloatOrNull()?.toInt() ?: curRate
                    if (raw != curRate) updateOverlayRateRaw(raw)

                    val pkg = getForegroundPackage()
                    if (pkg != null && pkg != lastPkg) {
                        val oldPkg = lastPkg
                        lastPkg = pkg
                        val entity = ScreenRefreshApp.instance.db.whitelistDao().getByPackage(pkg)
                        if (entity != null) {
                            // Only start stepping if not already stepping up
                            if (!steppingActive || stepDir != 1) {
                                lastTargetHz = entity.targetRate
                                stepUp(entity.targetRate)
                            }
                        } else if (oldPkg.isNotEmpty() && lastTargetHz > 120) {
                            stepDown()
                            lastTargetHz = 120
                        }
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private suspend fun stepUp(targetHz: Int) {
        stepperJob?.cancel()
        steppingActive = true; stepDir = 1
        val chain = Stepper.getChain(targetHz)
        currentStepChain = chain
        stepperJob = scope.launch {
            try {
                for ((i, rate) in chain.withIndex()) {
                    RateController.setRate(rate)
                    currentStepIdx = i
                    updateOverlayRateRaw(RateController.getCurrentRate(this@AppDetectionService))
                    if (rate != chain.last()) delay(2000)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {}
            finally { steppingActive = false; stepDir = 0 }
        }
    }

    private suspend fun stepDown() {
        stepperJob?.cancel()
        steppingActive = true; stepDir = -1
        val chain = currentStepChain.toList()
        if (chain.size <= 1 || currentStepIdx <= 0) {
            RateController.resetTo120()
            updateOverlayRateRaw(RateController.getCurrentRate(this@AppDetectionService))
            steppingActive = false; stepDir = 0
            currentStepChain = emptyList()
            return
        }
        val downChain = chain.take(currentStepIdx + 1).reversed().drop(1)
        stepperJob = scope.launch {
            try {
                for (rate in downChain) {
                    RateController.setRate(rate)
                    updateOverlayRateRaw(RateController.getCurrentRate(this@AppDetectionService))
                    if (rate != downChain.last()) delay(1500)
                }
                RateController.resetTo120()
                updateOverlayRateRaw(RateController.getCurrentRate(this@AppDetectionService))
            } catch (_: kotlinx.coroutines.CancellationException) {}
            finally { steppingActive = false; stepDir = 0; currentStepChain = emptyList() }
        }
    }

    private suspend fun getForegroundPackage(): String? {
        val r = RateController.suExec("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'")
        for (pat in listOf(
            Regex("""u0\s+([\w.]+)/"""),
            Regex("""([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+)/"""),
        )) {
            val m = pat.find(r.output); if (m != null) return m.groupValues[1]
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        daemonJob?.cancel(); stepperJob?.cancel()
        hideOverlay()
        try { unregisterReceiver(toggleReceiver) } catch (_: Exception) {}
        scope.launch { RateController.resetTo120() }
        _isRunning.value = false
        super.onDestroy()
    }
}
