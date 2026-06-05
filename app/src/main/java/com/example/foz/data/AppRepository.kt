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
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val collator = Collator.getInstance()
        resolveInfos
            .map {
                AppInfo(
                    name = it.loadLabel(packageManager)?.toString() ?: it.activityInfo.packageName,
                    packageName = it.activityInfo.packageName,
                    className = it.activityInfo.name,
                    icon = it.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    suspend fun getShortcuts(packageName: String): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || launcherApps == null) {
            return@withContext emptyList()
        }
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
    }
}
