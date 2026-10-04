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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Collator

class AppRepository(
    private val packageManager: PackageManager,
    private val launcherApps: LauncherApps?,
    /** When set, enables the cold-start app-list disk cache. */
    private val filesDir: File? = null
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
                    icon = try { info.getIcon(0) } catch (e: Exception) { null }
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

    // ---------- Cold-start disk cache ----------
    // Persists names/identifiers only (never icons) so favorites and the
    // alphabet index render instantly after a crash/cold start; icons are
    // re-decoded by the next refresh.

    suspend fun cacheApps(apps: List<AppInfo>) {
        val dir = filesDir ?: return
        withContext(Dispatchers.IO) {
            try {
                val array = JSONArray()
                apps.forEach { app ->
                    array.put(
                        JSONObject()
                            .put("name", app.name)
                            .put("packageName", app.packageName)
                            .put("className", app.className)
                    )
                }
                val file = cacheFile(dir)
                val temp = File(dir, "app_list_cache.tmp")
                temp.writeText(array.toString())
                if (!temp.renameTo(file)) {
                    temp.delete()
                }
            } catch (_: Throwable) {
            }
        }
    }

    /** Cached list with null icons, or null if missing/corrupt/empty. */
    suspend fun getCachedApps(): List<AppInfo>? {
        val dir = filesDir ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val file = cacheFile(dir)
                if (!file.exists()) return@withContext null
                val array = JSONArray(file.readText())
                val apps = (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    AppInfo(
                        name = obj.optString("name"),
                        packageName = obj.optString("packageName"),
                        className = obj.optString("className"),
                        icon = null
                    )
                }
                apps.ifEmpty { null }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun cacheFile(dir: File) = File(dir, "app_list_cache.json")
}
