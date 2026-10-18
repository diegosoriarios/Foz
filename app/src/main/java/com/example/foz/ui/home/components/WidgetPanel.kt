package com.example.foz.ui.home.components

import android.appwidget.AppWidgetHostView
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WidgetPanel(
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    panelHeightDp: Int,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var actionWidgetId by remember { mutableStateOf<Int?>(null) }
    val pagerState = rememberPagerState(pageCount = { widgetViews.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeightDp.dp)
        ) { page ->
            val (widgetId, hostView) = widgetViews[page]
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        // Remove from previous parent if any
                        (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)

                        val wrapper = object : android.widget.FrameLayout(context) {
                            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                                // Let the widget handle its own touches
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return false
                            }
                        }

                        hostView.layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        wrapper.addView(hostView)

                        hostView.setOnLongClickListener {
                            actionWidgetId = widgetId
                            true
                        }
                        wrapper
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    onClick = { actionWidgetId = widgetId },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Widget Options",
                        modifier = Modifier.padding(4.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (widgetViews.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(widgetViews.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (selected) 0.8f else 0.3f
                                )
                            )
                    )
                }
            }
        }
    }

    actionWidgetId?.let { widgetId ->
        WidgetActionDialog(
            onRemove = {
                onRemoveWidget(widgetId)
                actionWidgetId = null
            },
            onMoveUp = {
                onMoveWidget(widgetId, -1)
                actionWidgetId = null
            },
            onMoveDown = {
                onMoveWidget(widgetId, 1)
                actionWidgetId = null
            },
            onConfigure = widgetViews.find { it.first == widgetId }?.second?.appWidgetInfo?.configure?.let {
                {
                    onConfigureWidget(widgetId)
                    actionWidgetId = null
                }
            },
            onDismiss = { actionWidgetId = null }
        )
    }
}
