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
            "com.teslacoilsw.launcher.THEME",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.slimlauncher.THEME",
            "com.dlto.atom.launcher.THEME"
        )
        
        for (action in actions) {
            val intent = Intent(action)
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (ri in resolveInfos) {
                val packageName = ri.activityInfo.packageName
                if (!iconPacks.containsKey(packageName)) {
                    val name = ri.loadLabel(packageManager).toString()
                    val icon = ri.loadIcon(packageManager)
                    iconPacks[packageName] = IconPackInfo(packageName, name, icon)
                }
            }
        }

        // Also check by category
        val categories = listOf(
            "com.fede.launcher.ICON_PACK",
            "com.anddoes.launcher.ICON_PACK",
            "com.teslacoilsw.launcher.ICON_PACK"
        )
        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (ri in resolveInfos) {
                val packageName = ri.activityInfo.packageName
                if (!iconPacks.containsKey(packageName)) {
                    val name = ri.loadLabel(packageManager).toString()
                    val icon = ri.loadIcon(packageManager)
                    iconPacks[packageName] = IconPackInfo(packageName, name, icon)
                }
            }
        }

        // Special check for Icon Pack Studio exported packs
        val ipsExported = "ginlemon.iconpackstudio.exported"
        try {
            val ai = packageManager.getApplicationInfo(ipsExported, 0)
            if (!iconPacks.containsKey(ipsExported)) {
                val name = packageManager.getApplicationLabel(ai).toString()
                val icon = packageManager.getApplicationIcon(ai)
                iconPacks[ipsExported] = IconPackInfo(ipsExported, name, icon)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Not installed
        }

        iconPacks.values.sortedBy { it.name }
    }

    suspend fun loadIconPackMapping(iconPackPackageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val mapping = mutableMapOf<String, String>()
        try {
            val appRes = packageManager.getResourcesForApplication(iconPackPackageName)
            val possibleNames = listOf("appfilter", "appfilter_v2", "icon_pack")
            
            var xpp: XmlPullParser? = null
            
            // First try res/xml
            for (name in possibleNames) {
                val resId = appRes.getIdentifier(name, "xml", iconPackPackageName)
                if (resId != 0) {
                    xpp = appRes.getXml(resId)
                    break
                }
            }
            
            // Then try res/raw (common for Icon Pack Studio)
            if (xpp == null) {
                for (name in possibleNames) {
                    val resId = appRes.getIdentifier(name, "raw", iconPackPackageName)
                    if (resId != 0) {
                        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                        xpp = factory.newPullParser()
                        xpp.setInput(appRes.openRawResource(resId), "UTF-8")
                        break
                    }
                }
            }

            if (xpp != null) {
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && (xpp.name == "item" || xpp.name == "Item")) {
                        val componentName = xpp.getAttributeValue(null, "component")
                        val drawableName = xpp.getAttributeValue(null, "drawable")
                        if (componentName != null && drawableName != null) {
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
