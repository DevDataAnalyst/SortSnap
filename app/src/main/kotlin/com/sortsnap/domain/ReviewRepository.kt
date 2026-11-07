package com.sortsnap.domain

import com.sortsnap.classifier.Category
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.db.ReviewAnswerEntity
import com.sortsnap.data.db.ReviewDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepository @Inject constructor(
    private val images: ImageDao,
    private val reviews: ReviewDao,
    private val corrections: CorrectionUseCase,
) {
    /** Today's "Is this right?" queue: 20 random unsure (0.55–0.75) images, stable for the day. */
    suspend fun todaysCards(epochDay: Long, alreadyAnsweredToday: Int): List<ImageRecord> {
        val remaining = (DailyReviewPicker.DAILY_COUNT - alreadyAnsweredToday).coerceAtLeast(0)
        if (remaining == 0) return emptyList()
        val candidates = images.unsureCandidates(DailyReviewPicker.MIN_CONFIDENCE, DailyReviewPicker.MAX_CONFIDENCE)
        val picked = DailyReviewPicker.pick(candidates, epochDay, remaining)
        return images.byIds(picked).sortedBy { picked.indexOf(it.mediaId) }
    }

    fun answeredOn(epochDay: Long) = reviews.answeredOn(epochDay)

    suspend fun answerCorrect(id: Long, epochDay: Long) {
        corrections.confirm(id)
        reviews.answer(ReviewAnswerEntity(id, epochDay, wasCorrect = true))
    }

    suspend fun answerWrong(id: Long, correct: Category, epochDay: Long) {
        corrections.moveToCategory(listOf(id), correct)
        reviews.answer(ReviewAnswerEntity(id, epochDay, wasCorrect = false))
    }

    suspend fun skip(id: Long, epochDay: Long) {
        reviews.answer(ReviewAnswerEntity(id, epochDay, wasCorrect = true))
    }

    suspend fun markKept(id: Long, epochDay: Long = DateBuckets.epochDay(System.currentTimeMillis())) {
        reviews.answer(ReviewAnswerEntity(id, epochDay, wasCorrect = true))
    }

    suspend fun swipeQueue(limit: Int = 200): List<GalleryItem> =
        images.swipeQueue(listOf(Category.SCREENSHOT, Category.MEME, Category.FORWARD), limit).map { it.toGalleryItem() }
}
