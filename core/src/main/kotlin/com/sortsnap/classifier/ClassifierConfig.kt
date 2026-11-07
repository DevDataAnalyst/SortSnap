package com.sortsnap.classifier

/**
 * Every weight and threshold the classifier uses, in one place so it can be tuned against the
 * evaluation harness. Bump [VERSION] whenever defaults change so the scanner reclassifies.
 */
data class ClassifierConfig(
    // ---- Decision ----
    /** Below this normalised top score the result is the fallback category (OTHER by default). */
    val minConfidence: Float = 0.55f,
    /** First-pass results at or above this confidence skip the ML pass to save battery. */
    val mlSkipConfidence: Float = 0.85f,
    /** Pseudo-mass added to the denominator so sparse evidence is not over-confident. */
    val normalisationSmoothing: Float = 1.0f,

    // ---- Fusion ----
    val ruleWeight: Float = 1.0f,
    val mlWeight: Float = 1.0f,
    /** Bias added to a folder's learned category (see FolderPriorLogic). */
    val folderPriorWeight: Float = 2.0f,

    // ---- Screenshot rules ----
    val screenshotFolderWeight: Float = 3.0f,
    val screenshotFilenameWeight: Float = 2.5f,
    val screenshotDeviceDimensionWeight: Float = 1.5f,
    val screenshotCommonDimensionWeight: Float = 0.8f,
    val pngScreenshotWeight: Float = 0.5f,
    val noExifScreenshotWeight: Float = 0.4f,
    val noExifMemeWeight: Float = 0.2f,
    val noExifForwardWeight: Float = 0.2f,
    val noExifPhotoPenalty: Float = -0.8f,

    // ---- Chat app ----
    val chatMemeWeight: Float = 0.6f,
    val chatForwardWeight: Float = 0.6f,
    val chatPhotoPenalty: Float = -0.5f,

    // ---- Camera ----
    val cameraFolderWeight: Float = 2.0f,
    val cameraExifWeight: Float = 1.5f,
    val focalLengthWeight: Float = 0.5f,
    val gpsWeight: Float = 0.8f,

    // ---- Downloads / social saves ----
    val downloadMemeWeight: Float = 0.8f,
    val downloadForwardWeight: Float = 0.4f,
    val downloadDocumentWeight: Float = 0.3f,
    val downloadPhotoPenalty: Float = -0.3f,

    // ---- ML: memes ----
    val memeTopBottomCaptionWeight: Float = 2.0f,
    val memeSingleCaptionWeight: Float = 1.0f,
    val memeSingleCaptionMaxLines: Int = 4,
    val memeLabelWeight: Float = 1.2f,

    // ---- ML: forwards ----
    val greetingWeight: Float = 2.5f,
    val greetingLargeTextBonus: Float = 0.5f,
    val largeTextCoverage: Float = 0.08f,

    // ---- ML: documents ----
    val receiptStrongWeight: Float = 3.0f,
    val receiptWeakWeight: Float = 1.0f,
    val receiptStrongMinHits: Int = 2,
    val documentLayoutWeight: Float = 1.5f,
    val documentMinTextCoverage: Float = 0.25f,
    val documentMinBrightness: Float = 0.6f,
    val documentMinUniformity: Float = 0.45f,

    // ---- ML: screenshots ----
    val uiChromeWeight: Float = 1.5f,
    val uiChromeMinHits: Int = 2,
    /** Each UI hit beyond the minimum adds this much, capped at [uiChromeMaxWeight]. */
    val uiChromeExtraHitWeight: Float = 0.5f,
    val uiChromeMaxWeight: Float = 3.5f,

    // ---- ML: photos ----
    val photoLabelWeight: Float = 2.5f,
    val photoLabelMaxTextCoverage: Float = 0.05f,
    val photoLabelMinConfidence: Float = 0.6f,
    val heavyTextCoverage: Float = 0.15f,
    val heavyTextPhotoPenalty: Float = -2.5f,
    /** ML ran and found no text at all: memes, forwards and documents almost always have text. */
    val noTextMemePenalty: Float = -1.0f,
    val noTextForwardPenalty: Float = -1.0f,
    val noTextDocumentPenalty: Float = -1.0f,

    // ---- Clean-up ----
    /** Variance of Laplacian on a 256px thumbnail below which a photo counts as blurry. */
    val blurVarianceThreshold: Double = 60.0,
    /** Max Hamming distance between dHashes to count as near-duplicates. */
    val nearDuplicateMaxDistance: Int = 4,
) {
    companion object {
        /** v1 = rule-based first pass, v2 = ML second pass + fusion. */
        const val VERSION: Int = 2

        val DEFAULT = ClassifierConfig()
    }
}
