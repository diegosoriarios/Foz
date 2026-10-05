package com.example.foz.ui.diagnostics

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.data.PrefsManager
import com.example.foz.manager.CrashGuard
import com.example.foz.ui.common.ErrorHub
import com.example.foz.ui.common.FozErrorBanner
import com.example.foz.ui.theme.FozTheme
import java.text.DateFormat
import java.util.Date

/**
 * On-device error visibility: last recorded crash (CrashGuard), safe-mode
 * status and the persistent runtime-error history (ErrorHub), with a
 * share/export action. Local only — nothing leaves the device unless the
 * user shares the report.
 */
class DiagnosticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        setContent {
            val prefs = remember { PrefsManager(applicationContext) }
            val monochrome by prefs.monochromeMode.collectAsState(initial = false)
            val dynamicColor by prefs.useDynamicColor.collectAsState(initial = true)
            val errorLogJson by prefs.assistantErrorLog.collectAsState(initial = "")

            FozTheme(
                darkTheme = monochrome || androidx.compose.foundation.isSystemInDarkTheme(),
                dynamicColor = dynamicColor && !monochrome,
                monochromeMode = monochrome
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DiagnosticsScreen(
                        errorLogJson = errorLogJson,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DiagnosticsScreen(
    errorLogJson: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries = remember(errorLogJson) { ErrorHub.fromJson(errorLogJson) }
    val lastCrash = remember { CrashGuard.readLastCrash(context) }
    val safeMode = remember {
        CrashGuard.consumeStartupState(context) == CrashGuard.Recovery.SAFE_MODE
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.settings_diagnostics_title),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            IconButton(onClick = {
                shareReport(context, lastCrash, safeMode, entries, dateFormat)
            }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.diagnostics_share),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        val density = androidx.compose.ui.platform.LocalDensity.current
        val listBottomPadding = 28.dp +
            with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (safeMode) {
                item {
                    DiagnosticsCard(
                        title = stringResource(R.string.diagnostics_safe_mode),
                        body = stringResource(R.string.diagnostics_safe_mode_detail)
                    )
                }
            }
            item {
                DiagnosticsCard(
                    title = stringResource(R.string.diagnostics_last_crash),
                    body = lastCrash?.let { crash ->
                        buildString {
                            append(dateFormat.format(Date(crash.timestampMs)))
                            append(" · v")
                            append(crash.versionCode)
                            append('\n')
                            if (crash.exception.isNotBlank()) {
                                append(crash.exception)
                                append('\n')
                            }
                            if (crash.thread.isNotBlank()) {
                                append(stringResource(R.string.diagnostics_thread))
                                append(": ")
                                append(crash.thread)
                                append('\n')
                            }
                            if (crash.stack.isNotBlank()) {
                                append(crash.stack)
                            }
                        }.trim()
                    } ?: stringResource(R.string.diagnostics_no_crash)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.diagnostics_recent_errors),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diagnostics_no_errors),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(entries.asReversed()) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = dateFormat.format(Date(entry.timestampMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        }

        FozErrorBanner(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@androidx.compose.runtime.Composable
private fun DiagnosticsCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shareReport(
    context: android.content.Context,
    lastCrash: CrashGuard.LastCrash?,
    safeMode: Boolean,
    entries: List<ErrorHub.Entry>,
    dateFormat: DateFormat
) {
    val report = buildString {
        appendLine("Foz diagnostics report")
        appendLine("======================")
        if (safeMode) appendLine("Safe mode: ACTIVE")
        appendLine()
        appendLine("-- Last crash --")
        if (lastCrash != null) {
            appendLine("Time: " + dateFormat.format(Date(lastCrash.timestampMs)))
            appendLine("Version code: " + lastCrash.versionCode)
            appendLine("Thread: " + lastCrash.thread)
            appendLine("Exception: " + lastCrash.exception)
            appendLine("Stack:")
            appendLine(lastCrash.stack)
        } else {
            appendLine("none")
        }
        appendLine()
        appendLine("-- Recent errors (newest last) --")
        if (entries.isEmpty()) {
            appendLine("none")
        } else {
            entries.forEach { entry ->
                appendLine(dateFormat.format(Date(entry.timestampMs)) + ": " + entry.message)
            }
        }
    }
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                },
                null
            )
        )
    } catch (_: Throwable) {
    }
}
