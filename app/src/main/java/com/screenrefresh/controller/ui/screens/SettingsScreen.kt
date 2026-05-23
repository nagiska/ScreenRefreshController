package com.screenrefresh.controller.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrefresh.controller.root.StepProfiles
import com.screenrefresh.controller.service.SettingsManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settingsManager: SettingsManager) {
    var stepInterval by remember { mutableFloatStateOf(settingsManager.stepIntervalMs / 1000f) }
    var resetOnExit by remember { mutableStateOf(settingsManager.resetOnExit) }
    var selectedProfileId by remember { mutableStateOf(settingsManager.selectedProfileId) }

    var customRates by remember { mutableStateOf(settingsManager.customRates) }
    var newRateText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Step interval
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("步进间隔", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("每级刷新率之间的等待时间", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("${stepInterval.toInt()} 秒", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Slider(value = stepInterval, onValueChange = { stepInterval = it },
                    onValueChangeFinished = { settingsManager.stepIntervalMs = (stepInterval * 1000).toLong() },
                    valueRange = 1f..10f, steps = 8)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1s", style = MaterialTheme.typography.bodySmall)
                    Text("10s", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Reset on exit
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("退出时恢复默认", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("服务停止时恢复默认刷新率",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = resetOnExit, onCheckedChange = {
                    resetOnExit = it; settingsManager.resetOnExit = it
                })
            }
        }

        Spacer(Modifier.height(12.dp))

        // Step profile
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("步进方案", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("选择进入白名单应用后使用的提频方案",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                StepProfiles.profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selectedProfileId = profile.id
                        settingsManager.selectedProfileId = profile.id
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedProfileId == profile.id,
                            onClick = {
                                selectedProfileId = profile.id
                                settingsManager.selectedProfileId = profile.id
                            })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            Text(
                                if (profile.id == "custom") "自定义: ${customRates.joinToString("→")}Hz"
                                else profile.rates.joinToString(" → ") { "${it}Hz" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Custom rate editor (only visible when "自定义" is selected)
        if (selectedProfileId == "custom") {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("自定义刷新率", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    if (customRates.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            customRates.sorted().forEach { rate ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text("${rate}Hz", fontSize = 13.sp) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                customRates = customRates - rate
                                                settingsManager.customRates = customRates
                                            },
                                            modifier = Modifier.width(16.dp).height(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "删除",
                                                modifier = Modifier.width(14.dp).height(14.dp))
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newRateText,
                            onValueChange = { newRateText = it.filter { c -> c.isDigit() } },
                            label = { Text("输入刷新率 (Hz)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val rate = newRateText.toIntOrNull()
                                if (rate != null && rate in 30..300 && rate !in customRates) {
                                    customRates = (customRates + rate).sorted()
                                    settingsManager.customRates = customRates
                                    newRateText = ""
                                }
                            },
                            enabled = newRateText.toIntOrNull() != null
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("范围 30-300 Hz，添加你超频后支持的刷新率",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
