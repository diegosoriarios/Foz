package com.example.foz.ui.home.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LauncherOnboardingCard(
    dismissed: Boolean,
    onRequestLauncherRole: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = if (dismissed) "Foz is not your default launcher" else "Set Foz as your default launcher",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (dismissed) {
                    "Tap below when you want to switch to Foz."
                } else {
                    "You need to grant launcher role to use Foz when pressing Home."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onRequestLauncherRole, shape = MaterialTheme.shapes.medium) {
                    Text(
                        text = if (dismissed) "Set as launcher" else "Continue",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Surface(onClick = onOpenLauncherSettings, shape = MaterialTheme.shapes.medium) {
                    Text(
                        text = "Open settings",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                if (!dismissed) {
                    Surface(onClick = onDismiss, shape = MaterialTheme.shapes.medium) {
                        Text(
                            text = "Maybe later",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
