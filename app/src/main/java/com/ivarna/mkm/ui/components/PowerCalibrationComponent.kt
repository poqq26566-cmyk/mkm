
package com.ivarna.mkm.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.mkm.data.model.PowerStatus

@Composable
fun PowerCalibrationComponent(
    status: PowerStatus,
    savedMultiplier: Float,
    onSaveMultiplier: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Initialise from the persisted savedMultiplier, not status.multiplier from the
    // polling loop. LaunchedEffect re-syncs only when the *saved* value changes
    // (e.g. user saved from Overlay Settings), not on every poll emission.
    var multiplierText by remember { mutableStateOf(savedMultiplier.toString()) }
    LaunchedEffect(savedMultiplier) {
        multiplierText = savedMultiplier.toString()
    }
    val currentMultiplier = multiplierText.toFloatOrNull() ?: savedMultiplier
    val calibratedPower = status.powerW * currentMultiplier
    val polaritySign = if (status.isCharging) "+" else "-"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "功率校准",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { multiplierText = "1.0" }) {
                    Icon(
                        Icons.Default.SettingsBackupRestore,
                        contentDescription = "重置",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Data Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CalibrationDataItem("原始电流", "${polaritySign}${status.currentUa / 1000} mA")
                CalibrationDataItem("原始电压", "%.2f V".format(status.voltageUv / 1_000_000f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CalibrationDataItem("原始功率", "${polaritySign}%.3f W".format(status.powerW))
                CalibrationDataItem(
                    "校准后", 
                    "${polaritySign}%.3f W".format(calibratedPower),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "校准系数",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            var saveError by remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = multiplierText,
                    onValueChange = {
                        multiplierText = it
                        saveError = false
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("系数（例如 1.1）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = saveError,
                    supportingText = if (saveError) {
                        { Text("数字无效", color = MaterialTheme.colorScheme.error) }
                    } else null
                )
                
                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        // Normalise decimal separator for locales that use comma
                        val normalised = multiplierText.trim().replace(',', '.')
                        val v = normalised.toFloatOrNull()
                        if (v != null && v > 0f) {
                            multiplierText = v.toString()
                            saveError = false
                            onSaveMultiplier(v)
                        } else {
                            saveError = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "调整系数以匹配外部功率计的读数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CalibrationDataItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
