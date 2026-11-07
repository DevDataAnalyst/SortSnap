package com.sortsnap.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortsnap.BuildConfig
import com.sortsnap.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Debug-only scan statistics. Not reachable in release builds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val d by viewModel.debug.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val stats = d ?: return@Scaffold
        Column(Modifier.padding(padding)) {
            Row(stringResource(R.string.debug_images_per_sec), String.format(Locale.US, "%.1f", stats.stats.imagesPerSecond))
            Row(stringResource(R.string.debug_avg_ml), String.format(Locale.US, "%.0f ms", stats.stats.averageMlMillis))
            Row(stringResource(R.string.debug_queue), stats.queue.toString())
            Row(stringResource(R.string.debug_indexed), stats.indexed.toString())
            Row(stringResource(R.string.debug_processed), stats.stats.processed.toString())
            Row(stringResource(R.string.debug_version), stats.version.toString())
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    ListItem(headlineContent = { Text(label) }, trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium) })
}
