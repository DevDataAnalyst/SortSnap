package com.sortsnap.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortsnap.classifier.Category
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.domain.CleanupRepository
import com.sortsnap.domain.CleanupSection
import com.sortsnap.domain.DateBuckets
import com.sortsnap.domain.ReviewRepository
import com.sortsnap.domain.SectionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    cleanup: CleanupRepository,
    private val reviews: ReviewRepository,
) : ViewModel() {
    private val today = DateBuckets.epochDay(System.currentTimeMillis())

    val sections: StateFlow<List<SectionSummary>?> = combine(CleanupSection.entries.map { cleanup.summary(it) }) { it.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cards = MutableStateFlow<List<ImageRecord>?>(null)
    val cards: StateFlow<List<ImageRecord>?> = _cards.asStateFlow()

    init { loadCards() }

    private fun loadCards() = viewModelScope.launch {
        val answered = reviews.answeredOn(today).first().size
        _cards.value = reviews.todaysCards(today, answered)
    }

    private fun drop(id: Long) { _cards.value = _cards.value?.filterNot { it.mediaId == id } }

    fun confirm(id: Long) { drop(id); viewModelScope.launch { reviews.answerCorrect(id, today) } }
    fun correct(id: Long, category: Category) { drop(id); viewModelScope.launch { reviews.answerWrong(id, category, today) } }
    fun skip(id: Long) { drop(id); viewModelScope.launch { reviews.skip(id, today) } }
}
