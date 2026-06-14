package com.example.foz.ui.home.panels

import android.appwidget.AppWidgetHostView
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.foz.ui.home.components.WidgetActionDialog
import com.example.foz.ui.theme.FozTheme

@Composable
fun SwipeUpPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    widgetHeights: Map<Int, Int>,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedWidgetIdForAction by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Apps & Widgets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Surface(onClick = onClose, shape = MaterialTheme.shapes.medium) {
                Text(text = "Close", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true,
            placeholder = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(onClick = onAddWidget, shape = MaterialTheme.shapes.medium) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(text = "Add widget")
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (widgetViews.isNotEmpty()) {
            Text(
                text = "Widgets (Hold to edit)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(widgetViews, key = { it.first }) { (widgetId, hostView) ->
                    val heightDp = widgetHeights[widgetId] ?: 180
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp.dp)
                    ) {
                        AndroidView(
                            factory = { context ->
                                // Remove from previous parent if any
                                (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)
                                
                                val wrapper = object : android.widget.FrameLayout(context) {
                                    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                                        // Very important: Disallow parent (LazyColumn) from intercepting as soon as a touch is detected
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                        return false // Don't consume, let children (the widget) handle it
                                    }
                                }
                                
                                // Ensure hostView fills wrapper
                                hostView.layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                wrapper.addView(hostView)

                                hostView.apply {
                                    setOnLongClickListener {
                                        selectedWidgetIdForAction = widgetId
                                        true
                                    }
                                }
                                wrapper
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { _ ->
                                // Optional: Keep any state synced
                            }
                        )

                        // Options button for fallback if long press is difficult
                        Surface(
                            onClick = { selectedWidgetIdForAction = widgetId },
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape,
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
            }
        } else {
            Text(
                text = "No widgets added",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    selectedWidgetIdForAction?.let { widgetId ->
        WidgetActionDialog(
            onRemove = {
                onRemoveWidget(widgetId)
                selectedWidgetIdForAction = null
            },
            onMoveUp = {
                onMoveWidget(widgetId, -1)
                selectedWidgetIdForAction = null
            },
            onMoveDown = {
                onMoveWidget(widgetId, 1)
                selectedWidgetIdForAction = null
            },
            onResize = { newHeight ->
                onResizeWidget(widgetId, newHeight)
                selectedWidgetIdForAction = null
            },
            onConfigure = widgetViews.find { it.first == widgetId }?.second?.appWidgetInfo?.configure?.let {
                {
                    onConfigureWidget(widgetId)
                    selectedWidgetIdForAction = null
                }
            },
            onDismiss = { selectedWidgetIdForAction = null }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SwipeUpPanelPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mockWidgetViews = remember {
        listOf(100 to AppWidgetHostView(context))
    }

    FozTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SwipeUpPanel(
                query = "",
                onQueryChange = {},
                widgetViews = mockWidgetViews,
                widgetHeights = mapOf(100 to 150),
                onAddWidget = {},
                onRemoveWidget = {},
                onMoveWidget = { _, _ -> },
                onResizeWidget = { _, _ -> },
                onConfigureWidget = {},
                onClose = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SwipeUpPanelSearchPreview() {
    FozTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SwipeUpPanel(
                query = "Maps",
                onQueryChange = {},
                widgetViews = emptyList(),
                widgetHeights = emptyMap(),
                onAddWidget = {},
                onRemoveWidget = {},
                onMoveWidget = { _, _ -> },
                onResizeWidget = { _, _ -> },
                onConfigureWidget = {},
                onClose = {}
            )
        }
    }
}

