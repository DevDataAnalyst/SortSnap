package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category.DOCUMENT
import com.sortsnap.classifier.Category.FORWARD
import com.sortsnap.classifier.Category.MEME
import com.sortsnap.classifier.Category.PHOTO
import com.sortsnap.classifier.Category.SCREENSHOT
import com.sortsnap.classifier.Fixtures.labels
import com.sortsnap.classifier.Fixtures.text
import org.junit.Test

/** ML rules tested with mocked ML Kit outputs (plain data, no ML Kit at runtime). */
class MlRulesTest {
    private val c = ClassifierConfig.DEFAULT

    @Test fun mlRules_doNotFireWithoutMlFeatures() {
        DefaultRules.ML_PASS.forEach { assertThat(it.evaluate(Fixtures.download, c)).isNull() }
    }

    @Test fun memeCaption_topAndBottom() {
        val f = ImageFeatures(text = text("WHEN YOU WAKE UP\nAND IT'S MONDAY", top = true, bottom = true))
        val s = MemeCaptionRule.evaluate(f, c)!!
        assertThat(s.id).isEqualTo(MemeCaptionRule.id)
        assertThat(s.scores[MEME]).isEqualTo(c.memeTopBottomCaptionWeight)
        assertThat(s.source).isEqualTo(SignalSource.ML)
    }

    @Test fun memeCaption_singleShortCaption() {
        val f = ImageFeatures(text = text("me after one gym session", top = true))
        assertThat(MemeCaptionRule.evaluate(f, c)!!.id).isEqualTo(MemeCaptionRule.SINGLE_ID)
    }

    @Test fun memeCaption_longTopTextIsNotACaption() {
        val f = ImageFeatures(text = text("a\nb\nc\nd\ne\nf", top = true))
        assertThat(MemeCaptionRule.evaluate(f, c)).isNull()
    }

    @Test fun memeCaption_noText() {
        assertThat(MemeCaptionRule.evaluate(ImageFeatures(text = TextFeatures.NONE), c)).isNull()
    }

    @Test fun memeLabels_cartoonScalesWithConfidence() {
        val f = ImageFeatures(labels = labels("Cartoon" to 0.5f, "Sky" to 0.9f))
        val s = MemeLabelRule.evaluate(f, c)!!
        assertThat(s.scores[MEME]).isWithin(1e-5f).of(c.memeLabelWeight * 0.5f)
        assertThat(s.detail).isEqualTo("Cartoon")
        assertThat(MemeLabelRule.evaluate(ImageFeatures(labels = labels("Sky" to 0.9f)), c)).isNull()
    }

    @Test fun greeting_goodMorning() {
        val f = ImageFeatures(text = text("Good Morning! Have a blessed day", coverage = 0.03f))
        assertThat(GreetingRule.evaluate(f, c)!!.scores[FORWARD]).isEqualTo(c.greetingWeight)
    }

    @Test fun greeting_largeTextGetsBonus() {
        val f = ImageFeatures(text = text("Happy Diwali", coverage = 0.2f))
        assertThat(GreetingRule.evaluate(f, c)!!.scores[FORWARD]).isEqualTo(c.greetingWeight + c.greetingLargeTextBonus)
    }

    @Test fun greeting_transliteratedBengaliAndHindi() {
        assertThat(GreetingRule.evaluate(ImageFeatures(text = text("Shubho Sokal")), c)).isNotNull()
        assertThat(GreetingRule.evaluate(ImageFeatures(text = text("Suprabhat")), c)).isNotNull()
    }

    @Test fun greeting_noGreetingWords() {
        assertThat(GreetingRule.evaluate(ImageFeatures(text = text("Meeting notes")), c)).isNull()
    }

    @Test fun receipt_strongWithTotalAndGst() {
        val f = ImageFeatures(text = text("Invoice #123\nSubtotal 450\nGST 18%\nTotal ₹531.00"))
        assertThat(ReceiptKeywordRule.evaluate(f, c)!!.scores[DOCUMENT]).isEqualTo(c.receiptStrongWeight)
    }

    @Test fun receipt_weakWithSingleKeyword() {
        val f = ImageFeatures(text = text("Payment"))
        assertThat(ReceiptKeywordRule.evaluate(f, c)!!.scores[DOCUMENT]).isEqualTo(c.receiptWeakWeight)
    }

    @Test fun receipt_none() {
        assertThat(ReceiptKeywordRule.evaluate(ImageFeatures(text = text("hello there")), c)).isNull()
    }

    @Test fun documentLayout_denseTextOnLightUniformBackground() {
        val f = ImageFeatures(text = text("lots", coverage = 0.4f), visual = VisualFeatures(0.85f, 0.7f))
        assertThat(DocumentLayoutRule.evaluate(f, c)).isNotNull()
    }

    @Test fun documentLayout_darkBackgroundDoesNotFire() {
        val f = ImageFeatures(text = text("lots", coverage = 0.4f), visual = VisualFeatures(0.2f, 0.7f))
        assertThat(DocumentLayoutRule.evaluate(f, c)).isNull()
        assertThat(DocumentLayoutRule.evaluate(f.copy(visual = null), c)).isNull()
    }

    @Test fun uiChrome_statusBarAndButtons() {
        val f = ImageFeatures(text = text("10:42 85%\nReply  Like  Share"))
        assertThat(UiChromeRule.evaluate(f, c)!!.scores[SCREENSHOT]).isAtLeast(c.uiChromeWeight)
    }

    @Test fun uiChrome_singleHitIsNotEnough() {
        assertThat(UiChromeRule.evaluate(ImageFeatures(text = text("Reply")), c)).isNull()
    }

    @Test fun photoLabels_personWithLittleText() {
        val f = ImageFeatures(labels = labels("Person" to 0.92f, "Smile" to 0.8f), text = text("", lines = 0, coverage = 0f))
        val s = PhotoLabelRule.evaluate(f, c)!!
        assertThat(s.detail).isEqualTo("Person")
        assertThat(s.scores[PHOTO]).isWithin(1e-5f).of(c.photoLabelWeight * 0.92f)
    }

    @Test fun photoLabels_suppressedByText() {
        val f = ImageFeatures(labels = labels("Person" to 0.92f), text = text("GOOD MORNING", coverage = 0.3f))
        assertThat(PhotoLabelRule.evaluate(f, c)).isNull()
    }

    @Test fun photoLabels_lowConfidenceIgnored() {
        assertThat(PhotoLabelRule.evaluate(ImageFeatures(labels = labels("Dog" to 0.4f)), c)).isNull()
    }

    @Test fun heavyText_penalisesPhoto() {
        val f = ImageFeatures(text = text("x", coverage = 0.5f))
        assertThat(HeavyTextRule.evaluate(f, c)!!.scores[PHOTO]).isLessThan(0f)
        assertThat(HeavyTextRule.evaluate(ImageFeatures(text = text("x", coverage = 0.01f)), c)).isNull()
    }
}

class MlRulesInteractionTest {
    private val c = ClassifierConfig.DEFAULT

    @Test fun noText_penalisesTextCategories() {
        val s = NoTextRule.evaluate(ImageFeatures(text = TextFeatures.NONE), c)!!
        assertThat(s.scores[Category.MEME]).isLessThan(0f)
        assertThat(s.scores[Category.FORWARD]).isLessThan(0f)
        assertThat(s.scores[Category.DOCUMENT]).isLessThan(0f)
        assertThat(NoTextRule.evaluate(ImageFeatures(text = Fixtures.text("hi")), c)).isNull()
        assertThat(NoTextRule.evaluate(ImageFeatures(), c)).isNull()
    }

    @Test fun uiChrome_weightGrowsWithHitsAndIsCapped() {
        val few = UiChromeRule.evaluate(ImageFeatures(text = Fixtures.text("Reply Like")), c)!!
        val many = UiChromeRule.evaluate(
            ImageFeatures(text = Fixtures.text("10:42 85% Reply Like Share Comment Follow Send Search Home")), c,
        )!!
        assertThat(few.scores[Category.SCREENSHOT]).isEqualTo(c.uiChromeWeight)
        assertThat(many.scores[Category.SCREENSHOT]).isEqualTo(c.uiChromeMaxWeight)
    }

    @Test fun documentLayout_suppressedForAppUi() {
        val f = ImageFeatures(text = Fixtures.text("10:42 Reply Like", coverage = 0.4f), visual = VisualFeatures(0.9f, 0.8f))
        assertThat(DocumentLayoutRule.evaluate(f, c)).isNull()
    }

    @Test fun noExif_skippedInChatFolders() {
        assertThat(NoCameraExifRule.evaluate(Fixtures.whatsappImage, c)).isNull()
    }
}
