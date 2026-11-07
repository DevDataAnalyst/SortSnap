package com.sortsnap.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sortsnap.R

/**
 * Explains why photo access is needed and that nothing leaves the device. Stateless so it can be
 * UI-tested without a real permission dialog.
 */
@Composable
fun OnboardingScreen(
    deniedPermanently: Boolean,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp).testTag("onboarding"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge)
            Point(Icons.Filled.Lock, stringResource(R.string.onboarding_privacy_title), stringResource(R.string.onboarding_privacy_body))
            Point(Icons.Filled.Info, stringResource(R.string.onboarding_why_title), stringResource(R.string.onboarding_why_body))
            Point(Icons.Filled.Star, stringResource(R.string.filter_photos_only), stringResource(R.string.settings_hide_clutter_sub))
            Spacer(Modifier.height(8.dp))
            if (deniedPermanently) {
                Text(stringResource(R.string.onboarding_denied), color = MaterialTheme.colorScheme.error)
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().testTag("open_settings")) {
                    Text(stringResource(R.string.onboarding_open_settings))
                }
                OutlinedButton(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_allow))
                }
            } else {
                Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth().testTag("allow_access")) {
                    Text(stringResource(R.string.onboarding_allow))
                }
            }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Android 14+ partial access: offer to grant the whole library. */
@Composable
fun PartialAccessBanner(onAllowAll: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("partial_banner")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.partial_access_banner), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = onAllowAll) { Text(stringResource(R.string.partial_access_action)) }
        }
    }
}
