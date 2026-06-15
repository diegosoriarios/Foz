package com.example.foz.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.foz.model.AppInfo
import com.example.foz.ui.applist.AppIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteAppItem(
    app: AppInfo,
    iconSize: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            AppIcon(
                drawable = app.icon,
                contentDescription = app.name,
                modifier = Modifier
                    .size(iconSize.dp)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    )
            )
            TextWithOutline(
                text = app.name,
                style = MaterialTheme.typography.labelMedium,
                mainColor = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
