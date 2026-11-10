package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreFusionTest {
    private val c = ClassifierConfig.DEFAULT

    @Test fun negativeTotalsClampToZero() {
        val totals = ScoreFusion.rawTotals(listOf(Signal("x", mapOf(Category.PHOTO to -2f))), c)
        assertThat(totals[Category.PHOTO]).isEqualTo(0f)
    }

    @Test fun mlWeightScalesMlSignals() {
        val cfg = c.copy(mlWeight = 0.5f)
        val totals = ScoreFusion.rawTotals(listOf(Signal("m", mapOf(Category.MEME to 2f), SignalSource.ML)), cfg)
        assertThat(totals[Category.MEME]).isEqualTo(1f)
    }

    @Test fun ruleWeightScalesRuleSignalsButNotPriors() {
        val cfg = c.copy(ruleWeight = 2f)
        val totals = ScoreFusion.rawTotals(
            listOf(Signal("r", mapOf(Category.PHOTO to 1f)), Signal("p", mapOf(Category.MEME to 1f), SignalSource.PRIOR)),
            cfg,
        )
        assertThat(totals[Category.PHOTO]).isEqualTo(2f)
        assertThat(totals[Category.MEME]).isEqualTo(1f)
    }

    @Test fun otherContributionsIgnored() {
        val totals = ScoreFusion.rawTotals(listOf(Signal("o", mapOf(Category.OTHER to 5f))), c)
        assertThat(totals.containsKey(Category.OTHER)).isFalse()
    }

    @Test fun normaliseUsesSmoothing() {
        val n = ScoreFusion.normalise(mapOf(Category.PHOTO to 3f, Category.MEME to 0f), c)
        assertThat(n[Category.PHOTO]).isWithin(1e-5f).of(0.75f)
    }

    @Test fun normaliseAllZero() {
        assertThat(ScoreFusion.normalise(mapOf(Category.PHOTO to 0f), c)[Category.PHOTO]).isEqualTo(0f)
    }

    @Test fun belowThresholdReturnsFallbackButKeepsConfidence() {
        val r = ScoreFusion.decide(listOf(Signal("a", mapOf(Category.MEME to 1f))), c)
        assertThat(r.category).isEqualTo(Category.OTHER)
        assertThat(r.confidence).isWithin(1e-5f).of(0.5f) // 1 / (1 + 0 + 1)
        assertThat(r.bestGuess).isEqualTo(Category.MEME)
    }

    @Test fun aboveThresholdPicksTop() {
        val r = ScoreFusion.decide(listOf(Signal("a", mapOf(Category.MEME to 4f))), c)
        assertThat(r.category).isEqualTo(Category.MEME)
        assertThat(r.confidence).isWithin(1e-5f).of(0.8f) // 4 / (4 + 0 + 1)
    }

    @Test fun confidenceIsMarginAgainstRunnerUp() {
        val signals = listOf(
            Signal("a", mapOf(Category.MEME to 3f)),
            Signal("b", mapOf(Category.FORWARD to 1f)),
            Signal("c", mapOf(Category.DOCUMENT to 1f)),
        )
        val r = ScoreFusion.decide(signals, c)
        assertThat(r.confidence).isWithin(1e-5f).of(0.6f) // 3 / (3 + 1 + 1); DOCUMENT does not dilute
        assertThat(r.scores[Category.MEME]).isWithin(1e-5f).of(0.5f) // share: 3 / (5 + 1)
    }
}

class SignalCodecTest {
    @Test fun roundTrip() {
        val signals = listOf(
            Signal("chat_app_folder", mapOf(Category.MEME to 1f, Category.PHOTO to -0.5f), detail = "WhatsApp"),
            Signal("greeting_text", mapOf(Category.FORWARD to 2.5f), SignalSource.ML),
        )
        assertThat(SignalCodec.decode(SignalCodec.encode(signals))).isEqualTo(signals)
    }

    @Test fun garbageDecodesToEmpty() {
        assertThat(SignalCodec.decode("{not json")).isEmpty()
        assertThat(SignalCodec.decode(null)).isEmpty()
        assertThat(SignalCodec.decode("")).isEmpty()
    }
}

class SignalExplainerTest {
    @Test fun everyBuiltInSignalHasPlainEnglish() {
        val ids = DefaultRules.ALL.map { it.id } + listOf(
            ScreenshotDimensionRule.DEVICE_ID, ScreenshotDimensionRule.COMMON_ID, MemeCaptionRule.SINGLE_ID,
            SortSnapClassifier.FOLDER_PRIOR_ID, LongScreenshotRule.TALL_ID, PersonalModel.SIGNAL_ID,
        )
        ids.filter { it != ScreenshotDimensionRule.id }.forEach { id ->
            val text = SignalExplainer.explain(Signal(id, emptyMap()))
            assertThat(text).doesNotContain("_")
            assertThat(text).isNotEmpty()
        }
    }

    @Test fun detailsAreUsed() {
        assertThat(SignalExplainer.explain(Signal(ChatAppRule.id, emptyMap(), detail = "WhatsApp")))
            .isEqualTo("Saved in WhatsApp folder")
        assertThat(SignalExplainer.explain(Signal(MemeCaptionRule.id, emptyMap()))).isEqualTo("Text at top and bottom")
        assertThat(SignalExplainer.explain(Signal(CameraExifRule.id, emptyMap(), detail = "Google Pixel")))
            .contains("Google Pixel")
        assertThat(SignalExplainer.explain(Signal(MemeLabelRule.id, emptyMap(), detail = "Cartoon"))).contains("a cartoon")
        assertThat(SignalExplainer.explain(Signal(PhotoLabelRule.id, emptyMap(), detail = "Dog"))).contains("dog")
        assertThat(SignalExplainer.explain(Signal(DownloadsRule.id, emptyMap(), detail = "Reddit"))).contains("Reddit")
    }

    @Test fun unknownIdIsHumanised() {
        assertThat(SignalExplainer.explain(Signal("some_new_rule", emptyMap()))).isEqualTo("Some new rule")
    }

    @Test fun listKeepsOnlySupportingEvidenceStrongestFirst() {
        val signals = listOf(
            Signal(PngMimeRule.id, mapOf(Category.SCREENSHOT to 0.5f)),
            Signal(ScreenshotFolderRule.id, mapOf(Category.SCREENSHOT to 3f)),
            Signal(NoCameraExifRule.id, mapOf(Category.PHOTO to -0.8f, Category.SCREENSHOT to 0.4f)),
            Signal(ChatAppRule.id, mapOf(Category.MEME to 1f)),
        )
        val why = SignalExplainer.explain(signals, Category.SCREENSHOT)
        assertThat(why).containsExactly(
            "Saved in a Screenshots folder", "Saved as PNG, typical for screenshots", "No camera information in the file",
        ).inOrder()
    }
}

class TextAnalysisTest {
    @Test fun keywordsAreWholeWords() {
        assertThat(TextAnalysis.countKeywords("totally fine", listOf("total"))).isEqualTo(0)
        assertThat(TextAnalysis.countKeywords("Grand TOTAL: 50", listOf("total"))).isEqualTo(1)
        assertThat(TextAnalysis.countKeywords("   ", listOf("total"))).isEqualTo(0)
    }

    @Test fun receiptPatterns() {
        assertThat(TextAnalysis.receiptHits("₹1,250.00")).isEqualTo(1)
        assertThat(TextAnalysis.receiptHits("12/10/2026")).isEqualTo(1)
        assertThat(TextAnalysis.receiptHits("Rs. 99 on 01-02-26 total")).isEqualTo(3)
    }

    @Test fun uiChromePatterns() {
        assertThat(TextAnalysis.uiChromeHits("9:41")).isEqualTo(1)
        assertThat(TextAnalysis.uiChromeHits("100%")).isEqualTo(1)
        assertThat(TextAnalysis.uiChromeHits("last seen today")).isEqualTo(1)
    }

    @Test fun greetings() {
        assertThat(TextAnalysis.greetingHits("GOOD NIGHT sweet dreams")).isAtLeast(2)
        assertThat(TextAnalysis.greetingHits("quarterly report")).isEqualTo(0)
    }
}

class ImageMathTest {
    private fun gradient(w: Int, h: Int, reverse: Boolean = false) =
        IntArray(w * h) { i -> val x = i % w; if (reverse) 255 - x * 255 / (w - 1) else x * 255 / (w - 1) }

    @Test fun resizeAverages() {
        val out = ImageMath.resize(intArrayOf(0, 100, 200, 100), 2, 2, 1, 1)
        assertThat(out).asList().containsExactly(100)
    }

    @Test fun dHashStableUnderScaling() {
        val big = ImageMath.dHash(gradient(90, 80), 90, 80)
        val small = ImageMath.dHash(gradient(45, 40), 45, 40)
        assertThat(ImageMath.hamming(big, small)).isAtMost(2)
    }

    @Test fun dHashDiffersForDifferentImages() {
        val a = ImageMath.dHash(gradient(90, 80), 90, 80)
        val b = ImageMath.dHash(gradient(90, 80, reverse = true), 90, 80)
        assertThat(ImageMath.hamming(a, b)).isGreaterThan(40)
    }

    @Test fun hamming() {
        assertThat(ImageMath.hamming(0b1011L, 0b0001L)).isEqualTo(2)
        assertThat(ImageMath.hamming(-1L, 0L)).isEqualTo(64)
    }

    @Test fun laplacianVarianceLowForFlatHighForEdges() {
        val flat = IntArray(32 * 32) { 128 }
        val checker = IntArray(32 * 32) { i -> if ((i % 32 + i / 32) % 2 == 0) 0 else 255 }
        assertThat(ImageMath.laplacianVariance(flat, 32, 32)).isEqualTo(0.0)
        assertThat(ImageMath.laplacianVariance(checker, 32, 32)).isGreaterThan(1000.0)
        assertThat(ImageMath.laplacianVariance(IntArray(4), 2, 2)).isEqualTo(0.0)
    }

    @Test fun visualFeatures() {
        val white = ImageMath.visualFeatures(IntArray(100) { 255 })
        assertThat(white.meanBrightness).isWithin(1e-4f).of(1f)
        assertThat(white.backgroundUniformity).isEqualTo(1f)
        assertThat(ImageMath.visualFeatures(IntArray(0)).meanBrightness).isEqualTo(0f)
    }

    @Test fun luma() {
        assertThat(ImageMath.luma(0xFFFFFFFF.toInt())).isEqualTo(255)
        assertThat(ImageMath.luma(0xFF000000.toInt())).isEqualTo(0)
    }
}

class CategoryTest {
    @Test fun clutterExcludesPhotoAndOther() {
        assertThat(Category.OTHER.isClutter).isFalse()
        assertThat(Category.PHOTO.isClutter).isFalse()
        assertThat(Category.MEME.isClutter).isTrue()
        assertThat(Category.fromNameOrNull("MEME")).isEqualTo(Category.MEME)
        assertThat(Category.fromNameOrNull("nope")).isNull()
        assertThat(Category.fromNameOrNull(null)).isNull()
    }

    @Test fun featureHelpers() {
        assertThat(ExifFeatures().isEmpty).isTrue()
        assertThat(ExifFeatures(model = "X").hasCameraMakeModel).isTrue()
        assertThat(ScreenSize(1080, 2400).matches(2400, 1080)).isTrue()
        assertThat(ScreenSize(1080, 2400).matches(1080, 1920)).isFalse()
        assertThat(ImageFeatures().hasMlFeatures).isFalse()
        assertThat(ImageFeatures(labels = emptyList()).hasMlFeatures).isTrue()
    }
}
