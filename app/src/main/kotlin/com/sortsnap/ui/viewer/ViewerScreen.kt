package com.sortsnap.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.sortsnap.R
import com.sortsnap.domain.Formatting
import com.sortsnap.domain.GalleryItem
import com.sortsnap.ui.components.CategoryPickerDialog
import com.sortsnap.ui.components.DeleteFlowHost
import com.sortsnap.ui.components.deleteResultMessage
import com.sortsnap.ui.components.pluralLabel
import com.sortsnap.ui.components.rememberDeleteFlowState
import com.sortsnap.ui.components.singleLabel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(onBack: () -> Unit, viewModel: ViewerViewModel = hiltViewModel()) {
    val items = viewModel.pages.collectAsLazyPagingItems()
    val pager = rememberPagerState(initialPage = viewModel.initialIndex) { items.itemCount }
    val details by viewModel.details.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deleteFlow = rememberDeleteFlowState(viewModel.deleter)

    // Jump to the tapped image once placeholders have been counted.
    LaunchedEffect(items.itemCount) {
        if (items.itemCount > viewModel.initialIndex && pager.currentPage == 0 && viewModel.initialIndex > 0) {
            pager.scrollToPage(viewModel.initialIndex)
        }
    }
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.currentPage to items.itemCount }.collect { (page, count) ->
            if (page < count) items.peek(page)?.let { viewModel.onPageShown(it.id) }
        }
    }
    val current: GalleryItem? = if (pager.currentPage < items.itemCount) items.peek(pager.currentPage) else null

    DeleteFlowHost(deleteFlow) { step ->
        items.refresh()
        scope.launch { snackbar.showSnackbar(deleteResultMessage(context, step)) }
    }
    if (picking && current != null) {
        CategoryPickerDialog(
            current = details?.category,
            onPick = { picking = false; viewModel.move(current.id, it) },
            onDismiss = { picking = false },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("viewer")) {
        HorizontalPager(
            state = pager,
            key = items.itemKey { it.id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                ZoomableImage(
                    uri = item.uri,
                    contentDescription = item.displayName,
                    onTap = { chromeVisible = !chromeVisible },
                )
            }
        }
        if (chromeVisible) {
            ViewerTopBar(
                enabled = current != null,
                onBack = onBack,
                onInfo = { showDetails = true },
                onMove = { picking = true },
                onTrash = { current?.let { deleteFlow.start(listOf(it.toDeleteTarget())) } },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showDetails && current != null) {
        ModalBottomSheet(onDismissRequest = { showDetails = false }) {
            DetailsSheet(item = current, details = details, onMove = { showDetails = false; picking = true })
        }
    }
}

/** Translucent bar over the photo: back, details ("Why?"), move to category, trash. */
@Composable
fun ViewerTopBar(enabled: Boolean, onBack: () -> Unit, onInfo: () -> Unit, onMove: () -> Unit, onTrash: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).statusBarsPadding().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onInfo, enabled = enabled, modifier = Modifier.testTag("viewer_info")) {
            Icon(Icons.Filled.Info, stringResource(R.string.action_info), tint = Color.White)
        }
        IconButton(onClick = onMove, enabled = enabled) {
            Icon(Icons.Filled.Edit, stringResource(R.string.action_move_to_category), tint = Color.White)
        }
        IconButton(onClick = onTrash, enabled = enabled) {
            Icon(Icons.Filled.Delete, stringResource(R.string.action_trash), tint = Color.White)
        }
    }
}

@Composable
fun DetailsSheet(item: GalleryItem, details: ImageDetails?, onMove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).testTag("details_sheet")) {
        Text(stringResource(R.string.details_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        DetailRow(stringResource(R.string.details_name), item.displayName)
        DetailRow(stringResource(R.string.details_date), DateFormat.getDateTimeInstance().format(Date(item.dateTaken)))
        DetailRow(stringResource(R.string.details_size), Formatting.bytes(item.sizeBytes))
        DetailRow(stringResource(R.string.details_resolution), "${item.width} × ${item.height}")
        DetailRow(stringResource(R.string.details_folder), item.folder)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        val category = details?.category
        DetailRow(
            stringResource(R.string.details_category),
            category?.let { stringResource(it.singleLabel()) } ?: stringResource(R.string.details_not_sorted),
        )
        if (details?.record != null && category != null) {
            DetailRow(
                stringResource(R.string.details_confidence),
                if (details.setByUser) stringResource(R.string.details_set_by_you) else Formatting.percent(details.record.confidence),
            )
            if (!details.setByUser) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.details_why), style = MaterialTheme.typography.titleSmall)
                if (details.reasons.isEmpty()) {
                    Text(stringResource(R.string.details_no_reasons), style = MaterialTheme.typography.bodyMedium)
                } else {
                    details.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("why_reason")) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onMove) {
            Text(
                stringResource(R.string.action_move_to_category) +
                    (category?.let { " (" + stringResource(it.pluralLabel()) + ")" } ?: ""),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
