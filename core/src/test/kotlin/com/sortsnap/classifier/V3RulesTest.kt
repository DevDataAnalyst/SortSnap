package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category.DOCUMENT
import com.sortsnap.classifier.Category.MEME
import com.sortsnap.classifier.Category.PHOTO
import com.sortsnap.classifier.Category.SCREENSHOT
import com.sortsnap.classifier.Fixtures.PIXEL_SCREEN
import com.sortsnap.classifier.Fixtures.labels
import com.sortsnap.classifier.Fixtures.text
import org.junit.Test

/** Classifier v3 rules: layout, colour and deeper text understanding. */
class V3RulesTest {
    private val c = ClassifierConfig.DEFAULT

    // ---- LongScreenshotRule ----

    @Test fun longScreenshot_screenWidthAndMuchTaller() {
        val f = ImageFeatures(width = 1080, height = 6200, screen = PIXEL_SCREEN)
        val s = LongScreenshotRule.evaluate(f, c)!!
        assertThat(s.id).isEqualTo(LongScreenshotRule.id)
        assertThat(s.scores[SCREENSHOT]).isEqualTo(c.longScreenshotWeight)
    }

    @Test fun longScreenshot_normalScreenshotIsNotLong() {
        assertThat(LongScreenshotRule.evaluate(Fixtures.screenshot, c)).isNull()
    }

    @Test fun longScreenshot_tallCaptureFromAnotherPhone() {
        val f = ImageFeatures(width = 1170, height = 4800, screen = PIXEL_SCREEN)
        val s = LongScreenshotRule.evaluate(f, c)!!
        assertThat(s.id).isEqualTo(LongScreenshotRule.TALL_ID)
        assertThat(s.scores[SCREENSHOT]).isEqualTo(c.tallAspectScreenshotWeight)
        assertThat(s.scores[DOCUMENT]).isEqualTo(c.tallAspectDocumentWeight)
    }

    @Test fun longScreenshot_tallCameraPanoramaIsIgnored() {
        val f = ImageFeatures(width = 1200, height = 4800, exif = ExifFeatures(make = "Google"))
        assertThat(LongScreenshotRule.evaluate(f, c)).isNull()
    }

    @Test fun longScreenshot_landscapeOrUnknownSizeIgnored() {
        assertThat(LongScreenshotRule.evaluate(ImageFeatures(width = 4000, height = 1000), c)).isNull()
        assertThat(LongScreenshotRule.evaluate(ImageFeatures(), c)).isNull()
        assertThat(LongScreenshotRule.evaluate(ImageFeatures(width = 1000, height = 1500), c)).isNull()
    }

    // ---- FlatGraphicRule ----

    @Test fun flatGraphic_penalisesPhotoWithoutExif() {
        val f = Fixtures.download.copy(visual = VisualFeatures(0.9f, 0.7f))
        assertThat(FlatGraphicRule.evaluate(f, c)!!.scores[PHOTO]).isEqualTo(c.flatGraphicPhotoPenalty)
    }

    @Test fun flatGraphic_notForCameraPhotosOrBusyImages() {
        assertThat(FlatGraphicRule.evaluate(Fixtures.cameraPhoto.copy(visual = VisualFeatures(0.9f, 0.9f)), c)).isNull()
        assertThat(FlatGraphicRule.evaluate(Fixtures.download.copy(visual = VisualFeatures(0.5f, 0.2f)), c)).isNull()
        assertThat(FlatGraphicRule.evaluate(Fixtures.download, c)).isNull()
        val cameraFolderNoExif = ImageFeatures(relativePath = "DCIM/Camera/", visual = VisualFeatures(0.9f, 0.9f))
        assertThat(FlatGraphicRule.evaluate(cameraFolderNoExif, c)).isNull()
    }

    // ---- DocumentKeywordRule ----

    @Test fun documentKeywords_strongAndWeak() {
        val strong = ImageFeatures(text = text("Meeting notes\nAgenda"))
        assertThat(DocumentKeywordRule.evaluate(strong, c)!!.scores[DOCUMENT]).isEqualTo(c.documentKeywordStrongWeight)
        val weak = ImageFeatures(text = text("Boarding pass"))
        assertThat(DocumentKeywordRule.evaluate(weak, c)!!.scores[DOCUMENT]).isEqualTo(c.documentKeywordWeakWeight)
        assertThat(DocumentKeywordRule.evaluate(ImageFeatures(text = text("hello there")), c)).isNull()
        assertThat(DocumentKeywordRule.evaluate(ImageFeatures(), c)).isNull()
    }

    // ---- ListLayoutRule ----

    @Test fun listLayout_bulletsNumbersAndCheckboxes() {
        val f = ImageFeatures(text = text("Shopping\n- milk\n• eggs\n1. bread\n[x] rice"))
        val s = ListLayoutRule.evaluate(f, c)!!
        assertThat(s.scores[DOCUMENT]).isEqualTo(c.listLayoutWeight)
        assertThat(s.detail).isEqualTo("4")
        assertThat(ListLayoutRule.evaluate(ImageFeatures(text = text("- one\n- two")), c)).isNull()
        assertThat(ListLayoutRule.evaluate(ImageFeatures(), c)).isNull()
    }

    // ---- DocumentLabelRule ----

    @Test fun documentLabels_scaledByConfidence() {
        val f = ImageFeatures(labels = labels("Font" to 0.9f, "Paper" to 0.5f, "Receipt" to 0.8f))
        val s = DocumentLabelRule.evaluate(f, c)!!
        assertThat(s.scores[DOCUMENT]).isWithin(1e-6f).of(c.documentLabelWeight * 0.8f)
        assertThat(s.detail).isEqualTo("Receipt")
        assertThat(DocumentLabelRule.evaluate(ImageFeatures(labels = labels("Font" to 0.9f)), c)).isNull()
        assertThat(DocumentLabelRule.evaluate(ImageFeatures(), c)).isNull()
    }

    // ---- MemePhraseRule ----

    @Test fun memePhrases() {
        val f = ImageFeatures(text = text("Mondays be like"))
        assertThat(MemePhraseRule.evaluate(f, c)!!.scores[MEME]).isEqualTo(c.memePhraseWeight)
        assertThat(MemePhraseRule.evaluate(ImageFeatures(text = text("Good morning")), c)).isNull()
        assertThat(MemePhraseRule.evaluate(ImageFeatures(), c)).isNull()
    }

    @Test fun memePhrases_notInChatScreenshots() {
        val f = ImageFeatures(text = text("lol\nType a message\nlast seen today 10:42"))
        assertThat(MemePhraseRule.evaluate(f, c)).isNull()
    }

    // ---- SocialWatermarkRule ----

    @Test fun socialWatermark_handleOrSite() {
        assertThat(SocialWatermarkRule.evaluate(ImageFeatures(text = text("so true @daily.memes")), c)!!.scores[MEME])
            .isEqualTo(c.socialWatermarkWeight)
        assertThat(SocialWatermarkRule.evaluate(ImageFeatures(text = text("via 9GAG")), c)).isNotNull()
        assertThat(SocialWatermarkRule.evaluate(ImageFeatures(text = text("mail me at a@b")), c)).isNull()
        assertThat(SocialWatermarkRule.evaluate(ImageFeatures(), c)).isNull()
    }

    // ---- GrayscaleDocumentRule ----

    @Test fun grayscaleText_scanLikePage() {
        val f = ImageFeatures(
            visual = VisualFeatures(0.8f, 0.6f, colorfulness = 0.03f),
            text = text("Para one\nPara two\nPara three"),
        )
        assertThat(GrayscaleDocumentRule.evaluate(f, c)!!.scores[DOCUMENT]).isEqualTo(c.grayscaleDocumentWeight)
    }

    @Test fun grayscaleText_needsColourMeasuredLowAndEnoughLines() {
        val lines = text("a\nb\nc")
        assertThat(GrayscaleDocumentRule.evaluate(ImageFeatures(visual = VisualFeatures(0.8f, 0.6f), text = lines), c)).isNull()
        assertThat(GrayscaleDocumentRule.evaluate(ImageFeatures(visual = VisualFeatures(0.8f, 0.6f, 0.4f), text = lines), c)).isNull()
        assertThat(GrayscaleDocumentRule.evaluate(ImageFeatures(visual = VisualFeatures(0.8f, 0.6f, 0.02f), text = text("a")), c)).isNull()
        assertThat(GrayscaleDocumentRule.evaluate(ImageFeatures(text = lines), c)).isNull()
        assertThat(GrayscaleDocumentRule.evaluate(ImageFeatures(visual = VisualFeatures(0.8f, 0.6f, 0.02f)), c)).isNull()
        val ui = text("Search\nHome\nSettings 10:42")
        assertThat(GrayscaleDocumentRule.evaluate(ImageFeatures(visual = VisualFeatures(0.8f, 0.6f, 0.02f), text = ui), c)).isNull()
    }

    // ---- Text analysis helpers ----

    @Test fun textHelpers() {
        assertThat(TextAnalysis.documentHits("Date of birth: 01/01/1990\nAddress: X")).isEqualTo(2)
        assertThat(TextAnalysis.memePhraseHits("How it started / how it's going")).isEqualTo(2)
        assertThat(TextAnalysis.socialWatermarkHits("@funny_page on Instagram")).isEqualTo(2)
        assertThat(TextAnalysis.listLines("1) a\n2) b\nplain")).isEqualTo(2)
        assertThat(TextAnalysis.greetingHits("Radhe Radhe")).isEqualTo(1)
    }

    // ---- Colourfulness ----

    @Test fun colorfulness_greyIsZeroVividIsHigh() {
        val grey = IntArray(100) { val v = it * 2; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        assertThat(ImageMath.colorfulness(grey)).isWithin(1e-6f).of(0f)
        val vivid = IntArray(100) { if (it % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        assertThat(ImageMath.colorfulness(vivid)).isGreaterThan(0.5f)
        assertThat(ImageMath.colorfulness(IntArray(0))).isEqualTo(0f)
    }

    @Test fun visualFeatures_withAndWithoutColour() {
        val gray = IntArray(16) { 200 }
        val argb = IntArray(16) { 0xFFC8C8C8.toInt() }
        assertThat(ImageMath.visualFeatures(gray, argb).hasColorfulness).isTrue()
        assertThat(ImageMath.visualFeatures(gray, null).hasColorfulness).isFalse()
        assertThat(ImageMath.visualFeatures(gray, IntArray(0)).colorfulness).isEqualTo(VisualFeatures.UNKNOWN)
    }

    // ---- End to end ----

    @Test fun meetingNotesPhotographedWithCameraBecomeDocument() {
        val f = Fixtures.cameraPhoto.copy(
            visual = VisualFeatures(0.8f, 0.6f, colorfulness = 0.05f),
            text = text("Meeting notes\n- launch plan\n- budget review\n- hiring", coverage = 0.3f),
            labels = labels("Paper" to 0.8f, "Font" to 0.7f),
        )
        assertThat(SortSnapClassifier().classify(f).category).isEqualTo(DOCUMENT)
    }

    @Test fun scrollingScreenshotWithoutFolderHintsIsScreenshot() {
        val f = ImageFeatures(
            relativePath = "Pictures/", bucket = "Pictures", displayName = "IMG_1.jpg", mimeType = "image/jpeg",
            width = 1080, height = 7000, screen = PIXEL_SCREEN,
        )
        assertThat(SortSnapClassifier().firstPass(f).bestGuess).isEqualTo(SCREENSHOT)
    }
}
