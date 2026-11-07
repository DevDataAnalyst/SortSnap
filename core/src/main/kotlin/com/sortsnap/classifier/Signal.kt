package com.sortsnap.classifier

import kotlinx.serialization.Serializable

@Serializable
enum class SignalSource { RULE, ML, PRIOR }

/**
 * One piece of evidence that fired for an image. [scores] are signed per-category contributions
 * (already multiplied by their weight); [detail] carries a human-readable parameter such as the
 * chat app or folder name, used by [SignalExplainer].
 */
@Serializable
data class Signal(
    val id: String,
    val scores: Map<Category, Float>,
    val source: SignalSource = SignalSource.RULE,
    val detail: String? = null,
)

data class ClassificationResult(
    val category: Category,
    val confidence: Float,
    /** Ids of the signals that fired, in evaluation order. */
    val signals: List<String>,
    /** Full evidence, serialised to `signalsJson` in Room. */
    val evidence: List<Signal> = emptyList(),
    /** Normalised per-category scores (0..1, summing to <= 1). */
    val scores: Map<Category, Float> = emptyMap(),
    val usedMl: Boolean = false,
) {
    /** The category with the best score even when confidence was below the threshold. */
    val bestGuess: Category
        get() = scores.maxByOrNull { it.value }?.takeIf { it.value > 0f }?.key ?: Category.OTHER
}
