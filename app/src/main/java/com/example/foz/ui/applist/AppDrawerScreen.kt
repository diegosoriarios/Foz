package com.example.foz.ui.applist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.example.foz.model.AppInfo
import kotlinx.coroutines.launch

@Composable
fun AppDrawerScreen(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
    sectionIndexes: Map<Char, Int>,
    requestedSectionLetter: Char?,
    onRequestedSectionConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    
    // Animation for background dimming
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isClosing) 0f else 0.3f,
        animationSpec = tween(durationMillis = 300),
        label = "backgroundAlpha"
    )
    
    // Animation for drawer content
    val contentAlpha by animateFloatAsState(
        targetValue = if (isClosing) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "contentAlpha"
    )
    
    val drawerCloseOnPullConnection = remember(onCloseDrawer, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                ) {
                    isClosing = true
                    // Delay closing to allow animation to play
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(200)
                        onCloseDrawer()
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(requestedSectionLetter, sectionIndexes) {
        val letter = requestedSectionLetter ?: return@LaunchedEffect
        sectionIndexes[letter]?.let { index ->
            listState.scrollToItem(index)
        }
        onRequestedSectionConsumed()
    }
    
    // Background dimming overlay
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(backgroundAlpha)
    ) {
        drawRect(color = Color.Black)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .nestedScroll(drawerCloseOnPullConnection)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
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
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
                    AppRow(
                        app = app,
                        onLaunch = { onLaunchApp(app) },
                        onLongPress = { onLongPressApp(app) }
                    )
                }
            }
        }
        AlphabetSidebar(
            onLetterSelected = { letter ->
                sectionIndexes[letter]?.let { index ->
                    coroutineScope.launch { listState.animateScrollToItem(index) }
                }
            },
            modifier = Modifier.padding(end = 4.dp, top = 12.dp, bottom = 12.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onLaunch,
                onLongClick = onLongPress
            )
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppIcon(
                drawable = app.icon,
                contentDescription = app.name,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}