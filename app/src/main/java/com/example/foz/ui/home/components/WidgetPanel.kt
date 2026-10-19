package com.example.foz.ui.home.components

import android.appwidget.AppWidgetHostView
import android.view.MotionEvent
import android.view.VelocityTracker
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.foz.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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

    // Live height while the user drags the resize handle, null when idle
    var dragWidgetId by remember { mutableIntStateOf(-1) }
    var dragHeightDp by remember { mutableStateOf<Float?>(null) }
    var showHeightChip by remember { mutableStateOf(false) }

    // Height of the widget on the current page, falling back to the panel default
    val currentWidgetId = widgetViews.getOrNull(pagerState.currentPage)?.first
    val effectiveHeight = widgetHeights[currentWidgetId] ?: panelHeightDp
    val displayHeight = dragHeightDp?.roundToInt() ?: effectiveHeight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(displayHeight.dp)
        ) { page ->
            val (widgetId, hostView) = widgetViews[page]
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        // Remove from previous parent if any
                        (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)

                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        val flingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 5f
                        val wrapper = object : android.widget.FrameLayout(context) {
                            private var downX = 0f
                            private var downY = 0f
                            private var lastX = 0f
                            private var dragging = false
                            private var pendingScroll = 0f
                            private var pagerJob: Job? = null
                            private var velocityTracker: VelocityTracker? = null

                            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        downX = ev.x
                                        downY = ev.y
                                        lastX = ev.x
                                        dragging = false
                                        pendingScroll = 0f
                                        velocityTracker?.recycle()
                                        velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                                        if (hasScrollableChild(hostView)) {
                                            parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        if (!dragging) {
                                            val dx = abs(ev.x - downX)
                                            val dy = abs(ev.y - downY)
                                            if (dx > touchSlop && dx > dy) {
                                                // Horizontal drag: cancel the widget's gesture
                                                // and take over the stream to drive the pager.
                                                // dispatchTouchEvent still fires even when the
                                                // widget requests disallow-intercept-touch.
                                                dragging = true
                                                val cancel = MotionEvent.obtain(ev)
                                                cancel.action = MotionEvent.ACTION_CANCEL
                                                super.dispatchTouchEvent(cancel)
                                                cancel.recycle()
                                            }
                                        }
                                        if (dragging) {
                                            velocityTracker?.addMovement(ev)
                                            pendingScroll += lastX - ev.x
                                            lastX = ev.x
                                            if (pagerJob?.isActive != true) {
                                                pagerJob = scope.launch {
                                                    while (abs(pendingScroll) > 0.01f) {
                                                        val delta = pendingScroll
                                                        pendingScroll = 0f
                                                        pagerState.scrollBy(delta)
                                                    }
                                                }
                                            }
                                            return true
                                        }
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                                        dragging = false
                                        val tracker = velocityTracker
                                        velocityTracker = null
                                        val vx = if (ev.actionMasked == MotionEvent.ACTION_UP && tracker != null) {
                                            tracker.addMovement(ev)
                                            tracker.computeCurrentVelocity(1000)
                                            tracker.xVelocity
                                        } else {
                                            0f
                                        }
                                        tracker?.recycle()
                                        // Continuous scroll position; rounding settles on the
                                        // nearest page unless a fling advances it
                                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        val target = when {
                                            vx < -flingVelocity -> pagerState.currentPage + 1
                                            vx > flingVelocity -> pagerState.currentPage - 1
                                            else -> position.roundToInt()
                                        }.coerceIn(0, pagerState.pageCount - 1)
                                        scope.launch {
                                            pagerJob?.join()
                                            pagerState.animateScrollToPage(target)
                                        }
                                        return true
                                    }
                                    else -> if (dragging) return true
                                }
                                return super.dispatchTouchEvent(ev)
                            }

                            private fun hasScrollableChild(view: android.view.View): Boolean {
                                if (view.canScrollVertically(-1) || view.canScrollVertically(1)) return true
                                if (view is android.view.ViewGroup) {
                                    for (i in 0 until view.childCount) {
                                        if (hasScrollableChild(view.getChildAt(i))) return true
                                    }
                                }
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

            val resizeDesc = stringResource(R.string.widget_resize_desc)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            ) {
                AnimatedVisibility(
                    visible = showHeightChip,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${dragHeightDp?.roundToInt() ?: displayHeight} dp",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(width = 44.dp, height = 24.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { _ ->
                                    dragWidgetId = widgetViews.getOrNull(pagerState.currentPage)?.first ?: -1
                                    dragHeightDp = (widgetHeights[dragWidgetId] ?: panelHeightDp).toFloat()
                                    showHeightChip = true
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragHeightDp?.let { cur ->
                                        dragHeightDp = (cur + dragAmount.toDp().value).coerceIn(100f, 300f)
                                    }
                                },
                                onDragEnd = {
                                    val wid = dragWidgetId
                                    val h = dragHeightDp
                                    if (wid != -1 && h != null) {
                                        onResizeWidget(wid, h.roundToInt())
                                        scope.launch {
                                            delay(900)
                                            showHeightChip = false
                                        }
                                    }
                                    dragHeightDp = null
                                    dragWidgetId = -1
                                },
                                onDragCancel = {
                                    dragHeightDp = null
                                    dragWidgetId = -1
                                    showHeightChip = false
                                }
                            )
                        }
                        .semantics { contentDescription = resizeDesc },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.3f
                                )
                            )
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
