package com.example.foz.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foz.model.WidgetInfo
import com.example.foz.ui.components.FozBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerDialog(
    widgets: List<WidgetInfo>,
    onWidgetSelected: (WidgetInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val groupedWidgets = remember(widgets) {
        widgets.groupBy { it.packageName }
    }

    FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Text(
                text = "Select Widget",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                groupedWidgets.forEach { (packageName, appWidgets) ->
                    val appName = appWidgets.firstOrNull()?.appName ?: "Unknown App"
                    val appIcon = appWidgets.firstOrNull()?.appIcon

                    item(key = packageName) {
                        AppWidgetGroup(
                            appName = appName,
                            appIcon = appIcon,
                            widgets = appWidgets,
                            onWidgetSelected = onWidgetSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppWidgetGroup(
    appName: String,
    appIcon: android.graphics.drawable.Drawable?,
    widgets: List<WidgetInfo>,
    onWidgetSelected: (WidgetInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appIcon?.let {
                val bitmap = remember(it) { try { it.toInternalBitmap() } catch (e: Exception) { null } }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(widgets) { widget ->
                WidgetItem(
                    widget = widget,
                    onClick = { onWidgetSelected(widget) }
                )
            }
        }
    }
}

@Composable
fun WidgetItem(
    widget: WidgetInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .padding(4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val preview = widget.preview ?: widget.icon
                preview?.let {
                    val bitmap = remember(it) { try { it.toInternalBitmap() } catch (e: Exception) { null } }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                
                if (widget.preview == null) {
                    // Overlay icon if it's just the app icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = widget.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp
            )
        }
    }
}

private fun android.graphics.drawable.Drawable.toInternalBitmap(): android.graphics.Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable && bitmap != null) {
        return bitmap
    }
    val width = if (intrinsicWidth > 0) intrinsicWidth else 1
    val height = if (intrinsicHeight > 0) intrinsicHeight else 1
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
