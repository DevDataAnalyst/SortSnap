package com.sortsnap.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.sortsnap.classifier.Category
import com.sortsnap.classifier.SignalCodec
import com.sortsnap.classifier.SignalExplainer
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.domain.CorrectionUseCase
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.domain.GalleryItem
import com.sortsnap.domain.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageDetails(
    val record: ImageRecord?,
    val reasons: List<String>,
) {
    val category: Category? get() = record?.effectiveCategory
    val setByUser: Boolean get() = record?.userOverride != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    gallery: GalleryRepository,
    private val images: ImageDao,
    private val corrections: CorrectionUseCase,
    val deleter: MediaDeleter,
) : ViewModel() {
    val filter: GalleryFilter = GalleryFilter.decode(savedState.get<String>("filter"))
    val initialIndex: Int = savedState.get<String>("index")?.toIntOrNull() ?: savedState.get<Int>("index") ?: 0

    val pages: Flow<PagingData<GalleryItem>> = flow {
        emit(images.count() > 0)
    }.flatMapLatest { ready -> gallery.viewerPager(filter, ready, initialIndex) }.cachedIn(viewModelScope)

    private val currentId = MutableStateFlow<Long?>(null)

    val details: StateFlow<ImageDetails?> = currentId.flatMapLatest { id ->
        if (id == null) flowOf(null) else images.observe(id).map { record ->
            val category = record?.effectiveCategory
            ImageDetails(
                record = record,
                reasons = if (record == null || category == null || record.userOverride != null) {
                    emptyList()
                } else {
                    SignalExplainer.explain(SignalCodec.decode(record.signalsJson), category)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPageShown(id: Long) { currentId.value = id }

    fun move(id: Long, category: Category) = viewModelScope.launch { corrections.moveToCategory(listOf(id), category) }
}
