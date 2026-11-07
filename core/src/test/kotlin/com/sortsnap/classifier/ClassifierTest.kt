package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category.DOCUMENT
import com.sortsnap.classifier.Category.FORWARD
import com.sortsnap.classifier.Category.MEME
import com.sortsnap.classifier.Category.OTHER
import com.sortsnap.classifier.Category.PHOTO
import com.sortsnap.classifier.Category.SCREENSHOT
import com.sortsnap.classifier.Fixtures.labels
import com.sortsnap.classifier.Fixtures.text
import org.junit.Test

class ClassifierTest {
    private val classifier = SortSnapClassifier()

    @Test fun firstPass_cameraPhoto() {
        val r = classifier.firstPass(Fixtures.cameraPhoto)
        assertThat(r.category).isEqualTo(PHOTO)
        assertThat(r.signals).containsAtLeast("camera_folder", "camera_exif", "exif_gps")
        assertThat(r.usedMl).isFalse()
    }

    @Test fun firstPass_screenshotIsConfidentEnoughToSkipMl() {
        val r = classifier.firstPass(Fixtures.screenshot)
        assertThat(r.category).isEqualTo(SCREENSHOT)
        assertThat(r.confidence).isAtLeast(0.85f)
        assertThat(classifier.needsMlPass(r)).isFalse()
    }

    @Test fun firstPass_whatsappIsAmbiguousSoOther() {
        val r = classifier.firstPass(Fixtures.whatsappImage)
        assertThat(r.category).isEqualTo(OTHER)
        assertThat(classifier.needsMlPass(r)).isTrue()
    }

    @Test fun firstPass_unknownImageIsOther() {
        val r = classifier.firstPass(ImageFeatures(displayName = "image.jpg", mimeType = "image/jpeg"))
        assertThat(r.category).isEqualTo(OTHER)
        assertThat(r.confidence).isLessThan(0.55f)
        assertThat(r.bestGuess).isEqualTo(SCREENSHOT)
    }

    @Test fun firstPass_emptyFeaturesHaveZeroConfidence() {
        val r = SortSnapClassifier(firstPassRules = emptyList()).firstPass(ImageFeatures())
        assertThat(r.category).isEqualTo(OTHER)
        assertThat(r.confidence).isEqualTo(0f)
        assertThat(r.bestGuess).isEqualTo(OTHER)
    }

    @Test fun fallbackCategoryUsedWhenUnsure() {
        val r = classifier.firstPass(Fixtures.whatsappImage, fallback = PHOTO)
        assertThat(r.category).isEqualTo(PHOTO)
    }

    @Test fun ml_whatsappMemeWithCaptions() {
        val f = Fixtures.whatsappImage.copy(
            text = text("ME: I'LL SLEEP EARLY\nALSO ME AT 3AM", top = true, bottom = true, coverage = 0.12f),
            labels = labels("Cartoon" to 0.7f),
        )
        val r = classifier.classify(f)
        assertThat(r.category).isEqualTo(MEME)
        assertThat(r.usedMl).isTrue()
    }

    @Test fun ml_whatsappGoodMorningForward() {
        val f = Fixtures.whatsappImage.copy(
            text = text("Good Morning\nHave a blessed Sunday", coverage = 0.2f),
            labels = labels("Flower" to 0.8f),
        )
        assertThat(classifier.classify(f).category).isEqualTo(FORWARD)
    }

    @Test fun ml_receiptPhotoInCameraFolderBecomesDocument() {
        val f = Fixtures.cameraPhoto.copy(
            exif = ExifFeatures(make = "Google", model = "Pixel 9", hasFocalLength = true),
            text = text("SUPERMART\nItem Qty Amount\nSubtotal 840\nCGST 21 SGST 21\nTotal Rs. 882.00 12/10/2026", coverage = 0.35f),
            visual = VisualFeatures(0.8f, 0.6f),
            labels = labels("Paper" to 0.8f),
        )
        assertThat(classifier.classify(f).category).isEqualTo(DOCUMENT)
    }

    @Test fun ml_personPhotoStaysPhoto() {
        val f = Fixtures.cameraPhoto.copy(text = TextFeatures.NONE, labels = labels("Person" to 0.95f, "Smile" to 0.9f))
        val r = classifier.classify(f)
        assertThat(r.category).isEqualTo(PHOTO)
        assertThat(r.confidence).isGreaterThan(classifier.firstPass(Fixtures.cameraPhoto).confidence)
    }

    @Test fun classify_withoutMlFeaturesMatchesFirstPass() {
        val a = classifier.classify(Fixtures.cameraPhoto)
        val b = classifier.firstPass(Fixtures.cameraPhoto)
        assertThat(a.category).isEqualTo(b.category)
        assertThat(a.confidence).isEqualTo(b.confidence)
    }

    @Test fun folderPrior_tipsAmbiguousFolder() {
        val r = classifier.firstPass(Fixtures.whatsappImage, folderPrior = MEME)
        assertThat(r.category).isEqualTo(MEME)
        assertThat(r.signals).contains(SortSnapClassifier.FOLDER_PRIOR_ID)
        assertThat(r.evidence.first { it.id == SortSnapClassifier.FOLDER_PRIOR_ID }.detail).isEqualTo("WhatsApp Images")
    }

    @Test fun folderPrior_otherIsIgnored() {
        val r = classifier.firstPass(Fixtures.whatsappImage, folderPrior = OTHER)
        assertThat(r.signals).doesNotContain(SortSnapClassifier.FOLDER_PRIOR_ID)
    }

    @Test fun folderPrior_detailFallsBackToPath() {
        val r = classifier.firstPass(ImageFeatures(relativePath = "Pictures/Memes/"), folderPrior = MEME)
        assertThat(r.evidence.first { it.id == SortSnapClassifier.FOLDER_PRIOR_ID }.detail).isEqualTo("Memes")
    }

    @Test fun version_isExposed() {
        assertThat(classifier.version).isEqualTo(ClassifierConfig.VERSION)
    }
}
