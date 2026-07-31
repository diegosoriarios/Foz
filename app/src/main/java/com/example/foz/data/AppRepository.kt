package com.example.foz.data

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

class AppRepository(
    private val packageManager: PackageManager,
    private val launcherApps: LauncherApps?
) {
    suspend fun getLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val collator = Collator.getInstance()
        
        if (launcherApps != null) {
            val user = Process.myUserHandle()
            val activities = launcherApps.getActivityList(null, user)
            activities.map { info ->
                AppInfo(
                    name = info.label?.toString() ?: info.applicationInfo.packageName,
                    packageName = info.applicationInfo.packageName,
                    className = info.componentName.className,
                    icon = try { info.loadIcon(0) } catch (e: Exception) { null }
                )
            }
            .distinctBy { it.packageName }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            resolveInfos
                .map { info ->
                    AppInfo(
                        name = info.loadLabel(packageManager)?.toString() ?: info.activityInfo.packageName,
                        packageName = info.activityInfo.packageName,
                        className = info.activityInfo.name,
                        icon = try { info.loadIcon(packageManager) } catch (e: Exception) { null }
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
    }

    suspend fun getShortcuts(packageName: String): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || launcherApps == null) {
            return@withContext emptyList()
        }
        try {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            launcherApps.getShortcuts(query, Process.myUserHandle())
                ?.mapNotNull { shortcut ->
                    val label = shortcut.shortLabel?.toString()
                        ?: shortcut.longLabel?.toString()
                        ?: return@mapNotNull null
                    AppShortcut(
                        id = shortcut.id,
                        label = label,
                        packageName = packageName
                    )
                }
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
