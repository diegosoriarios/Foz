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
        val iconPacks = mutableListOf<IconPackInfo>()
        val intent = Intent("org.adw.launcher.THEMES")
        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        
        for (ri in resolveInfos) {
            val packageName = ri.activityInfo.packageName
            val name = ri.loadLabel(packageManager).toString()
            val icon = ri.loadIcon(packageManager)
            iconPacks.add(IconPackInfo(packageName, name, icon))
        }
        iconPacks.sortBy { it.name }
        iconPacks
    }

    suspend fun loadIconPackMapping(iconPackPackageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val mapping = mutableMapOf<String, String>()
        try {
            val appRes = packageManager.getResourcesForApplication(iconPackPackageName)
            val resId = appRes.getIdentifier("appfilter", "xml", iconPackPackageName)
            if (resId != 0) {
                val xpp = appRes.getXml(resId)
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
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
