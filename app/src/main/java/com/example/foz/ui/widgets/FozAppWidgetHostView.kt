package com.example.foz.ui.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.RemoteViews
import com.example.foz.BuildConfig

class FozAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private val savedScrolls = mutableMapOf<Int, Pair<Int, Int>>()

    override fun updateAppWidget(views: RemoteViews?) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "FozWidgetDiag",
                "updateAppWidget[id=$appWidgetId view=${System.identityHashCode(this)}" +
                    " attached=$isAttachedToWindow shown=$isShown]" +
                    " views=${if (views != null) "present" else "null"}"
            )
        }
        if (views != null) {
            // Reset the internal view first so the next apply is a full inflate
            // instead of a reapply onto the existing tree. Without this, widgets
            // using setRemoteAdapter(RemoteCollectionItems) (Glance, Android 12L+)
            // never rebind their ListView because no
            // notifyAppWidgetViewDataChanged is ever delivered host-side. Both
            // calls run in the same main-thread message, so the transient
            // default view is never drawn.
            //
            // The full inflate rebuilds every ListView from scratch, dropping
            // the scroll position, so snapshot it before and restore it after.
            captureListScrolls()
            super.updateAppWidget(null)
        }
        super.updateAppWidget(views)
        if (views != null && savedScrolls.isNotEmpty()) {
            restoreListScrolls()
        }
    }

    private fun captureListScrolls() {
        savedScrolls.clear()
        forEachDescendant(this) { view ->
            val list = view as? AbsListView ?: return@forEachDescendant
            val position = list.firstVisiblePosition
            if (position > 0 && list.id != View.NO_ID) {
                val offset = list.getChildAt(0)?.top ?: 0
                savedScrolls[list.id] = position to offset
            }
        }
        if (BuildConfig.DEBUG && savedScrolls.isNotEmpty()) {
            Log.d("FozWidgetDiag", "scrollSaved[id=$appWidgetId] $savedScrolls")
        }
    }

    private fun restoreListScrolls() {
        forEachDescendant(this) { view ->
            val list = view as? AbsListView ?: return@forEachDescendant
            val scroll = savedScrolls.remove(list.id)
            if (BuildConfig.DEBUG) {
                Log.d(
                    "FozWidgetDiag",
                    "listApplied[id=$appWidgetId list=${System.identityHashCode(list)}]" +
                        " viewId=0x${Integer.toHexString(list.id)} count=${list.adapter?.count ?: 0}" +
                        " restore=${scroll?.first ?: -1}"
                )
            }
            if (scroll != null) {
                val (position, offset) = scroll
                if ((list.adapter?.count ?: 0) > position) {
                    // Restore synchronously, before the first layout/draw of the
                    // rebuilt list; ListView honors this pre-layout by scrolling
                    // during its first layout pass, so no top frame is visible.
                    list.setSelectionFromTop(position, offset)
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "FozWidgetDiag",
                            "scrollRestored[id=$appWidgetId] position=$position offset=$offset" +
                                " stuck=${list.firstVisiblePosition == position}"
                        )
                    }
                } else {
                    scheduleScrollRestore(list, scroll, MAX_SCROLL_RESTORE_RETRIES)
                }
            }
        }
        savedScrolls.clear()
    }

    private fun scheduleScrollRestore(list: AbsListView, scroll: Pair<Int, Int>, retries: Int) {
        list.post {
            val count = list.adapter?.count ?: 0
            val (position, offset) = scroll
            if (position < count) {
                list.setSelectionFromTop(position, offset)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "FozWidgetDiag",
                        "scrollRestored[id=$appWidgetId] position=$position offset=$offset path=posted"
                    )
                }
            } else if (retries > 0) {
                scheduleScrollRestore(list, scroll, retries - 1)
            } else if (BuildConfig.DEBUG) {
                Log.d("FozWidgetDiag", "scrollRestoreGaveUp[id=$appWidgetId] position=$position count=$count")
            }
        }
    }

    private fun forEachDescendant(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                action(child)
                forEachDescendant(child, action)
            }
        }
    }

    private companion object {
        const val MAX_SCROLL_RESTORE_RETRIES = 5
    }
}
