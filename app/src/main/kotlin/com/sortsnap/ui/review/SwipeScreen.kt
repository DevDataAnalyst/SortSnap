package com.sortsnap.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.sortsnap.R
import com.sortsnap.classifier.Category
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.domain.CorrectionUseCase
import com.sortsnap.domain.Formatting
import com.sortsnap.domain.GalleryItem
import com.sortsnap.domain.ReviewRepository
import com.sortsnap.domain.UndoStack
import com.sortsnap.ui.components.CategoryPickerDialog
import com.sortsnap.ui.components.DeleteFlowHost
import com.sortsnap.ui.components.EmptyState
import com.sortsnap.ui.components.deleteResultMessage
import com.sortsnap.ui.components.rememberDeleteFlowState
import com.sortsnap.ui.components.singleLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

sealed interface SwipeAction {
    val item: GalleryItem
    data class Trash(override val item: GalleryItem) : SwipeAction
    data class Keep(override val item: GalleryItem) : SwipeAction
    data class Move(override val item: GalleryItem, val from: Category?, val to: Category) : SwipeAction
}

data class SwipeState(
    val queue: List<GalleryItem> = emptyList(),
    val marked: List<GalleryItem> = emptyList(),
    val canUndo: Boolean = false,
    val loading: Boolean = true,
)

/**
 * Left = mark for trash, right = keep, up = move category. Marked items are trashed together with a
 * single system confirmation. The last 10 actions can be undone (including category moves).
 */
@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val reviews: ReviewRepository,
    private val corrections: CorrectionUseCase,
    val deleter: MediaDeleter,
) : ViewModel() {
    private val undo = UndoStack<SwipeAction>(10)
    private val _state = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        _state.value = SwipeState(queue = reviews.swipeQueue(), loading = false)
        undo.clear()
    }

    fun act(action: SwipeAction) {
        undo.push(action)
        val s = _state.value
        _state.value = s.copy(
            queue = s.queue.drop(1),
            marked = if (action is SwipeAction.Trash) s.marked + action.item else s.marked,
            canUndo = true,
        )
        when (action) {
            is SwipeAction.Move -> viewModelScope.launch { corrections.moveToCategory(listOf(action.item.id), action.to) }
            // Remember "keep" so the image is not offered again next time.
            is SwipeAction.Keep -> viewModelScope.launch { reviews.markKept(action.item.id) }
            is SwipeAction.Trash -> Unit
        }
    }

    fun undoLast() {
        val action = undo.pop() ?: return
        val s = _state.value
        _state.value = s.copy(
            queue = listOf(action.item) + s.queue,
            marked = if (action is SwipeAction.Trash) s.marked - action.item else s.marked,
            canUndo = undo.canUndo,
        )
        if (action is SwipeAction.Move) {
            // Restore the category the item had before the swipe.
            viewModelScope.launch { action.from?.let { corrections.moveToCategory(listOf(action.item.id), it) } }
        }
    }

    fun onTrashed() {
        _state.value = _state.value.copy(marked = emptyList(), canUndo = false)
        undo.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(onBack: () -> Unit, viewModel: SwipeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deleteFlow = rememberDeleteFlowState(viewModel.deleter)
    DeleteFlowHost(deleteFlow) { step ->
        if (step is com.sortsnap.data.delete.DeleteStep.Done) viewModel.onTrashed()
        scope.launch { snackbar.showSnackbar(deleteResultMessage(context, step)) }
    }
    val top = state.queue.firstOrNull()
    if (picking && top != null) {
        CategoryPickerDialog(
            current = top.category,
            onPick = { picking = false; viewModel.act(SwipeAction.Move(top, top.category, it)) },
            onDismiss = { picking = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.swipe_mode)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = viewModel::undoLast, enabled = state.canUndo, modifier = Modifier.testTag("swipe_undo")) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(stringResource(R.string.swipe_undo))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.marked.isNotEmpty()) {
                Button(
                    onClick = { deleteFlow.start(state.marked.map { it.toDeleteTarget() }) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("swipe_commit"),
                ) {
                    Text(stringResource(R.string.swipe_commit, state.marked.size) + " · " + Formatting.bytes(state.marked.sumOf { it.sizeBytes }))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> Unit
                top == null -> EmptyState(title = stringResource(R.string.swipe_mode), body = stringResource(R.string.swipe_empty))
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SwipeCard(
                        item = top,
                        onLeft = { viewModel.act(SwipeAction.Trash(top)) },
                        onRight = { viewModel.act(SwipeAction.Keep(top)) },
                        onUp = { picking = true },
                    )
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        FilledTonalIconButton(onClick = { viewModel.act(SwipeAction.Trash(top)) }, modifier = Modifier.testTag("swipe_trash")) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.swipe_trash))
                        }
                        FilledTonalIconButton(onClick = { viewModel.act(SwipeAction.Keep(top)) }, modifier = Modifier.testTag("swipe_keep")) {
                            Icon(Icons.Filled.Check, stringResource(R.string.swipe_keep))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeCard(item: GalleryItem, onLeft: () -> Unit, onRight: () -> Unit, onUp: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dx = remember(item.id) { Animatable(0f) }
    val dy = remember(item.id) { Animatable(0f) }
    var armed by remember(item.id) { mutableStateOf(false) }
    val threshold = 280f
    ElevatedCard(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dx.value
                translationY = dy.value
                rotationZ = dx.value / 40f
            }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragEnd = {
                        val x = dx.value
                        val y = dy.value
                        when {
                            x < -threshold -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLeft() }
                            x > threshold -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onRight() }
                            y < -threshold && abs(x) < threshold -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { dx.animateTo(0f); dy.animateTo(0f) }
                                onUp()
                            }
                            else -> scope.launch { dx.animateTo(0f) }.also { scope.launch { dy.animateTo(0f) } }
                        }
                        armed = false
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { dx.snapTo(dx.value + drag.x); dy.snapTo(dy.value + drag.y) }
                    val past = abs(dx.value) > threshold || dy.value < -threshold
                    if (past && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    armed = past
                }
            }
            .testTag("swipe_card"),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)),
        )
        val label = item.category?.let { stringResource(it.singleLabel()) }.orEmpty()
        Text("$label · ${Formatting.bytes(item.sizeBytes)}", modifier = Modifier.padding(12.dp))
    }
}
