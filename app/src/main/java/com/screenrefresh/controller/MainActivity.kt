package com.screenrefresh.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// MIUI-like color scheme
private val MiuiBlue = Color(0xFF1677FF)
private val MiuiBlueDark = Color(0xFF0958D9)
private val MiuiBg = Color(0xFFF2F3F5)
private val MiuiCardBg = Color(0xFFFFFFFF)
private val MiuiDanger = Color(0xFFFF4D4F)

private val MiuiLightScheme = lightColorScheme(
    primary = MiuiBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = MiuiBlueDark,
    surface = MiuiCardBg,
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceVariant = Color(0xFFEEEEEF),
    background = MiuiBg,
    onBackground = Color(0xFF1A1A1A),
    secondary = Color(0xFF787880),
    tertiary = Color(0xFF34C759),
    error = MiuiDanger
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MiuiLightScheme) {
                Surface(Modifier.fillMaxSize(), color = MiuiBg) { MainScreen() }
            }
        }
    }
}

private val PROFILE_RATES = listOf(120, 132, 144, 156, 165)

@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    var currentRate by remember { mutableIntStateOf(120) }
    var debug by remember { mutableStateOf<List<RootExecutor.DebugEntry>>(emptyList()) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var shizukuOk by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var setting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootOk = RootExecutor.isRootAvailable()
        shizukuOk = RootExecutor.isShizukuAvailable()
        currentRate = RateController.getCurrentRate()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Current rate display ──
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("当前刷新率", fontSize = 14.sp, color = Color(0xFF8E8E93))
                Spacer(Modifier.height(8.dp))
                Text("${currentRate} Hz", fontSize = 56.sp,
                    fontWeight = FontWeight.Bold, color = MiuiBlue)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusChip("Root", rootOk)
                    StatusChip("Shizuku", shizukuOk)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Rate buttons ──
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("切换刷新率", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILE_RATES.forEach { rate ->
                        val isActive = rate == currentRate
                        Button(
                            onClick = {
                                if (setting) return@Button
                                setting = true
                                scope.launch(Dispatchers.IO) {
                                    RateController.setRate(rate)
                                    currentRate = RateController.getCurrentRate()
                                    debug = RateController.lastDebugEntries
                                    setting = false
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = if (isActive)
                                ButtonDefaults.buttonColors(containerColor = MiuiBlue)
                            else
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF2F3F5),
                                    contentColor = Color(0xFF1A1A1A)
                                ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${rate}Hz", fontSize = 14.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium)
                                if (isActive) Text("●", fontSize = 6.sp,
                                    color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Tool buttons ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        RateController.runDiagnostic()
                        debug = RateController.lastDebugEntries
                        showDebug = true
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)
            ) { Text("诊断", fontSize = 13.sp, color = Color(0xFF1A1A1A)) }

            FilledTonalButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        currentRate = RateController.getCurrentRate()
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)
            ) { Text("刷新状态", fontSize = 13.sp, color = Color(0xFF1A1A1A)) }

            FilledTonalButton(
                onClick = {
                    showDebug = !showDebug
                    if (!showDebug) RateController.clearDebug()
                },
                modifier = Modifier.weight(0.7f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White)
            ) { Text(if (showDebug) "收起" else "日志", fontSize = 13.sp,
                color = Color(0xFF1A1A1A))
            }
        }

        // ── Debug log ──
        if (showDebug && debug.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("调试日志", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93))
                    Spacer(Modifier.height(8.dp))
                    debug.forEach { e ->
                        val icon = if (e.success) "✅" else "❌"
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("$icon [${e.via}] ${e.method}  ${e.elapsedMs}ms",
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = Color(0xFF3A3A3C))
                            if (e.output != "(empty)" && e.output.isNotBlank())
                                Text("  ${e.output.take(250)}", fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF8E8E93))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun StatusChip(label: String, ok: Boolean?) {
    val color = when (ok) {
        true -> Color(0xFF34C759)
        false -> Color(0xFFFF4D4F)
        null -> Color(0xFF8E8E93)
    }
    val text = when (ok) {
        true -> "✅"
        false -> "❌"
        null -> "…"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F3F5))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("$label $text", fontSize = 12.sp, color = Color(0xFF8E8E93))
    }
}
