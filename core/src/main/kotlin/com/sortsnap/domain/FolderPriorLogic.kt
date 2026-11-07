package com.sortsnap.domain

import com.sortsnap.classifier.Category

/** One user correction, reduced to what folder learning needs. */
data class CorrectionEvent(val bucket: String, val predicted: Category?, val corrected: Category)

data class LearnedPrior(val bucket: String, val category: Category, val sampleCount: Int)

/**
 * Per-folder personal learning. If the user corrects [minCorrections]+ images from the same folder
 * to the same category (and that category is the clear majority of their corrections there), the
 * folder gets a bias towards it.
 *
 * When the user deletes a learned rule we remember the count at deletion ([dismissedAtCount]) and
 * only re-learn once they make another [minCorrections] corrections, so a deleted rule does not
 * immediately reappear.
 */
object FolderPriorLogic {
    const val DEFAULT_MIN_CORRECTIONS = 5

    fun learnForBucket(
        bucket: String,
        corrections: List<CorrectionEvent>,
        dismissedAtCount: Int? = null,
        minCorrections: Int = DEFAULT_MIN_CORRECTIONS,
    ): LearnedPrior? {
        if (bucket.isBlank()) return null
        val inBucket = corrections.filter { it.bucket == bucket && it.corrected != Category.OTHER }
        if (inBucket.isEmpty()) return null
        val (category, count) = inBucket.groupingBy { it.corrected }.eachCount().maxBy { it.value }
        if (count < minCorrections) return null
        if (count * 2 <= inBucket.size) return null // not a clear majority
        if (dismissedAtCount != null && count < dismissedAtCount + minCorrections) return null
        return LearnedPrior(bucket, category, count)
    }

    fun learnAll(
        corrections: List<CorrectionEvent>,
        dismissed: Map<String, Int> = emptyMap(),
        minCorrections: Int = DEFAULT_MIN_CORRECTIONS,
    ): List<LearnedPrior> = corrections.map { it.bucket }.distinct().sorted()
        .mapNotNull { learnForBucket(it, corrections, dismissed[it], minCorrections) }
}
