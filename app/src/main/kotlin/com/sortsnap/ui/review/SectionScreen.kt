package com.sortsnap.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortsnap.R
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.domain.CleanupRepository
import com.sortsnap.domain.CleanupSection
import com.sortsnap.domain.Formatting
import com.sortsnap.domain.SectionContent
import com.sortsnap.ui.components.DeleteFlowHost
import com.sortsnap.ui.components.EmptyState
import com.sortsnap.ui.components.GridSkeleton
import com.sortsnap.ui.components.StaticGalleryGrid
import com.sortsnap.ui.components.deleteResultMessage
import com.sortsnap.ui.components.rememberDeleteFlowState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectionUiState(val content: SectionContent, val selected: Set<Long>) {
    val selectedBytes: Long get() = content.all.filter { it.id in selected }.sumOf { it.sizeBytes }
}

/** Everything starts selected (except the best copy of each near-duplicate group); tap to keep. */
@HiltViewModel
class SectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    cleanup: CleanupRepository,
    val deleter: MediaDeleter,
) : ViewModel() {
    val section: CleanupSection = CleanupSection.valueOf(savedState.get<String>("section") ?: CleanupSection.MEMES.name)
    private val keepers = MutableStateFlow<Set<Long>?>(null)

    val state: StateFlow<SectionUiState?> = combine(cleanup.content(section), keepers) { content, keep ->
        val keepSet = keep ?: content.keepByDefault
        SectionUiState(content, content.all.map { it.id }.filterNot { it in keepSet }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(id: Long) {
        val defaults = state.value?.content?.keepByDefault ?: emptySet()
        keepers.update { current -> val k = current ?: defaults; if (id in k) k - id else k + id }
    }

    fun targets() = state.value?.let { s -> s.content.all.filter { it.id in s.selected }.map { it.toDeleteTarget() } }.orEmpty()

    fun resetKeepers() = viewModelScope.launch { keepers.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(onBack: () -> Unit, viewModel: SectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deleteFlow = rememberDeleteFlowState(viewModel.deleter)
    DeleteFlowHost(deleteFlow) { step ->
        viewModel.resetKeepers()
        scope.launch { snackbar.showSnackbar(deleteResultMessage(context, step)) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(viewModel.section.titleRes())) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val s = state
            if (s != null && s.content.all.isNotEmpty()) {
                Button(
                    onClick = { deleteFlow.start(viewModel.targets()) },
                    enabled = s.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("trash_selected"),
                ) {
                    Text(stringResource(R.string.trash_selected, s.selected.size, Formatting.bytes(s.selectedBytes)))
                }
            }
        },
    ) { padding ->
        val s = state
        when {
            s == null -> GridSkeleton(4, Modifier.padding(padding))
            s.content.all.isEmpty() -> EmptyState(
                title = stringResource(viewModel.section.titleRes()),
                body = stringResource(R.string.section_empty),
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    stringResource(R.string.section_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                StaticGalleryGrid(
                    groups = s.content.groups,
                    columns = 4,
                    selected = s.selected,
                    onToggle = { viewModel.toggle(it.id) },
                    groupTitle = if (viewModel.section == CleanupSection.NEAR_DUPLICATES) {
                        { g -> "${g + 1}" }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
