package com.example.foz.model

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable

data class WidgetInfo(
    val label: String,
    val providerInfo: AppWidgetProviderInfo,
    val icon: Drawable?
)
