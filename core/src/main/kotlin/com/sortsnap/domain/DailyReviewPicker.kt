package com.sortsnap.domain

import kotlin.random.Random

/**
 * Picks the day's "Is this right?" cards: up to [count] images whose confidence is in the unsure
 * band. Deterministic per day so the set is stable across app restarts.
 */
object DailyReviewPicker {
    const val MIN_CONFIDENCE = 0.55f
    const val MAX_CONFIDENCE = 0.75f
    const val DAILY_COUNT = 20

    fun isCandidate(confidence: Float, hasOverride: Boolean): Boolean =
        !hasOverride && confidence >= MIN_CONFIDENCE && confidence <= MAX_CONFIDENCE

    fun pick(candidateIds: List<Long>, epochDay: Long, count: Int = DAILY_COUNT): List<Long> =
        candidateIds.sorted().shuffled(Random(epochDay)).take(count)
}
