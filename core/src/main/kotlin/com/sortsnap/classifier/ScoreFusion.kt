package com.sortsnap.classifier

/**
 * Combines signals into normalised per-category scores.
 *
 * total(c) = ruleWeight * Σ rule(c) + mlWeight * Σ ml(c) + Σ prior(c), clamped at 0.
 * score(c) = total(c) / (Σ total + smoothing)   (each category's share, for display/debugging)
 * confidence = top / (top + runnerUp + smoothing)
 *
 * Confidence is a margin against the strongest competitor rather than a share of all mass, so weak
 * context evidence spread across several categories (e.g. "saved in WhatsApp") does not drown a
 * decisive ML signal, while a close race between two categories still yields low confidence.
 */
object ScoreFusion {

    fun rawTotals(signals: List<Signal>, config: ClassifierConfig): Map<Category, Float> {
        val totals = Category.SCORABLE.associateWith { 0f }.toMutableMap()
        for (s in signals) {
            val w = when (s.source) {
                SignalSource.RULE -> config.ruleWeight
                SignalSource.ML -> config.mlWeight
                SignalSource.PRIOR -> 1f
            }
            for ((cat, v) in s.scores) {
                if (cat == Category.OTHER) continue
                totals[cat] = (totals[cat] ?: 0f) + v * w
            }
        }
        return totals.mapValues { (_, v) -> v.coerceAtLeast(0f) }
    }

    fun normalise(totals: Map<Category, Float>, config: ClassifierConfig): Map<Category, Float> {
        val sum = totals.values.sum()
        if (sum <= 0f) return totals.mapValues { 0f }
        val denominator = sum + config.normalisationSmoothing
        return totals.mapValues { (_, v) -> (v / denominator).coerceIn(0f, 1f) }
    }

    fun confidence(totals: Map<Category, Float>, config: ClassifierConfig): Float {
        val sorted = totals.values.sortedDescending()
        val top = sorted.getOrElse(0) { 0f }
        if (top <= 0f) return 0f
        val runnerUp = sorted.getOrElse(1) { 0f }
        return (top / (top + runnerUp + config.normalisationSmoothing)).coerceIn(0f, 1f)
    }

    fun decide(
        signals: List<Signal>,
        config: ClassifierConfig,
        fallback: Category = Category.OTHER,
        usedMl: Boolean = false,
    ): ClassificationResult {
        val totals = rawTotals(signals, config)
        val scores = normalise(totals, config)
        val confidence = confidence(totals, config)
        val top = totals.maxByOrNull { it.value }
        val category = when {
            top == null || top.value <= 0f -> fallback
            confidence < config.minConfidence -> fallback
            else -> top.key
        }
        return ClassificationResult(
            category = category,
            confidence = confidence,
            signals = signals.map { it.id },
            evidence = signals,
            scores = scores,
            usedMl = usedMl,
        )
    }
}
