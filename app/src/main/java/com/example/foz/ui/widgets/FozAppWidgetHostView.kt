package com.example.foz.ui.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.example.foz.BuildConfig

class FozAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    override fun updateAppWidget(views: RemoteViews?) {
        if (BuildConfig.DEBUG) {
            Log.d("FozWidgetDiag", "updateAppWidget views=${if (views != null) "present" else "null"}")
        }
        if (views != null) {
            // Reset the internal view first so the next apply is a full inflate
            // instead of a reapply onto the existing tree. Without this, widgets
            // using setRemoteAdapter(RemoteCollectionItems) (Glance, Android 12L+)
            // never rebind their ListView because no
            // notifyAppWidgetViewDataChanged is ever delivered host-side. Both
            // calls run in the same main-thread message, so the transient
            // default view is never drawn.
            super.updateAppWidget(null)
        }
        super.updateAppWidget(views)
    }
}
