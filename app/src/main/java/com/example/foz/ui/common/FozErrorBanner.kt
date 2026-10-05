package com.example.foz.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom-anchored snackbar host fed by [ErrorHub]. Drop into any screen's
 * root Box to surface runtime errors (model load, downloads, voice,
 * recovery notices) consistently.
 */
@Composable
fun FozErrorBanner(
    modifier: Modifier = Modifier,
    duration: SnackbarDuration = SnackbarDuration.Short
) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        ErrorHub.events.collect { message ->
            hostState.showSnackbar(message, duration = duration)
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = 16.dp)
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
