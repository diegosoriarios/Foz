package com.example.foz.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.example.foz.model.IconPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class IconPackManager(private val context: Context) {
    private val packageManager = context.packageManager

    suspend fun getInstalledIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val iconPacks = mutableMapOf<String, IconPackInfo>()
        val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
        )
        
        for (action in actions) {
            val intent = Intent(action)
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (ri in resolveInfos) {
                val packageName = ri.activityInfo.packageName
                if (!iconPacks.containsKey(packageName)) {
                    val name = ri.loadLabel(packageManager).toString()
                    val icon = ri.loadIcon(packageManager)
                    iconPacks[packageName] = IconPackInfo(packageName, name, icon)
                }
            }
        }
        iconPacks.values.sortedBy { it.name }
    }

    suspend fun loadIconPackMapping(iconPackPackageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val mapping = mutableMapOf<String, String>()
        try {
            val appRes = packageManager.getResourcesForApplication(iconPackPackageName)
            // Try different possible names for the mapping file
            val possibleNames = listOf("appfilter", "appfilter_v2", "icon_pack")
            var resId = 0
            for (name in possibleNames) {
                resId = appRes.getIdentifier(name, "xml", iconPackPackageName)
                if (resId != 0) break
            }

            if (resId != 0) {
                val xpp = appRes.getXml(resId)
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && (xpp.name == "item" || xpp.name == "Item")) {
                        val componentName = xpp.getAttributeValue(null, "component")
                        val drawableName = xpp.getAttributeValue(null, "drawable")
                        if (componentName != null && drawableName != null) {
                            // Extract just the component info part if it's in full format
                            // e.g. ComponentInfo{com.example/com.example.Main}
                            mapping[componentName] = drawableName
                        }
                    }
                    eventType = xpp.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mapping
    }

    fun loadIcon(iconPackPackageName: String, drawableName: String): Drawable? {
        return try {
            val appRes = packageManager.getResourcesForApplication(iconPackPackageName)
            val resId = appRes.getIdentifier(drawableName, "drawable", iconPackPackageName)
            if (resId != 0) {
                appRes.getDrawable(resId, null)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
