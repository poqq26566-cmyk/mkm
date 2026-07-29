package com.ivarna.mkm.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.data.provider.ThermalStatus
import com.ivarna.mkm.data.provider.ThermalZone

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ivarna.mkm.utils.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalCard(
    status: ThermalStatus,
    isLoading: Boolean = false,
    onSetLimit: (Int) -> Unit,
    onDisableThrottling: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "温控状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLoading) {
                            Text(
                                "正在检测传感器…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "峰值：${status.maxTemp}°C",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (status.maxTemp > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (status.currentLimit > 0) {
                                    Text(
                                        text = " • 限制：${status.currentLimit}°C",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "展开"
                        )
                    }
                }
            }


            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                    
                    status.zones.take(10).forEach { zone -> // Limit to top 10 hottest
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = zone.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${zone.temp}°C",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (zone.temp > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface 
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showLimitDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("设置限制")
                        }

                        Button(
                            onClick = { showWarningDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("禁用")
                        }
                    }
                    
                    TextButton(
                        onClick = { showLogDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("查看调试日志")
                    }
                }
            }
        }
    }

    if (showLimitDialog) {
        var sliderValue by remember { mutableFloatStateOf(status.currentLimit.toFloat().coerceIn(45f, 95f)) }
        if (sliderValue == 0f) sliderValue = 85f // Default if reading failed

        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            icon = { Icon(Icons.Default.Thermostat, contentDescription = null) },
            title = { Text("设置温控限制") },
            text = {
                Column {
                    Text("调整开始降频的温度。数值越高性能越好，但发热也越高。")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${sliderValue.toInt()}°C",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 45f..95f,
                        steps = 9 // 5 degree increments: 45, 50, ..., 95
                    )
                    if (sliderValue > 85) {
                        Text(
                            "警告：高温可能会降低电池寿命。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetLimit(sliderValue.toInt())
                        showLimitDialog = false
                    }
                ) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showLogDialog) {
        val logs by AppLogger.logs.collectAsState()
        
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("调试日志") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    if (logs.isEmpty()) {
                        item { Text("暂无日志。") }
                    } else {
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("极度危险") },
            text = { 
                Text("禁用温控降频可能会永久损坏你的设备和电池，甚至造成人身伤害。强烈不建议这样做。\n\n你确定要继续吗？") 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        onDisableThrottling()
                        showWarningDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("是的，我了解风险")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
