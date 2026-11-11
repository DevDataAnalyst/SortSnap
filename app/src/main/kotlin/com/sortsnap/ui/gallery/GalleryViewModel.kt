package com.sortsnap.ui.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.sortsnap.classifier.Category
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.delete.DeleteTarget
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.data.scan.ScanScheduler
import com.sortsnap.data.scan.ScanStatus
import com.sortsnap.data.settings.SettingsRepository
import com.sortsnap.domain.CorrectionUseCase
import com.sortsnap.domain.DateBuckets
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.domain.GalleryItem
import com.sortsnap.domain.GalleryRepository
import com.sortsnap.ui.components.GalleryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the main Gallery (with filter chips) and the per-category album screen, which pins the
 * filter through the "filter" navigation argument.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val gallery: GalleryRepository,
    private val settings: SettingsRepository,
    private val corrections: CorrectionUseCase,
    images: ImageDao,
    scheduler: ScanScheduler,
    val deleter: MediaDeleter,
) : ViewModel() {

    /** Non-null when this is a fixed album (Categories > tile). */
    val pinnedFilter: GalleryFilter? = savedState.get<String>("filter")?.let(GalleryFilter::decode)

    private val chosenFilter = MutableStateFlow<GalleryFilter?>(pinnedFilter)

    init {
        if (pinnedFilter == null) {
            viewModelScope.launch {
                val s = settings.current()
                chosenFilter.value = if (s.hideClutter) GalleryFilter.HideClutter else GalleryFilter.All
            }
        }
    }

    val filter: StateFlow<GalleryFilter?> = chosenFilter.asStateFlow()

    val columns: StateFlow<Int> = settings.settings.map { it.gridColumns }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 4)

    val scanStatus: StateFlow<ScanStatus> = scheduler.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanStatus.IDLE)

    private val indexReady: Flow<Boolean> = images.countFlow().map { it > 0 }.distinctUntilChanged()

    val entries: Flow<PagingData<GalleryEntry>> = combine(chosenFilter.filterNotNull(), indexReady) { f, ready -> f to ready }
        .distinctUntilChanged()
        .flatMapLatest { (f, ready) -> gallery.pager(f, ready) }
        .map { paging -> withHeaders(paging, System.currentTimeMillis()) }
        .cachedIn(viewModelScope)

    private val _selected = MutableStateFlow<Map<Long, DeleteTarget>>(emptyMap())
    val selected: StateFlow<Set<Long>> = _selected.map { it.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** True while "Select all" loads the ids of every image in the current view. */
    private val _selectingAll = MutableStateFlow(false)
    val selectingAll: StateFlow<Boolean> = _selectingAll.asStateFlow()

    fun selectedTargets(): List<DeleteTarget> = _selected.value.values.toList()

    fun setFilter(f: GalleryFilter) {
        if (pinnedFilter == null) chosenFilter.value = f
    }

    fun setColumns(c: Int) = viewModelScope.launch { settings.setGridColumns(c) }

    fun toggle(item: GalleryItem) = _selected.update { if (item.id in it) it - item.id else it + (item.id to item.toDeleteTarget()) }

    /** Selects every image the current filter shows, not just the loaded pages (paging keeps the grid lazy). */
    fun selectAll() {
        val f = chosenFilter.value ?: return
        if (_selectingAll.value) return
        _selectingAll.value = true
        viewModelScope.launch {
            // A MediaStore failure leaves the current selection as it was rather than crashing.
            runCatching { gallery.allTargets(f, indexReady.first()) }
                .onSuccess { all -> _selected.value = all.associateBy { it.mediaId } }
            _selectingAll.value = false
        }
    }

    fun clearSelection() { _selected.value = emptyMap() }

    fun moveSelected(category: Category) = viewModelScope.launch {
        val ids = _selected.value.keys.toList()
        clearSelection()
        corrections.moveToCategory(ids, category)
    }

    /** Snapshot used to open the viewer at the same position. */
    suspend fun indexReadyNow(): Boolean = indexReady.first()

    companion object {
        fun withHeaders(paging: PagingData<GalleryItem>, now: Long): PagingData<GalleryEntry> =
            paging.map<GalleryItem, GalleryEntry> { GalleryEntry.Image(it) }
                .insertSeparators { before, after ->
                    val a = (after as? GalleryEntry.Image)?.item ?: return@insertSeparators null
                    val bucketAfter = DateBuckets.of(a.dateTaken, now)
                    val b = (before as? GalleryEntry.Image)?.item
                    if (b == null || DateBuckets.of(b.dateTaken, now) != bucketAfter) GalleryEntry.Header(bucketAfter) else null
                }
    }
}
