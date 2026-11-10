package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category.FORWARD
import com.sortsnap.classifier.Category.MEME
import com.sortsnap.classifier.Category.OTHER
import com.sortsnap.classifier.Category.PHOTO
import org.junit.Test

class PersonalModelTest {
    private val c = ClassifierConfig.DEFAULT
    private val classifier = SortSnapClassifier()

    /** The evidence a Telegram image with a short top caption produces. */
    private val telegramCaption = ImageFeatures(
        relativePath = "Pictures/Telegram/", bucket = "Telegram", displayName = "photo_1.jpg",
        mimeType = "image/jpeg", width = 1080, height = 1080,
        text = Fixtures.text("Stay strong", top = true), labels = emptyList(),
    )

    private fun evidence(f: ImageFeatures) = classifier.classify(f).evidence

    private fun examples(f: ImageFeatures, category: Category, n: Int) = List(n) { LabelledExample(evidence(f), category) }

    @Test fun emptyModelSaysNothing() {
        assertThat(PersonalModel.EMPTY.isEmpty).isTrue()
        assertThat(PersonalModel.EMPTY.vote(evidence(telegramCaption), c)).isNull()
        assertThat(PersonalModel.build(emptyList()).isEmpty).isTrue()
    }

    @Test fun learnsFromConsistentCorrections() {
        val before = classifier.classify(telegramCaption)
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, 4))
        assertThat(model.exampleCount).isEqualTo(4)
        assertThat(model.patternCount).isEqualTo(1)

        val vote = model.vote(evidence(telegramCaption), c)!!
        assertThat(vote.category).isEqualTo(FORWARD)
        assertThat(vote.agreement).isWithin(1e-6f).of(1f)
        assertThat(vote.support).isEqualTo(4)

        val after = classifier.classify(telegramCaption, personal = model)
        assertThat(after.category).isEqualTo(FORWARD)
        assertThat(after.signals).contains(PersonalModel.SIGNAL_ID)
        assertThat(before.category).isNotEqualTo(FORWARD)
        assertThat(SignalExplainer.explain(after.evidence, FORWARD)).contains("Similar to 4 images you sorted yourself")
    }

    @Test fun needsEnoughSupport() {
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, c.personalMinSupport - 1))
        assertThat(model.vote(evidence(telegramCaption), c)).isNull()
    }

    @Test fun needsClearAgreement() {
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, 3) + examples(telegramCaption, MEME, 3))
        assertThat(model.vote(evidence(telegramCaption), c)).isNull()
    }

    @Test fun otherAnswersAndEmptyEvidenceAreIgnored() {
        val model = PersonalModel.build(
            examples(telegramCaption, OTHER, 5) + List(5) { LabelledExample(emptyList(), FORWARD) },
        )
        assertThat(model.isEmpty).isTrue()
    }

    @Test fun dissimilarImagesAreNotAffected() {
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, 10))
        assertThat(model.vote(evidence(Fixtures.cameraPhoto), c)).isNull()
        assertThat(classifier.classify(Fixtures.cameraPhoto, personal = model).category).isEqualTo(PHOTO)
    }

    @Test fun strongEvidenceStillWins() {
        // Even if the user once filed camera photos elsewhere, EXIF + camera folder keep them photos:
        // personal learning only settles close calls.
        val model = PersonalModel.build(examples(Fixtures.cameraPhoto, MEME, 10))
        assertThat(model.vote(classifier.firstPass(Fixtures.cameraPhoto).evidence, c)?.category).isEqualTo(MEME)
        val r = classifier.firstPass(Fixtures.cameraPhoto, personal = model)
        assertThat(r.category).isEqualTo(PHOTO)
        assertThat(r.signals).doesNotContain(PersonalModel.SIGNAL_ID)
    }

    @Test fun chatAppDetailMatters() {
        val whatsapp = Fixtures.whatsappImage
        val telegram = whatsapp.copy(relativePath = "Pictures/Telegram/", bucket = "Telegram")
        val model = PersonalModel.build(examples(whatsapp, FORWARD, 5))
        assertThat(model.vote(classifier.firstPass(whatsapp).evidence, c)?.category).isEqualTo(FORWARD)
        assertThat(model.vote(classifier.firstPass(telegram).evidence, c)).isNull()
    }

    @Test fun firstPassComparesMetadataEvidenceOnly() {
        // Examples were sorted after the ML pass; a new image before ML still matches on metadata.
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, 5))
        val firstPass = classifier.firstPass(telegramCaption.copy(text = null, labels = null))
        assertThat(model.vote(firstPass.evidence, c)?.category).isEqualTo(FORWARD)
    }

    @Test fun priorAndPersonalSignalsAreNotPartOfTheFingerprint() {
        val signals = listOf(
            Signal("a", emptyMap()),
            Signal("b", emptyMap(), SignalSource.ML),
            Signal("p", emptyMap(), SignalSource.PRIOR),
            Signal(PersonalModel.SIGNAL_ID, emptyMap(), SignalSource.PERSONAL),
        )
        assertThat(PersonalModel.fingerprint(signals)).containsExactly("a", "b")
        assertThat(PersonalModel.EMPTY.vote(emptyList(), c)).isNull()
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, 5))
        assertThat(model.vote(listOf(Signal("p", emptyMap(), SignalSource.PRIOR)), c)).isNull()
    }

    @Test fun jaccard() {
        assertThat(PersonalModel.jaccard(setOf("a", "b"), setOf("b", "c"))).isWithin(1e-6f).of(1f / 3)
        assertThat(PersonalModel.jaccard(emptySet(), emptySet())).isEqualTo(1f)
        assertThat(PersonalModel.jaccard(setOf("a"), emptySet())).isEqualTo(0f)
    }

    @Test fun respectsMaxExamples() {
        val model = PersonalModel.build(examples(telegramCaption, FORWARD, 20), c.copy(personalMaxExamples = 7))
        assertThat(model.exampleCount).isEqualTo(7)
    }

    @Test fun personalSourceIsFusedAtFullWeight() {
        val totals = ScoreFusion.rawTotals(
            listOf(Signal(PersonalModel.SIGNAL_ID, mapOf(FORWARD to 1.5f), SignalSource.PERSONAL)),
            c.copy(ruleWeight = 0f, mlWeight = 0f),
        )
        assertThat(totals[FORWARD]).isEqualTo(1.5f)
    }
}
