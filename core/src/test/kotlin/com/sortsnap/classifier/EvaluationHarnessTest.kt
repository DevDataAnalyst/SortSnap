package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Labelled evaluation harness. Reads `eval/fixtures.csv` (precomputed features + true labels),
 * classifies every row and prints a confusion matrix with per-category precision/recall.
 *
 * Run: ./gradlew :core:test --tests '*EvaluationHarnessTest*'
 */
class EvaluationHarnessTest {

    private fun loadFixtures(): List<LabeledFeatures> {
        val stream = javaClass.classLoader.getResourceAsStream("eval/fixtures.csv")
            ?: error("eval/fixtures.csv missing from test resources")
        return FixtureCsv.parse(stream.bufferedReader().readText())
    }

    @Test fun printConfusionMatrix() {
        val fixtures = loadFixtures()
        assertThat(fixtures.size).isAtLeast(60)
        val classifier = SortSnapClassifier()
        val matrix = ConfusionMatrix.evaluate(fixtures) { classifier.classify(it) }
        println("\n==== SortSnap classifier evaluation (v${ClassifierConfig.VERSION}) ====")
        println(matrix.render())
        val misses = fixtures.filter { classifier.classify(it.features).category != it.label }
        if (misses.isNotEmpty()) {
            println("\nMisclassified:")
            misses.forEach {
                val r = classifier.classify(it.features)
                println("  ${it.id}: expected ${it.label}, got ${r.category} (%.2f) signals=${r.signals}".format(r.confidence))
            }
        }
        // Guard rail so weight tweaks can't silently tank accuracy.
        assertThat(matrix.accuracy).isAtLeast(0.8)
    }

    @Test fun firstPassOnlyBaseline() {
        val fixtures = loadFixtures()
        val classifier = SortSnapClassifier()
        val matrix = ConfusionMatrix.evaluate(fixtures) { classifier.firstPass(it) }
        println("\n==== First pass only (no ML) ====")
        println(matrix.render())
        // Without ML many chat-app images are legitimately OTHER; it must never be worse than chance.
        assertThat(matrix.accuracy).isAtLeast(0.3)
    }
}

class ConfusionMatrixTest {
    @Test fun precisionRecallAccuracy() {
        val m = ConfusionMatrix()
        m.add(Category.PHOTO, Category.PHOTO)
        m.add(Category.PHOTO, Category.MEME)
        m.add(Category.MEME, Category.MEME)
        assertThat(m.total).isEqualTo(3)
        assertThat(m.count(Category.PHOTO, Category.MEME)).isEqualTo(1)
        assertThat(m.accuracy).isWithin(1e-9).of(2.0 / 3)
        assertThat(m.precision(Category.MEME)).isWithin(1e-9).of(0.5)
        assertThat(m.recall(Category.PHOTO)).isWithin(1e-9).of(0.5)
        assertThat(m.precision(Category.DOCUMENT)).isEqualTo(0.0)
        assertThat(m.recall(Category.DOCUMENT)).isEqualTo(0.0)
        assertThat(m.render()).contains("accuracy: 0.667")
        assertThat(ConfusionMatrix().accuracy).isEqualTo(0.0)
    }
}

class FixtureCsvTest {
    @Test fun parsesQuotedFieldsAndMlColumns() {
        val csv = FixtureCsv.HEADER.joinToString(",") + "\r\n" +
            "a1,meme,Pictures/Telegram/,Telegram,x.jpg,image/jpeg,720,720,1000,,,0,0,1080,2400,0.5,0.2," +
            "\"TOP, TEXT\n\"\"BOTTOM\"\"\",2,0.12,1,1,Cartoon:0.7|Poster:0.4\n" +
            "a2,PHOTO,DCIM/Camera/,Camera,p.jpg,image/jpeg,4000,3000,5,Google,Pixel,1,yes,,,,,,,,,,\n"
        val rows = FixtureCsv.parse(csv)
        assertThat(rows).hasSize(2)
        val meme = rows[0]
        assertThat(meme.label).isEqualTo(Category.MEME)
        assertThat(meme.features.text!!.fullText).isEqualTo("TOP, TEXT\n\"BOTTOM\"")
        assertThat(meme.features.text!!.topBandText).isTrue()
        assertThat(meme.features.labels).containsExactly(ImageLabel("Cartoon", 0.7f), ImageLabel("Poster", 0.4f))
        assertThat(meme.features.screen).isEqualTo(ScreenSize(1080, 2400))
        val photo = rows[1]
        assertThat(photo.features.exif.hasGps).isTrue()
        assertThat(photo.features.text).isNull()
        assertThat(photo.features.labels).isNull()
        assertThat(photo.features.visual).isNull()
        assertThat(photo.features.screen).isNull()
    }

    @Test fun emptyInput() {
        assertThat(FixtureCsv.parse("")).isEmpty()
    }
}
