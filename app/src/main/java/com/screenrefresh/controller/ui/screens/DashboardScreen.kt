package com.screenrefresh.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrefresh.controller.root.RootShell

@Composable
fun DashboardScreen(
    isServiceRunning: Boolean,
    isRootAvailable: Boolean?,
    currentRate: Int,
    isStepping: Boolean,
    supportedRates: List<Int>,
    profileName: String,
    debugEntries: List<RootShell.ShellDebugEntry>,
    onToggleService: () -> Unit,
    onManualSetRate: (Int) -> Unit,
    onClearDebug: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
    }
}
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Status
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                StatusRow("无障碍服务", if (isServiceRunning) "运行中" else "已停止", isServiceRunning)
                Spacer(Modifier.height(8.dp))
                StatusRow("Root 权限", when (isRootAvailable) { true -> "已获取"; false -> "未获取"; null -> "检测中..." }, isRootAvailable == true)
                Spacer(Modifier.height(8.dp))
                StatusRow("步进方案", profileName, true)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Manual test buttons
        if (supportedRates.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("手动测试", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("点击按钮尝试切换到对应刷新率（会尝试所有已知方法）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        supportedRates.forEach { rate ->
                            FilledTonalButton(
                                onClick = { onManualSetRate(rate) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${rate}Hz", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Debug log
        if (debugEntries.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("调试日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FilledTonalButton(onClick = onClearDebug, modifier = Modifier.height(28.dp)) {
                            Text("清空", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    debugEntries.forEach { entry ->
                        val icon = if (entry.success) "✅" else "❌"
                        val status = if (entry.success) "OK" else "FAIL"
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("$icon [$status] ${entry.method}",
                                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium)
                            Text(" > ${entry.command}", fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.output.isNotEmpty() && entry.output != "(empty)") {
                                Text(" < ${entry.output.take(150)}",
                                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onToggleService,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)
        ) {
            Icon(imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isServiceRunning) "停止服务" else "启动服务")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String, isActive: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
