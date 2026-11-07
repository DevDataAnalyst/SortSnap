package com.sortsnap.classifier

/**
 * Entry point used by the scanner.
 *
 * - [firstPass] runs only metadata rules (cheap, used for every image).
 * - [needsMlPass] decides whether to spend battery on ML Kit.
 * - [classify] runs every rule whose inputs are present and fuses rule + ML + folder prior.
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
    ): ClassificationResult {
        val signals = evaluate(firstPassRules, features) + priorSignal(features, folderPrior)
        return ScoreFusion.decide(signals, config, fallback, usedMl = false)
    }

    fun needsMlPass(firstPass: ClassificationResult): Boolean = firstPass.confidence < config.mlSkipConfidence

    fun classify(
        features: ImageFeatures,
        folderPrior: Category? = null,
        fallback: Category = Category.OTHER,
    ): ClassificationResult {
        val signals = evaluate(firstPassRules, features) +
            (if (features.hasMlFeatures) evaluate(mlRules, features) else emptyList()) +
            priorSignal(features, folderPrior)
        return ScoreFusion.decide(signals, config, fallback, usedMl = features.hasMlFeatures)
    }

    private fun evaluate(rules: List<ClassifierRule>, features: ImageFeatures): List<Signal> =
        rules.mapNotNull { it.evaluate(features, config) }

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
