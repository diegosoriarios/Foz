package com.example.foz.ui.home.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.model.AppInfo
import com.example.foz.model.ContactInfo
import com.example.foz.model.InstantAnswer
import com.example.foz.ui.home.components.AppListItem
import com.example.foz.ui.home.components.ContactItem
import com.example.foz.ui.home.components.InstantAnswerCard
import com.example.foz.ui.home.components.InstantAnswerLoadingCard
import com.example.foz.ui.home.components.WebSearchRow

@Composable
fun SwipeUpPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    filteredApps: List<AppInfo>,
    searchContacts: List<ContactInfo>,
    instantAnswer: InstantAnswer?,
    instantAnswerLoading: Boolean,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onOpenContact: (ContactInfo) -> Unit,
    onSearchWeb: (String) -> Unit,
    onOpenAnswerUrl: (String) -> Unit,
    appIconSize: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchListState = rememberLazyListState()

    val nestedScrollConnection = remember(query, onClose) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isAtTop = if (query.isNotEmpty()) {
                    searchListState.firstVisibleItemIndex == 0 && searchListState.firstVisibleItemScrollOffset == 0
                } else {
                    true
                }

                if (source == NestedScrollSource.UserInput && available.y > 15f && isAtTop) {
                    onClose()
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_panel_title),
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
            Surface(
                onClick = onClose,
                shape = MaterialTheme.shapes.medium,
                color = LocalContentColor.current.copy(alpha = 0.15f)
            ) {
                Text(
                    text = stringResource(R.string.settings_close),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
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
            placeholder = { Text(stringResource(R.string.search_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LocalContentColor.current,
                unfocusedTextColor = LocalContentColor.current,
                focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                focusedBorderColor = Color.White.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                cursorColor = Color.White,
                focusedLeadingIconColor = LocalContentColor.current,
                unfocusedLeadingIconColor = LocalContentColor.current.copy(alpha = 0.7f),
                focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (query.isNotEmpty()) {
            LazyColumn(
                state = searchListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (instantAnswer != null) {
                    item(key = "answer") {
                        InstantAnswerCard(
                            answer = instantAnswer,
                            onClick = { instantAnswer.sourceUrl?.let(onOpenAnswerUrl) }
                        )
                    }
                } else if (instantAnswerLoading) {
                    item(key = "answer_loading") {
                        InstantAnswerLoadingCard()
                    }
                }

                if (searchContacts.isNotEmpty()) {
                    item(key = "contacts_header") {
                        SearchSectionLabel(text = stringResource(R.string.search_section_contacts))
                    }
                    items(searchContacts, key = { "contact_${it.phoneNumber}_${it.name}" }) { contact ->
                        ContactItem(
                            contact = contact,
                            onClick = { onOpenContact(contact) }
                        )
                    }
                }

                if (filteredApps.isNotEmpty()) {
                    item(key = "apps_header") {
                        SearchSectionLabel(text = stringResource(R.string.search_section_apps))
                    }
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            iconSize = appIconSize,
                            onClick = { onLaunchApp(app) },
                            onLongClick = { onLongPressApp(app) }
                        )
                    }
                }

                item(key = "web_row") {
                    Spacer(modifier = Modifier.height(2.dp))
                    WebSearchRow(
                        query = query,
                        onClick = { onSearchWeb(query) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}
