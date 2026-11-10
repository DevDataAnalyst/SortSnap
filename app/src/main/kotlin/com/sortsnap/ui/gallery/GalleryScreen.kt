package com.sortsnap.ui.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.sortsnap.R
import com.sortsnap.classifier.Category
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.domain.GalleryItem
import com.sortsnap.data.scan.ScanStatus
import com.sortsnap.ui.components.GalleryEntry
import com.sortsnap.ui.components.CategoryPickerDialog
import com.sortsnap.ui.components.DeleteFlowHost
import com.sortsnap.ui.components.EmptyState
import com.sortsnap.ui.components.PagedGalleryGrid
import com.sortsnap.ui.components.ScanProgressBar
import com.sortsnap.ui.components.deleteResultMessage
import com.sortsnap.ui.components.pluralLabel
import com.sortsnap.ui.components.rememberDeleteFlowState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onOpenViewer: (filter: GalleryFilter, index: Int) -> Unit,
    onBack: (() -> Unit)? = null,
    banner: @Composable () -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val scan by viewModel.scanStatus.collectAsStateWithLifecycle()
    val entries = viewModel.entries.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    val deleteFlow = rememberDeleteFlowState(viewModel.deleter)

    DeleteFlowHost(deleteFlow) { step ->
        viewModel.clearSelection()
        entries.refresh()
        scope.launch { snackbar.showSnackbar(deleteResultMessage(context, step)) }
    }
    if (picking) {
        CategoryPickerDialog(
            current = null,
            onPick = { picking = false; viewModel.moveSelected(it) },
            onDismiss = { picking = false },
        )
    }

    val title = (viewModel.pinnedFilter as? GalleryFilter.Only)?.category?.let { stringResource(it.pluralLabel()) }
        ?: stringResource(R.string.nav_gallery)
    GalleryContent(
        title = title,
        onBack = onBack,
        scan = scan,
        showFilters = viewModel.pinnedFilter == null,
        filter = filter,
        onSelectFilter = viewModel::setFilter,
        entries = entries,
        columns = columns,
        selected = selected,
        onClearSelection = viewModel::clearSelection,
        onMoveSelected = { picking = true },
        onTrashSelected = { deleteFlow.start(viewModel.selectedItems().map { it.toDeleteTarget() }) },
        onOpen = { index -> filter?.let { onOpenViewer(it, index) } },
        onToggleSelect = viewModel::toggle,
        onColumnsChange = { viewModel.setColumns(it) },
        banner = banner,
        snackbar = snackbar,
    )
}

/** Stateless body of [GalleryScreen]: top bar (or selection bar), scan progress, filter chips and the paged grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryContent(
    title: String,
    onBack: (() -> Unit)?,
    scan: ScanStatus,
    showFilters: Boolean,
    filter: GalleryFilter?,
    onSelectFilter: (GalleryFilter) -> Unit,
    entries: LazyPagingItems<GalleryEntry>,
    columns: Int,
    selected: Set<Long>,
    onClearSelection: () -> Unit,
    onMoveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onOpen: (index: Int) -> Unit,
    onToggleSelect: (GalleryItem) -> Unit,
    onColumnsChange: (Int) -> Unit,
    banner: @Composable () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            Column {
                if (selected.isNotEmpty()) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.selected_count, selected.size), modifier = Modifier.testTag("selection_count")) },
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                        actions = {
                            IconButton(onClick = onMoveSelected, modifier = Modifier.testTag("action_move")) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_move_to_category))
                            }
                            IconButton(onClick = onTrashSelected, modifier = Modifier.testTag("action_trash")) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_trash))
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            }
                        },
                    )
                }
                ScanProgressBar(scan)
                if (showFilters) {
                    banner()
                    FilterChips(current = filter, onSelect = onSelectFilter)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PagedGalleryGrid(
            entries = entries,
            columns = columns,
            selected = selected,
            onOpen = { index, _ -> onOpen(index) },
            onToggleSelect = onToggleSelect,
            onColumnsChange = onColumnsChange,
            modifier = Modifier.padding(padding).fillMaxSize(),
            emptyContent = {
                EmptyState(
                    title = stringResource(R.string.empty_gallery_title),
                    body = stringResource(if (filter == GalleryFilter.All) R.string.empty_gallery_body else R.string.empty_filter_body),
                    modifier = Modifier.padding(padding),
                )
            },
        )
    }
}

@Composable
fun FilterChips(current: GalleryFilter?, onSelect: (GalleryFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp).testTag("filter_chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = current == GalleryFilter.All,
            onClick = { onSelect(GalleryFilter.All) },
            label = { Text(stringResource(R.string.filter_all)) },
            modifier = Modifier.testTag("chip_all"),
        )
        FilterChip(
            selected = current == GalleryFilter.HideClutter,
            onClick = { onSelect(GalleryFilter.HideClutter) },
            label = { Text(stringResource(R.string.filter_photos_only)) },
            modifier = Modifier.testTag("chip_photos"),
        )
        Category.entries.forEach { c ->
            FilterChip(
                selected = current == GalleryFilter.Only(c),
                onClick = { onSelect(GalleryFilter.Only(c)) },
                label = { Text(stringResource(c.pluralLabel())) },
                modifier = Modifier.testTag("chip_${c.name}"),
            )
        }
    }
}
