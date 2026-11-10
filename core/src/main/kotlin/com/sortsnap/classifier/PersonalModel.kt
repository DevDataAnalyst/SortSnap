package com.sortsnap.classifier

/** One image the user sorted by hand: the evidence the classifier saw, and the user's answer. */
data class LabelledExample(val signals: List<Signal>, val category: Category)

/** Outcome of asking the personal model about one image. */
data class PersonalVote(val category: Category, val agreement: Float, val support: Int)

/**
 * On-device personal learning: "images that look like this, you put in X".
 *
 * Every image the user moved or confirmed keeps the evidence the classifier saw (`signalsJson`).
 * Each image is reduced to an evidence *fingerprint*: the set of signal ids that fired (folder-type
 * signals keep their detail, so WhatsApp and Telegram differ). A new image is compared with every
 * hand-sorted fingerprint by Jaccard similarity; similar ones vote, weighted by similarity. Only a
 * clear majority with enough support produces a signal, and its weight is bounded by
 * [ClassifierConfig.personalWeight], so strong evidence (camera EXIF, a Screenshots folder) still
 * wins while ambiguous patterns follow the user's own taste.
 *
 * This generalises the per-folder learned rules: it also learns things like "Telegram images with
 * a short caption and no camera data are forwards for me", across folders.
 *
 * Identical fingerprints are collapsed, so a vote costs O(distinct patterns), typically a few dozen
 * even with thousands of examples.
 */
class PersonalModel private constructor(
    private val patterns: List<Pattern>,
    /** Number of hand-sorted examples the model was built from. */
    val exampleCount: Int,
) {
    private class Pattern(val keys: Set<String>, val firstPassKeys: Set<String>, val counts: Map<Category, Int>)

    val isEmpty: Boolean get() = patterns.isEmpty()

    /** Distinct evidence patterns learned. */
    val patternCount: Int get() = patterns.size

    fun vote(signals: List<Signal>, config: ClassifierConfig): PersonalVote? {
        if (patterns.isEmpty()) return null
        val keys = fingerprint(signals)
        if (keys.isEmpty()) return null
        // A first-pass query has no ML evidence yet: compare against the examples' metadata evidence only.
        val firstPassOnly = signals.none { it.source == SignalSource.ML }
        val weights = HashMap<Category, Float>()
        var support = 0
        for (p in patterns) {
            val other = if (firstPassOnly) p.firstPassKeys else p.keys
            val sim = jaccard(keys, other)
            if (sim < config.personalMinSimilarity) continue
            for ((cat, n) in p.counts) {
                weights[cat] = (weights[cat] ?: 0f) + sim * n
                support += n
            }
        }
        if (support < config.personalMinSupport) return null
        val total = weights.values.sum()
        val best = weights.maxByOrNull { it.value } ?: return null
        val agreement = if (total > 0f) best.value / total else 0f
        if (agreement < config.personalMinAgreement) return null
        return PersonalVote(best.key, agreement, support)
    }

    /** The vote as fusion evidence, or null when the model has nothing confident to say. */
    fun signal(signals: List<Signal>, config: ClassifierConfig): Signal? {
        val vote = vote(signals, config) ?: return null
        return Signal(
            id = SIGNAL_ID,
            scores = mapOf(vote.category to config.personalWeight * vote.agreement),
            source = SignalSource.PERSONAL,
            detail = vote.support.toString(),
        )
    }

    companion object {
        const val SIGNAL_ID = "personal_similar"

        val EMPTY = PersonalModel(emptyList(), 0)

        /** Signals whose detail matters for similarity (which chat app / which download source). */
        private val DETAIL_KEYED = setOf(ChatAppRule.id, DownloadsRule.id)

        /**
         * Builds the model from hand-sorted examples (most recent first). OTHER answers and examples
         * without evidence are ignored; at most [ClassifierConfig.personalMaxExamples] are used.
         */
        fun build(examples: List<LabelledExample>, config: ClassifierConfig = ClassifierConfig.DEFAULT): PersonalModel {
            val used = examples.asSequence()
                .filter { it.category != Category.OTHER }
                .map { fingerprintWithFirstPass(it.signals) to it.category }
                .filter { it.first.first.isNotEmpty() }
                .take(config.personalMaxExamples)
                .toList()
            val grouped = used.groupBy({ it.first }, { it.second }).map { (fp, cats) ->
                Pattern(fp.first, fp.second, cats.groupingBy { it }.eachCount())
            }
            return PersonalModel(grouped, used.size)
        }

        /** Evidence fingerprint: classifier signals only (never earlier priors or personal votes). */
        fun fingerprint(signals: List<Signal>): Set<String> = signals.asSequence()
            .filter { it.source == SignalSource.RULE || it.source == SignalSource.ML }
            .map { key(it) }
            .toSet()

        private fun fingerprintWithFirstPass(signals: List<Signal>): Pair<Set<String>, Set<String>> {
            val all = fingerprint(signals)
            val firstPass = signals.asSequence().filter { it.source == SignalSource.RULE }.map { key(it) }.toSet()
            return all to firstPass
        }

        private fun key(s: Signal): String =
            if (s.id in DETAIL_KEYED && !s.detail.isNullOrBlank()) "${s.id}:${s.detail.lowercase()}" else s.id

        fun jaccard(a: Set<String>, b: Set<String>): Float {
            if (a.isEmpty() && b.isEmpty()) return 1f
            val inter = a.count { it in b }
            val union = a.size + b.size - inter
            return if (union == 0) 0f else inter.toFloat() / union
        }
    }
}
