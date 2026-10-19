package com.example.foz.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

class UsageStatsRepository(private val context: Context) {

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getLaunchCounts(days: Int = 7, excludePackages: Set<String> = emptySet()): Map<String, Int> {
        if (!hasPermission()) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val since = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        val counts = mutableMapOf<String, Int>()
        try {
            val events = usm.queryEvents(since, System.currentTimeMillis())
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.packageName !in excludePackages
                ) {
                    counts[event.packageName] = (counts[event.packageName] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
        }
        return counts
    }

    fun getScreenTimeSince(startOfDayMillis: Long): Long {
        if (!hasPermission()) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0L
        return try {
            val now = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayMillis, now)
                ?.sumOf { it.totalTimeInForeground } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun getScreenTimeToday(): Long {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return getScreenTimeSince(startOfDay)
    }
}
