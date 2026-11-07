package com.sortsnap.classifier

import com.sortsnap.classifier.Category.DOCUMENT
import com.sortsnap.classifier.Category.FORWARD
import com.sortsnap.classifier.Category.MEME
import com.sortsnap.classifier.Category.PHOTO
import com.sortsnap.classifier.Category.SCREENSHOT

/** One small, independently testable piece of classification evidence. */
interface ClassifierRule {
    val id: String
    val source: SignalSource get() = SignalSource.RULE
    fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal?
}

private fun ClassifierRule.signal(vararg scores: Pair<Category, Float>, detail: String? = null) =
    Signal(id = id, scores = mapOf(*scores), source = source, detail = detail)

// ---------------------------------------------------------------------------------------------
// First pass: metadata rules (fast, no pixels).
// ---------------------------------------------------------------------------------------------

object ScreenshotFolderRule : ClassifierRule {
    override val id = "screenshot_folder"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig) =
        if (FolderMatchers.isScreenshotFolder(features)) signal(SCREENSHOT to config.screenshotFolderWeight) else null
}

object ScreenshotFilenameRule : ClassifierRule {
    override val id = "screenshot_filename"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val name = features.displayName.lowercase()
        val hit = name.startsWith("screenshot") || name.startsWith("screen_shot") || name.startsWith("screen-shot")
        return if (hit) signal(SCREENSHOT to config.screenshotFilenameWeight) else null
    }
}

/** Exact device-screen match is strong evidence; a common phone resolution is weaker. */
object ScreenshotDimensionRule : ClassifierRule {
    override val id = "screenshot_dimension"
    const val DEVICE_ID = "screenshot_dimension_device"
    const val COMMON_ID = "screenshot_dimension_common"

    /** Portrait (width x height) resolutions of popular phones. */
    val COMMON_PHONE_RESOLUTIONS: Set<Pair<Int, Int>> = setOf(
        720 to 1280, 720 to 1520, 720 to 1600, 750 to 1334, 828 to 1792,
        1080 to 1920, 1080 to 2160, 1080 to 2220, 1080 to 2280, 1080 to 2340, 1080 to 2400, 1080 to 2412,
        1170 to 2532, 1179 to 2556, 1220 to 2712, 1242 to 2688, 1260 to 2800, 1284 to 2778, 1290 to 2796,
        1440 to 2560, 1440 to 2960, 1440 to 3040, 1440 to 3088, 1440 to 3120, 1440 to 3200,
    )

    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val w = features.width
        val h = features.height
        if (w <= 0 || h <= 0) return null
        if (features.screen?.matches(w, h) == true) {
            return Signal(DEVICE_ID, mapOf(SCREENSHOT to config.screenshotDeviceDimensionWeight), source)
        }
        val portrait = minOf(w, h) to maxOf(w, h)
        return if (portrait in COMMON_PHONE_RESOLUTIONS) {
            Signal(COMMON_ID, mapOf(SCREENSHOT to config.screenshotCommonDimensionWeight), source)
        } else {
            null
        }
    }
}

object PngMimeRule : ClassifierRule {
    override val id = "png_mime"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig) =
        if (features.mimeType.equals("image/png", ignoreCase = true)) signal(SCREENSHOT to config.pngScreenshotWeight) else null
}

/**
 * No camera EXIF at all. Skipped for camera folders (missing EXIF there is a stripping artefact) and
 * for chat-app folders (chat apps always strip EXIF, so it would double-count ChatAppRule).
 */
object NoCameraExifRule : ClassifierRule {
    override val id = "no_camera_exif"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        if (!features.exif.isEmpty || FolderMatchers.isCameraFolder(features)) return null
        if (FolderMatchers.chatApp(features) != null) return null
        return signal(
            SCREENSHOT to config.noExifScreenshotWeight,
            MEME to config.noExifMemeWeight,
            FORWARD to config.noExifForwardWeight,
            PHOTO to config.noExifPhotoPenalty,
        )
    }
}

object ChatAppRule : ClassifierRule {
    override val id = "chat_app_folder"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val app = FolderMatchers.chatApp(features) ?: return null
        return signal(
            MEME to config.chatMemeWeight,
            FORWARD to config.chatForwardWeight,
            PHOTO to config.chatPhotoPenalty,
            detail = app,
        )
    }
}

object CameraFolderRule : ClassifierRule {
    override val id = "camera_folder"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig) =
        if (FolderMatchers.isCameraFolder(features)) signal(PHOTO to config.cameraFolderWeight) else null
}

object CameraExifRule : ClassifierRule {
    override val id = "camera_exif"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val exif = features.exif
        if (!exif.hasCameraMakeModel) return null
        val detail = listOfNotNull(exif.make, exif.model).joinToString(" ").trim().ifBlank { null }
        return signal(PHOTO to config.cameraExifWeight, detail = detail)
    }
}

object FocalLengthRule : ClassifierRule {
    override val id = "exif_focal_length"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig) =
        if (features.exif.hasFocalLength) signal(PHOTO to config.focalLengthWeight) else null
}

object GpsRule : ClassifierRule {
    override val id = "exif_gps"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig) =
        if (features.exif.hasGps) signal(PHOTO to config.gpsWeight) else null
}

object DownloadsRule : ClassifierRule {
    override val id = "downloads_folder"
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val where = FolderMatchers.socialSave(features) ?: return null
        return signal(
            MEME to config.downloadMemeWeight,
            FORWARD to config.downloadForwardWeight,
            DOCUMENT to config.downloadDocumentWeight,
            PHOTO to config.downloadPhotoPenalty,
            detail = where,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Second pass: ML Kit derived rules. They only fire when text/labels are present.
// ---------------------------------------------------------------------------------------------

private fun ImageFeatures.labelConfidence(names: Set<String>): Float =
    labels.orEmpty().filter { it.name.lowercase() in names }.maxOfOrNull { it.confidence } ?: 0f

/** Text in both the top and bottom bands is the classic image-macro layout. */
object MemeCaptionRule : ClassifierRule {
    override val id = "meme_caption_layout"
    const val SINGLE_ID = "meme_single_caption"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        if (text.lineCount == 0) return null
        if (text.topBandText && text.bottomBandText) return signal(MEME to config.memeTopBottomCaptionWeight)
        if ((text.topBandText || text.bottomBandText) && text.lineCount <= config.memeSingleCaptionMaxLines) {
            return Signal(SINGLE_ID, mapOf(MEME to config.memeSingleCaptionWeight), source)
        }
        return null
    }
}

object MemeLabelRule : ClassifierRule {
    override val id = "meme_labels"
    override val source = SignalSource.ML
    val LABELS = setOf("cartoon", "poster", "comics", "meme", "illustration", "fictional character", "animation")
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val conf = features.labelConfidence(LABELS)
        if (conf <= 0f) return null
        val name = features.labels.orEmpty().filter { it.name.lowercase() in LABELS }.maxBy { it.confidence }.name
        return signal(MEME to config.memeLabelWeight * conf, detail = name)
    }
}

object GreetingRule : ClassifierRule {
    override val id = "greeting_text"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        if (TextAnalysis.greetingHits(text.fullText) == 0) return null
        val bonus = if (text.textCoverage >= config.largeTextCoverage) config.greetingLargeTextBonus else 0f
        return signal(FORWARD to config.greetingWeight + bonus)
    }
}

object ReceiptKeywordRule : ClassifierRule {
    override val id = "receipt_keywords"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        val hits = TextAnalysis.receiptHits(text.fullText)
        return when {
            hits >= config.receiptStrongMinHits -> signal(DOCUMENT to config.receiptStrongWeight)
            hits == 1 -> signal(DOCUMENT to config.receiptWeakWeight)
            else -> null
        }
    }
}

/** Dense text on a light, flat background: printed pages, notes, scans. Not when it looks like app UI. */
object DocumentLayoutRule : ClassifierRule {
    override val id = "document_layout"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        val visual = features.visual ?: return null
        if (TextAnalysis.uiChromeHits(text.fullText) >= config.uiChromeMinHits) return null
        val hit = text.textCoverage >= config.documentMinTextCoverage &&
            visual.meanBrightness >= config.documentMinBrightness &&
            visual.backgroundUniformity >= config.documentMinUniformity
        return if (hit) signal(DOCUMENT to config.documentLayoutWeight) else null
    }
}

object UiChromeRule : ClassifierRule {
    override val id = "ui_chrome_text"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        val hits = TextAnalysis.uiChromeHits(text.fullText)
        if (hits < config.uiChromeMinHits) return null
        val weight = (config.uiChromeWeight + (hits - config.uiChromeMinHits) * config.uiChromeExtraHitWeight)
            .coerceAtMost(config.uiChromeMaxWeight)
        return signal(SCREENSHOT to weight)
    }
}

object PhotoLabelRule : ClassifierRule {
    override val id = "photo_labels"
    override val source = SignalSource.ML
    val LABELS = setOf(
        "person", "selfie", "smile", "baby", "child", "crowd", "food", "pet", "dog", "cat", "bird",
        "sky", "cloud", "mountain", "beach", "sea", "lake", "river", "plant", "flower", "tree",
        "building", "city", "landscape", "sunset", "party", "wedding", "event", "vehicle", "car",
    )
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val labels = features.labels ?: return null
        val coverage = features.text?.textCoverage ?: 0f
        if (coverage > config.photoLabelMaxTextCoverage) return null
        val best = labels.filter { it.name.lowercase() in LABELS && it.confidence >= config.photoLabelMinConfidence }
            .maxByOrNull { it.confidence } ?: return null
        return signal(PHOTO to config.photoLabelWeight * best.confidence, detail = best.name)
    }
}

object HeavyTextRule : ClassifierRule {
    override val id = "heavy_text"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        return if (text.textCoverage >= config.heavyTextCoverage) signal(PHOTO to config.heavyTextPhotoPenalty) else null
    }
}

/** ML ran and found no text: strong evidence against memes, forwards and documents. */
object NoTextRule : ClassifierRule {
    override val id = "no_text"
    override val source = SignalSource.ML
    override fun evaluate(features: ImageFeatures, config: ClassifierConfig): Signal? {
        val text = features.text ?: return null
        if (text.lineCount > 0 || text.fullText.isNotBlank()) return null
        return signal(
            MEME to config.noTextMemePenalty,
            FORWARD to config.noTextForwardPenalty,
            DOCUMENT to config.noTextDocumentPenalty,
        )
    }
}

object DefaultRules {
    val FIRST_PASS: List<ClassifierRule> = listOf(
        ScreenshotFolderRule, ScreenshotFilenameRule, ScreenshotDimensionRule, PngMimeRule, NoCameraExifRule,
        ChatAppRule, CameraFolderRule, CameraExifRule, FocalLengthRule, GpsRule, DownloadsRule,
    )
    val ML_PASS: List<ClassifierRule> = listOf(
        MemeCaptionRule, MemeLabelRule, GreetingRule, ReceiptKeywordRule, DocumentLayoutRule, UiChromeRule,
        PhotoLabelRule, HeavyTextRule, NoTextRule,
    )
    val ALL: List<ClassifierRule> = FIRST_PASS + ML_PASS
}
