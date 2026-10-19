package com.example.foz.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.model.CalendarEventInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val eventTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val eventDayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun Long.toLocalDateTime(): java.time.LocalDateTime {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

@Composable
fun CalendarPreviewCard(
    events: List<CalendarEventInfo>,
    onOpenAgenda: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenAgenda() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            events.take(3).forEachIndexed { index, event ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.begin.toLocalDateTime().format(eventTimeFormatter),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun AgendaContent(
    events: List<CalendarEventInfo>,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.agenda_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (!isPermissionGranted) {
            Text(
                text = stringResource(R.string.agenda_needs_access),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRequestPermission) {
                Text(text = stringResource(R.string.agenda_grant_access))
            }
        } else if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.agenda_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        } else {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val grouped = events.groupBy { it.begin.toLocalDateTime().toLocalDate() }

            grouped.forEach { (day, dayEvents) ->
                val dayLabel = when (day) {
                    today -> stringResource(R.string.agenda_today)
                    tomorrow -> stringResource(R.string.agenda_tomorrow)
                    else -> day.format(eventDayFormatter)
                }
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                dayEvents.forEach { event ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = if (event.allDay) {
                                stringResource(R.string.agenda_all_day)
                            } else {
                                val begin = event.begin.toLocalDateTime().format(eventTimeFormatter)
                                val end = event.end.toLocalDateTime().format(eventTimeFormatter)
                                "$begin – $end"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.width(110.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            event.location?.let { location ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
