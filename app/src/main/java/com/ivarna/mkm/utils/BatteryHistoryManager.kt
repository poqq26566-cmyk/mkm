package com.ivarna.mkm.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ivarna.mkm.MainActivity
import com.ivarna.mkm.R
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.service.BatteryMonitorService

/**
 * Manages a persistent notification card showing battery statistics.
 *
 * Decoupled from UI and tracker — only knows how to build & post notifications.
 */
class BatteryNotificationManager(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = appContext.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val CHANNEL_ID = "mkm_battery_channel"
        const val NOTIFICATION_ID = 2001
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "电池监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在会话进行中显示实时电池统计数据"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds a [Notification] for the given stats without posting it.
     * Useful for [android.app.Service.startForeground].
     */
    fun buildNotification(stats: BatteryStats): Notification {
        val style = NotificationCompat.BigTextStyle()
            .bigText(buildContentText(stats))
            .setSummaryText(buildSubText(stats))

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(buildTitle(stats))
            .setContentText(buildOneLine(stats))
            .setSubText(buildSubText(stats))
            .setNumber(stats.percent)
            .setContentIntent(openBatteryPendingIntent())
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Builds a placeholder notification for use before the first stats are ready.
     */
    fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("电池监控")
            .setContentText("正在初始化…")
            .setContentIntent(openBatteryPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Posts or updates the battery notification.
     */
    fun show(stats: BatteryStats) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(stats))
    }

    /**
     * Cancels the battery notification.
     */
    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildTitle(stats: BatteryStats): String {
        val parts = mutableListOf("${stats.percent}%")

        if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_SHOW_WATTAGE, true) && stats.calibratedWattageW != 0f) {
            parts.add(String.format("%+.2fW", stats.calibratedWattageW))
        }
        if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_SHOW_TEMPERATURE, false)) {
            parts.add(String.format("%.1f°C", stats.temperatureC))
        }
        if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_SHOW_DRAIN, false)) {
            if (stats.isCharging) {
                // Show charge gain rate (%/hr) instead of drain rate during charging
                val sessionHours = stats.totalSessionTimeMs / 3_600_000f
                val gainRatePerHr = if (sessionHours > 0.001f && stats.chargingGainedPercent > 0)
                    stats.chargingGainedPercent / sessionHours else 0f
                if (gainRatePerHr > 0f) parts.add(String.format("+%.1f%%/hr", gainRatePerHr))
            } else if (stats.isSessionActive) {
                parts.add(String.format("%.2f%%/hr", stats.activeDrainPerHr))
            }
        }
        if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_SHOW_TIME_LEFT, false) && stats.estimatedTimeRemainingMin > 0) {
            val h = stats.estimatedTimeRemainingMin / 60
            val m = stats.estimatedTimeRemainingMin % 60
            parts.add(if (h > 0) "${h}h${m}m" else "${m}m")
        }
        if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_SHOW_CURRENT, false)) {
            var currentMa = kotlin.math.abs(stats.currentMa)
            // Fallback: if sensor reports 0 but wattage+voltage are known, derive current
            if (currentMa == 0 && stats.voltageMv > 0 && stats.calibratedWattageW != 0f) {
                val rawW = kotlin.math.abs(stats.wattageW)
                currentMa = ((rawW * 1_000f) / stats.voltageMv).toInt()
            }
            parts.add("${currentMa}mA")
        }
        if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_SHOW_VOLTAGE, false)) {
            parts.add("${stats.voltageMv}mV")
        }

        return parts.joinToString(" · ")
    }

    private fun buildOneLine(stats: BatteryStats): String {
        return when {
            stats.isCharging -> {
                val powerW = if (stats.calibratedWattageW != 0f) stats.calibratedWattageW
                             else if (stats.wattageW != 0f) stats.wattageW else 0f
                val parts = mutableListOf<String>()
                parts.add("充电中 · ${String.format("%.1f", stats.temperatureC)}°C")
                if (powerW > 0f) parts.add("${String.format("+%.1f", powerW)}W")
                val currentMa = kotlin.math.abs(stats.currentMa)
                if (currentMa > 50) parts.add("${currentMa}mA")
                if (stats.chargingGainedPercent > 0) parts.add("+${stats.chargingGainedPercent}%")
                parts.joinToString(" · ")
            }
            stats.isSessionActive -> "放电中 · ${String.format("%.1f", stats.temperatureC)}°C"
            else -> "已接通电源 · ${String.format("%.1f", stats.temperatureC)}°C"
        }
    }

    private fun buildSubText(stats: BatteryStats): String {
        return when {
            stats.isCharging -> {
                val gained = if (stats.chargingGainedPercent > 0) "已增加 +${stats.chargingGainedPercent}%" else "充电中"
                val timeLeft = if (stats.estimatedTimeRemainingMin > 0) {
                    val h = stats.estimatedTimeRemainingMin / 60
                    val m = stats.estimatedTimeRemainingMin % 60
                    " · 还需 ${if (h > 0) "${h}h ${m}m" else "${m}m"} 充满"
                } else ""
                "$gained$timeLeft"
            }
            stats.isSessionActive -> {
                val drain = String.format("%.2f", stats.activeDrainPerHr)
                val time = if (stats.estimatedTimeRemainingMin > 0) {
                    val h = stats.estimatedTimeRemainingMin / 60
                    val m = stats.estimatedTimeRemainingMin % 60
                    " · 剩余 ${h}h ${m}m"
                } else ""
                "耗电 ${drain}%/小时$time"
            }
            else -> "已接通电源"
        }
    }

    private fun buildContentText(stats: BatteryStats): String {
        return buildString {
            if (stats.isCharging) {
                // Rich charging session content — each line gated by its own pref
                val powerW = if (stats.calibratedWattageW != 0f) stats.calibratedWattageW
                             else if (stats.wattageW != 0f) stats.wattageW else 0f
                var currentMa = kotlin.math.abs(stats.currentMa)
                // Fallback: derive from wattage + voltage if sensor returns 0
                if (currentMa == 0 && stats.voltageMv > 0 && powerW != 0f) {
                    val rawW = kotlin.math.abs(stats.wattageW)
                    currentMa = ((rawW * 1_000f) / stats.voltageMv).toInt()
                }

                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_CHG_SHOW_TEMP, true)) {
                    appendLine("温度：${String.format("%.1f", stats.temperatureC)}°C · ${stats.voltageMv} mV")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_CHG_SHOW_POWER, true) && powerW > 0f) {
                    appendLine("充电功率：${String.format("+%.2f", powerW)} W")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_CHG_SHOW_CURRENT, true)) {
                    if (currentMa > 0) appendLine("充电电流：$currentMa mA")
                    if (stats.chargingAvgCurrentMa > 0) appendLine("平均电流：${stats.chargingAvgCurrentMa} mA")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_CHG_SHOW_GAINED, true) && stats.chargingGainedPercent > 0) {
                    appendLine("已增加：+${stats.chargingGainedPercent}% (${stats.chargingSessionStartPercent}% → ${stats.percent}%)")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_CHG_SHOW_DURATION, true) && stats.totalSessionTimeMs > 0) {
                    appendLine("已充电：${formatDuration(stats.totalSessionTimeMs)}")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_CHG_SHOW_ETA, true) && stats.estimatedTimeRemainingMin > 0) {
                    val h = stats.estimatedTimeRemainingMin / 60
                    val m = stats.estimatedTimeRemainingMin % 60
                    appendLine("预计充满：${if (h > 0) "${h}h ${m}m" else "${m}m"}")
                }
                // Remove trailing newline
                if (length > 0 && last() == '\n') deleteCharAt(length - 1)
            } else if (stats.isSessionActive) {
                // Discharging session content
                val showMah = prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_SHOW_MAH, false)
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_TEMP_VOLTAGE, true)) {
                    appendLine("温度：${String.format("%.1f", stats.temperatureC)}°C · ${stats.voltageMv} mV")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_POWER, true)) {
                    appendLine("功率：${String.format("%+.2f", stats.calibratedWattageW)} W")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_DRAIN, true)) {
                    appendLine("使用中耗电：${String.format("%.2f", stats.activeDrainPerHr)}%/小时 · 待机耗电：${String.format("%.2f", stats.idleDrainPerHr)}%/小时")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_TIME_LEFT, true) && stats.estimatedTimeRemainingMin > 0) {
                    val h = stats.estimatedTimeRemainingMin / 60
                    val m = stats.estimatedTimeRemainingMin % 60
                    appendLine("预计剩余时间：${h}h ${m}m")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_SCREEN_ON, true)) {
                    val mah = mahStr(stats.screenOnDrainPercent, stats, showMah)
                    appendLine("亮屏：${formatDuration(stats.screenOnTimeMs)} (耗电 ${String.format("%.1f", stats.screenOnDrainPercent)}%$mah)")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_SCREEN_OFF, true)) {
                    val mah = mahStr(stats.screenOffDrainPercent, stats, showMah)
                    appendLine("熄屏：${formatDuration(stats.screenOffTimeMs)} (耗电 ${String.format("%.1f", stats.screenOffDrainPercent)}%$mah)")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_DEEP_SLEEP, true)) {
                    val mah = mahStr(stats.deepSleepDrainPercent, stats, showMah)
                    appendLine("深度睡眠：${formatDuration(stats.deepSleepTimeMs)} (耗电 ${String.format("%.2f", stats.deepSleepDrainPercent)}%$mah)")
                }
                if (prefs.getBoolean(BatteryMonitorService.PREF_NOTIF_EXP_AWAKE, true)) {
                    val mah = mahStr(stats.awakeDrainPercent, stats, showMah)
                    append("唤醒（未睡眠）：${formatDuration(stats.awakeTimeMs)} (耗电 ${String.format("%.2f", stats.awakeDrainPercent)}%$mah)")
                }
                // Remove trailing newline
                if (length > 0 && last() == '\n') deleteCharAt(length - 1)
            }
        }
    }


    /**
     * Returns a formatted " · ~X mAh" suffix if showMah is true and capacity is known.
     */
    private fun mahStr(drainPercent: Float, stats: BatteryStats, showMah: Boolean): String {
        if (!showMah || drainPercent <= 0f) return ""
        val capacity = if (stats.estimatedCapacityMah > 0) stats.estimatedCapacityMah
                       else if (stats.ratedCapacityMah > 0) stats.ratedCapacityMah
                       else return ""
        val mah = (drainPercent / 100f * capacity).toInt()
        return " · ~${mah} mAh"
    }

    private fun openBatteryPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = BatteryMonitorService.ACTION_OPEN_BATTERY
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(appContext, 0, intent, flags)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m ${seconds}s"
        } else {
            "${minutes}m ${seconds}s"
        }
    }
}
