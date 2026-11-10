package com.sortsnap.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category
import com.sortsnap.classifier.LabelledExample
import com.sortsnap.classifier.PersonalModel
import com.sortsnap.classifier.Signal
import com.sortsnap.classifier.SignalCodec
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.db.SortSnapDatabase
import com.sortsnap.data.media.MediaStoreRepository
import com.sortsnap.data.scan.IndexSync
import com.sortsnap.domain.CorrectionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: SortSnapDatabase
    private lateinit var corrections: CorrectionUseCase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SortSnapDatabase::class.java).allowMainThreadQueries().build()
        corrections = CorrectionUseCase(db, IndexSync(MediaStoreRepository(context), db.images()))
    }

    @After fun tearDown() = db.close()

    private fun record(id: Long, bucket: String = "WhatsApp Images", category: Category? = Category.OTHER) = ImageRecord(
        mediaId = id, uri = "content://media/external/images/media/$id", displayName = "IMG_$id.jpg",
        path = "WhatsApp/Media/", bucket = bucket, dateTaken = id * 1000, dateModified = id, width = 100, height = 100,
        size = 1000, mime = "image/jpeg", category = category, effectiveCategory = category, confidence = 0.4f,
        classifierVersion = 1,
    )

    @Test fun scanNeverOverwritesUserOverride() = runTest {
        db.images().insertAll(listOf(record(1)))
        corrections.moveToCategory(listOf(1L), Category.MEME)
        val updated = db.images().updateClassification(1, Category.PHOTO, 0.9f, "[]", null, null, 0, 99, true)
        assertThat(updated).isEqualTo(0)
        assertThat(db.images().get(1)!!.effectiveCategory).isEqualTo(Category.MEME)
        // ...and it is not even queued for reclassification.
        assertThat(db.images().pendingBatch(99, 0, 50)).isEmpty()
        db.images().requeueAll()
        assertThat(db.images().get(1)!!.userOverride).isEqualTo(Category.MEME)
    }

    @Test fun refreshKeepsOverrideButRequeuesOthers() = runTest {
        db.images().insertAll(listOf(record(1), record(2)))
        db.images().setOverride(listOf(1L), Category.DOCUMENT)
        listOf(1L, 2L).forEach {
            db.images().refreshMetadata(it, "u", "n", "p", "b", 0, 5, 1, 1, 1, "image/png")
        }
        assertThat(db.images().get(1)!!.classifierVersion).isEqualTo(1)
        assertThat(db.images().get(2)!!.classifierVersion).isEqualTo(0)
        assertThat(db.images().pendingBatch(1, 0, 50).map { it.mediaId }).containsExactly(2L)
    }

    @Test fun fiveCorrectionsInOneFolderLearnARuleThatCanBeDismissed() = runTest {
        db.images().insertAll((1L..6L).map { record(it) })
        corrections.moveToCategory((1L..4L).toList(), Category.MEME)
        assertThat(db.priors().active()).isEmpty()
        corrections.moveToCategory(listOf(5L), Category.MEME)
        val rule = db.priors().active().single()
        assertThat(rule.bucket).isEqualTo("WhatsApp Images")
        assertThat(rule.category).isEqualTo(Category.MEME)
        assertThat(db.corrections().countFlow().first()).isEqualTo(5)

        db.priors().dismiss("WhatsApp Images")
        assertThat(db.priors().active()).isEmpty()
        corrections.moveToCategory(listOf(6L), Category.MEME) // 6 < 5 + 5: stays dismissed
        assertThat(db.priors().active()).isEmpty()
    }

    @Test fun handSortedImagesBecomePersonalLearningExamples() = runTest {
        val evidence = SignalCodec.encode(listOf(Signal("chat_app_folder", mapOf(Category.MEME to 0.6f), detail = "Telegram")))
        db.images().insertAll(
            listOf(
                record(1).copy(signalsJson = evidence),
                record(2).copy(signalsJson = evidence),
                record(3).copy(signalsJson = evidence),
                record(4).copy(signalsJson = "[]"), // no evidence: useless for learning
                record(5).copy(signalsJson = evidence), // not hand-sorted
            ),
        )
        corrections.moveToCategory(listOf(1L, 2L, 4L), Category.FORWARD)
        corrections.confirm(3L) // "Is this right?" → Yes also teaches (as OTHER here, which is ignored)
        val rows = db.images().labelledExamples(100)
        assertThat(rows.map { it.category }).containsExactly(Category.FORWARD, Category.FORWARD)
        assertThat(db.images().labelledCountFlow().first()).isEqualTo(2)

        val model = PersonalModel.build(rows.map { LabelledExample(SignalCodec.decode(it.signalsJson), it.category) })
        assertThat(model.exampleCount).isEqualTo(2)
    }

    @Test fun resortUnsureRequeuesOnlyUnsureUncorrectedRows() = runTest {
        db.images().insertAll(
            listOf(
                record(1, category = Category.OTHER).copy(classifierVersion = 3, confidence = 0.4f),
                record(2, category = Category.PHOTO).copy(classifierVersion = 3, confidence = 0.95f),
                record(3, category = Category.MEME).copy(classifierVersion = 3, confidence = 0.5f),
                record(4, category = Category.OTHER).copy(classifierVersion = 3, confidence = 0.4f),
            ),
        )
        db.images().setOverride(listOf(4L), Category.DOCUMENT)
        assertThat(db.images().requeueUnsure(0.55f)).isEqualTo(2)
        assertThat(db.images().pendingBatch(3, 0, 50).map { it.mediaId }).containsExactly(1L, 3L)
    }

    @Test fun movingToSameCategoryLogsNoCorrection() = runTest {
        db.images().insertAll(listOf(record(1, category = Category.MEME)))
        corrections.moveToCategory(listOf(1L), Category.MEME)
        assertThat(db.corrections().countFlow().first()).isEqualTo(0)
        assertThat(db.images().get(1)!!.userOverride).isEqualTo(Category.MEME)
    }

    @Test fun categoryStatsAndHideClutterQuery() = runTest {
        db.images().insertAll(
            listOf(record(1, category = Category.PHOTO), record(2, category = Category.MEME), record(3, category = null), record(4, category = Category.OTHER)),
        )
        val stats = db.images().categoryStats().first().associateBy { it.category }
        assertThat(stats[Category.PHOTO]!!.count).isEqualTo(1)
        assertThat(stats[Category.MEME]!!.coverUri).endsWith("/2")
    }
}
