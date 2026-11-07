package com.sortsnap.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sortsnap.R
import com.sortsnap.classifier.Category
import com.sortsnap.data.scan.ScanStatus
import com.sortsnap.data.scan.ScanWorker

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).testTag("empty_state"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.error_loading),
        body = message,
        modifier = modifier,
        action = { Button(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
    )
}

/** Shimmering placeholder grid shown while the first page loads. */
@Composable
fun GridSkeleton(columns: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize().testTag("grid_skeleton"),
        userScrollEnabled = false,
    ) {
        items(columns * 8) {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(1.dp).aspectRatio(1f).alpha(alpha).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Slim progress bar for the gallery top bar while the scanner runs. */
@Composable
fun ScanProgressBar(status: ScanStatus, modifier: Modifier = Modifier) {
    if (!status.running && !status.deferred) return
    Column(modifier.fillMaxWidth().testTag("scan_progress")) {
        val label = when {
            status.deferred -> stringResource(R.string.scan_waiting_charger)
            status.phase == ScanWorker.PHASE_SYNC || status.total == 0L -> stringResource(R.string.scanning_sync)
            else -> stringResource(R.string.scanning_progress, status.done.toInt(), status.total.toInt())
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
        val fraction = status.fraction
        if (fraction != null && !status.deferred) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(2.dp))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@Composable
fun CategoryPickerDialog(
    current: Category?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_category_title)) },
        text = {
            Column(Modifier.testTag("category_picker")) {
                Category.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = c == current, onClick = { onPick(c) }, role = Role.RadioButton)
                            .padding(vertical = 10.dp)
                            .testTag("pick_${c.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c == current, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(c.pluralLabel()))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
