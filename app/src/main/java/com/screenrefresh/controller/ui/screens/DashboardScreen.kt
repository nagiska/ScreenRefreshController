package com.screenrefresh.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val MiuiBlue = Color(0xFF1677FF)
val MiuiGreen = Color(0xFF34C759)
val MiuiBg = Color(0xFFF2F3F5)
val MiuiCardBg = Color.White
val MiuiText = Color(0xFF1A1A1A)
val MiuiGray = Color(0xFF8E8E93)

val MiuiLightScheme = lightColorScheme(
    primary = MiuiBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = Color(0xFF0958D9),
    surface = MiuiCardBg,
    onSurface = MiuiText,
    onSurfaceVariant = MiuiGray,
    surfaceVariant = Color(0xFFEEEEEF),
    background = MiuiBg,
    onBackground = MiuiText,
    secondary = Color(0xFF787880),
    tertiary = MiuiGreen,
    error = Color(0xFFFF4D4F)
)

@Composable
fun DashboardScreen(
    currentRate: Int,
    kernelVersion: String,
    availableRates: List<Int> = emptyList(),
    isServiceRunning: Boolean = false,
    onOpenAccessibility: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var curRate by remember { mutableIntStateOf(currentRate) }
    var kern by remember { mutableStateOf(kernelVersion) }
    var showDebug by remember { mutableStateOf(false) }
    var debug by remember { mutableStateOf<List<RootExecutor.DebugEntry>>(emptyList()) }
    val rates = if (availableRates.isNotEmpty()) availableRates.sorted() else listOf(120, 132, 144, 156, 165)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        .padding(top = 20.dp, bottom = 40.dp)) {

        Spacer(Modifier.height(8.dp))

        // ── Top row: Current rate box + Step indicators ──
        Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
            // Left: current rate card (bigger square)
            Card(
                modifier = Modifier.width(130.dp).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MiuiCardBg),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(MiuiGreen),
                            contentAlignment = Alignment.Center
                        ) { Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(6.dp))
                        Text("${curRate} Hz", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MiuiText)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("当前刷新率", fontSize = 9.sp, color = MiuiGray)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right: 5 bars stacked vertically
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                rates.forEach { rate ->
                    val active = rate == curRate
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) MiuiBlue else Color(0xFFEEEEEF))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    RateController.setRate(rate)
                                    delay(500)
                                    curRate = RateController.getCurrentRate(ctx)
                                    debug = RateController.lastDebugEntries
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${rate}Hz", fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color.White else MiuiGray
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Kernel version ──
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Kernel", fontSize = 11.sp, color = MiuiGray, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(kern, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MiuiText)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Manual buttons ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val half = (rates.size + 1) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rates.take(half).forEach { rate ->
                    RateButton(rate, curRate, scope, ctx) { curRate = it; debug = RateController.lastDebugEntries }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rates.drop(half).forEach { rate ->
                    RateButton(rate, curRate, scope, ctx) { curRate = it; debug = RateController.lastDebugEntries }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Tools ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    RateController.runDiagnostic()
                    debug = RateController.lastDebugEntries
                    showDebug = true
                }
            }, Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)
            ) { Text("诊断", fontSize = 11.sp, color = MiuiText) }

            FilledTonalButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    curRate = RateController.getCurrentRate(ctx)
                }
            }, Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)
            ) { Text("刷新", fontSize = 11.sp, color = MiuiText) }

            FilledTonalButton(onClick = {
                showDebug = !showDebug
                if (!showDebug) RateController.clearDebug()
            }, Modifier.weight(0.6f).height(40.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)
            ) { Text(if (showDebug) "收起" else "日志", fontSize = 11.sp, color = MiuiText) }
        }

        Spacer(Modifier.height(12.dp))

        // ── Service status (always visible) ──
        Card(
            Modifier.fillMaxWidth().clickable { if (!isServiceRunning) onOpenAccessibility() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isServiceRunning) "●" else "○", fontSize = 10.sp,
                    color = if (isServiceRunning) MiuiGreen else Color(0xFFFF4D4F))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("无障碍服务", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828))
                    Text(if (isServiceRunning) "监控中" else "点击开启",
                        fontSize = 10.sp, color = MiuiGray)
                }
            }
        }

        // ── Debug log (conditional) ──
        if (showDebug && debug.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    debug.forEach { e ->
                        val icon = if (e.success) "✅" else "❌"
                        Text("$icon [${e.via}] ${e.method} ${e.elapsedMs}ms",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MiuiText)
                        if (e.output != "(empty)" && e.output.isNotBlank())
                            Text("  ${e.output.take(200)}", fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace, color = MiuiGray)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RowScope.RateButton(
    rate: Int,
    curRate: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    ctx: android.content.Context,
    onDone: (Int) -> Unit
) {
    val active = rate == curRate
    Button(
        onClick = {
            scope.launch(Dispatchers.IO) {
                RateController.setRate(rate)
                delay(500) // wait for display to switch
                val fresh = RateController.getCurrentRate(ctx)
                onDone(fresh)
            }
        },
        modifier = Modifier.weight(1f).height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = if (active) ButtonDefaults.buttonColors(containerColor = MiuiBlue)
        else ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEF), contentColor = MiuiText),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) { Text("${rate}Hz", fontSize = if (rate >= 165) 12.sp else 14.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
}
