package com.sortsnap.classifier

/**
 * Entry point used by the scanner.
 *
 * - [firstPass] runs only metadata rules (cheap, used for every image).
 * - [needsMlPass] decides whether to spend battery on ML Kit.
 * - [classify] runs every rule whose inputs are present and fuses rule + ML + folder prior.
 *
 * Both accept an optional [PersonalModel] learned from the user's own sorting; its vote is added as
 * one more bounded piece of evidence.
 */
class SortSnapClassifier(
    private val config: ClassifierConfig = ClassifierConfig.DEFAULT,
    private val firstPassRules: List<ClassifierRule> = DefaultRules.FIRST_PASS,
    private val mlRules: List<ClassifierRule> = DefaultRules.ML_PASS,
) {
    val version: Int get() = ClassifierConfig.VERSION

    fun firstPass(
        features: ImageFeatures,
        folderPrior: Category? = null,
        fallback: Category = Category.OTHER,
        personal: PersonalModel? = null,
    ): ClassificationResult {
        val evidence = evaluate(firstPassRules, features)
        return decide(evidence, features, folderPrior, fallback, personal, usedMl = false)
    }

    fun needsMlPass(firstPass: ClassificationResult): Boolean = firstPass.confidence < config.mlSkipConfidence

    fun classify(
        features: ImageFeatures,
        folderPrior: Category? = null,
        fallback: Category = Category.OTHER,
        personal: PersonalModel? = null,
    ): ClassificationResult {
        val evidence = evaluate(firstPassRules, features) +
            (if (features.hasMlFeatures) evaluate(mlRules, features) else emptyList())
        return decide(evidence, features, folderPrior, fallback, personal, usedMl = features.hasMlFeatures)
    }

    private fun evaluate(rules: List<ClassifierRule>, features: ImageFeatures): List<Signal> =
        rules.mapNotNull { it.evaluate(features, config) }

    /** Fuses evidence + folder prior; if still unsure, lets the user's own sorting settle it. */
    private fun decide(
        evidence: List<Signal>,
        features: ImageFeatures,
        folderPrior: Category?,
        fallback: Category,
        personal: PersonalModel?,
        usedMl: Boolean,
    ): ClassificationResult {
        val signals = evidence + priorSignal(features, folderPrior)
        val base = ScoreFusion.decide(signals, config, fallback, usedMl)
        if (personal == null || base.confidence >= config.personalMaxBaseConfidence) return base
        val vote = personal.signal(evidence, config) ?: return base
        return ScoreFusion.decide(signals + vote, config, fallback, usedMl)
    }

    private fun priorSignal(features: ImageFeatures, prior: Category?): List<Signal> {
        if (prior == null || prior == Category.OTHER) return emptyList()
        return listOf(
            Signal(
                id = FOLDER_PRIOR_ID,
                scores = mapOf(prior to config.folderPriorWeight),
                source = SignalSource.PRIOR,
                detail = features.bucket.ifBlank { features.relativePath.trimEnd('/').substringAfterLast('/') },
            ),
        )
    }

    companion object {
        const val FOLDER_PRIOR_ID = "folder_prior"
    }
}
