package com.screenrefresh.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrefresh.controller.root.DisplayMode
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    var modes by remember { mutableStateOf<List<DisplayMode>>(emptyList()) }
    var currentRate by remember { mutableIntStateOf(60) }
    var debug by remember { mutableStateOf<List<RootExecutor.DebugEntry>>(emptyList()) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var shizukuOk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootOk = RootExecutor.isRootAvailable()
        shizukuOk = RootExecutor.isShizukuAvailable()
        RateController.refreshModes()
        modes = RateController.getModeInfo()
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // === Status ===
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("当前刷新率: $currentRate Hz", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Root: ${when(rootOk) { true -> "✅"; false -> "❌"; else -> "检测中..." }}")
                    Text("Shizuku: ${if (shizukuOk) "✅" else "❌"}")
                }
                Text("已检测模式: ${modes.size} 个", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (modes.isNotEmpty()) {
                    Text(modes.map { "${it.fps}Hz(m${it.id})" }.joinToString(" "),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === Modes from dumpsys ===
        if (modes.isNotEmpty()) {
            Text("DisplayModeRecord 模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.distinctBy { it.fps }.sortedBy { it.fps }.forEach { m ->
                    FilledTonalButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                RateController.setRate(m.fps)
                                debug = RateController.lastDebugEntries
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("${m.fps}Hz\nm${m.id}", fontSize = 10.sp, maxLines = 2) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // === Manual fallback buttons ===
        Text("强制设置（不依赖模式扫描）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(60, 90, 120, 144, 165).forEach { rate ->
                FilledTonalButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            RateController.setRate(rate)
                            debug = RateController.lastDebugEntries
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("${rate}Hz", fontSize = 11.sp) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // === Diagnostic ===
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        RateController.runDiagnostic()
                        debug = RateController.lastDebugEntries
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("诊断系统", fontSize = 12.sp) }

            FilledTonalButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        RateController.refreshModes()
                        modes = RateController.getModeInfo()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("重新扫描", fontSize = 12.sp) }

            FilledTonalButton(
                onClick = {
                    RateController.clearDebug()
                    debug = emptyList()
                },
                modifier = Modifier.weight(0.6f)
            ) { Text("清空", fontSize = 12.sp) }
        }

        Spacer(Modifier.height(12.dp))

        // === Debug log ===
        if (debug.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("调试日志", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    debug.forEach { entry ->
                        val icon = if (entry.success) "✅" else "❌"
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("$icon ${entry.method} (${entry.elapsedMs}ms)",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("  ${entry.command.take(80)}",
                                fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.output.isNotEmpty() && entry.output != "(empty)") {
                                Text("  → ${entry.output.take(200)}",
                                    fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
