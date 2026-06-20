package com.example.foz.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
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
                contentDescription = null, // Redundant with text
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)

            )
            TextWithOutline(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                mainColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            if (notificationCount > 0) {
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Show Notifications",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
