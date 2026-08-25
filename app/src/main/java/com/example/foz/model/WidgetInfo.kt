package com.example.foz.model

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable

data class WidgetInfo(
    val label: String,
    val providerInfo: AppWidgetProviderInfo,
    val icon: Drawable?,
    val preview: Drawable? = null,
    val appName: String = "",
    val packageName: String = "",
    val appIcon: Drawable? = null
)
