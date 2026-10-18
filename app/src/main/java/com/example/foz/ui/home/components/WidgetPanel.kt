package com.example.foz.ui.home.components

import android.appwidget.AppWidgetHostView
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WidgetPanel(
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    widgetHeights: Map<Int, Int>,
    panelHeightDp: Int,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var actionWidgetId by remember { mutableIntStateOf(-1) }
    val pagerState = rememberPagerState(pageCount = { widgetViews.size })
    val scope = rememberCoroutineScope()

    // Height of the widget on the current page, falling back to the panel default
    val currentWidgetId = widgetViews.getOrNull(pagerState.currentPage)?.first
    val effectiveHeight = widgetHeights[currentWidgetId] ?: panelHeightDp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveHeight.dp)
        ) { page ->
            val (widgetId, hostView) = widgetViews[page]
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        // Remove from previous parent if any
                        (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)

                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        val wrapper = object : android.widget.FrameLayout(context) {
                            private var downX = 0f
                            private var downY = 0f
                            private var lastX = 0f
                            private var dragging = false

                            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        downX = ev.x
                                        downY = ev.y
                                        lastX = ev.x
                                        dragging = false
                                        // Let the widget try to handle its own gestures first
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        val dx = abs(ev.x - downX)
                                        val dy = abs(ev.y - downY)
                                        if (!dragging && dx > touchSlop && dx > dy) {
                                            // Horizontal drag: cancel the widget's gesture
                                            // and take over the stream to drive the pager
                                            dragging = true
                                            parent?.requestDisallowInterceptTouchEvent(false)
                                            return true
                                        }
                                    }
                                }
                                return false
                            }

                            override fun onTouchEvent(ev: MotionEvent): Boolean {
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_MOVE -> if (dragging) {
                                        val delta = lastX - ev.x
                                        lastX = ev.x
                                        // Feed the drag straight into the pager (native -> Compose bridge)
                                        scope.launch { pagerState.scrollBy(delta) }
                                        return true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                                        dragging = false
                                        // Continuous scroll position; rounding settles on the nearest page
                                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        val target = position.roundToInt()
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                target.coerceIn(0, pagerState.pageCount - 1)
                                            )
                                        }
                                        return true
                                    }
                                }
                                return dragging
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

    if (actionWidgetId != -1) {
        val widgetId = actionWidgetId
        WidgetActionDialog(
            onRemove = {
                onRemoveWidget(widgetId)
                actionWidgetId = -1
            },
            onMoveUp = {
                onMoveWidget(widgetId, -1)
                actionWidgetId = -1
            },
            onMoveDown = {
                onMoveWidget(widgetId, 1)
                actionWidgetId = -1
            },
            onResize = { newHeight ->
                onResizeWidget(widgetId, newHeight)
                actionWidgetId = -1
            },
            onConfigure = widgetViews.find { it.first == widgetId }?.second?.appWidgetInfo?.configure?.let {
                {
                    onConfigureWidget(widgetId)
                    actionWidgetId = -1
                }
            },
            onDismiss = { actionWidgetId = -1 }
        )
    }
}
