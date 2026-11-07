package com.sortsnap.domain

import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class SyncDiffTest {
    @Test fun insertsDeletesAndRefreshes() {
        val media = listOf(MediaStamp(1, 10, 100), MediaStamp(2, 20, 200), MediaStamp(4, 40, 400))
        val index = listOf(MediaStamp(1, 10, 100), MediaStamp(2, 21, 200), MediaStamp(3, 30, 300))
        val plan = SyncDiff.compute(media, index)
        assertThat(plan.toInsert).containsExactly(4L)
        assertThat(plan.toDelete).containsExactly(3L)
        assertThat(plan.toRefresh).containsExactly(2L)
        assertThat(plan.isEmpty).isFalse()
    }

    @Test fun sizeChangeIsRefresh() {
        val plan = SyncDiff.compute(listOf(MediaStamp(1, 10, 101)), listOf(MediaStamp(1, 10, 100)))
        assertThat(plan.toRefresh).containsExactly(1L)
    }

    @Test fun identicalIsEmpty() {
        val same = listOf(MediaStamp(1, 1, 1))
        assertThat(SyncDiff.compute(same, same).isEmpty).isTrue()
    }

    @Test fun emptyMediaStoreDeletesEverything() {
        val plan = SyncDiff.compute(emptyList(), listOf(MediaStamp(5, 1, 1), MediaStamp(2, 1, 1)))
        assertThat(plan.toDelete).containsExactly(2L, 5L).inOrder()
    }

    @Test fun firstScanInsertsEverythingSorted() {
        val plan = SyncDiff.compute(listOf(MediaStamp(9, 1, 1), MediaStamp(3, 1, 1)), emptyList())
        assertThat(plan.toInsert).containsExactly(3L, 9L).inOrder()
    }

    @Test fun handlesLargeLibraryQuickly() {
        val media = (1L..60_000L).map { MediaStamp(it, it, it) }
        val index = (1L..59_000L).map { MediaStamp(it, it, it) }
        val start = System.nanoTime()
        val plan = SyncDiff.compute(media, index)
        assertThat(plan.toInsert).hasSize(1_000)
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000L)
    }
}

class FolderPriorLogicTest {
    private fun corrections(bucket: String, cat: Category, n: Int) = List(n) { CorrectionEvent(bucket, Category.PHOTO, cat) }

    @Test fun fiveSameCorrectionsLearnAPrior() {
        val prior = FolderPriorLogic.learnForBucket("WhatsApp Images", corrections("WhatsApp Images", Category.MEME, 5))
        assertThat(prior).isEqualTo(LearnedPrior("WhatsApp Images", Category.MEME, 5))
    }

    @Test fun fourIsNotEnough() {
        assertThat(FolderPriorLogic.learnForBucket("A", corrections("A", Category.MEME, 4))).isNull()
    }

    @Test fun otherBucketsDoNotCount() {
        val list = corrections("A", Category.MEME, 3) + corrections("B", Category.MEME, 3)
        assertThat(FolderPriorLogic.learnForBucket("A", list)).isNull()
    }

    @Test fun needsClearMajority() {
        val list = corrections("A", Category.MEME, 5) + corrections("A", Category.FORWARD, 5)
        assertThat(FolderPriorLogic.learnForBucket("A", list)).isNull()
        val majority = corrections("A", Category.MEME, 6) + corrections("A", Category.FORWARD, 5)
        assertThat(FolderPriorLogic.learnForBucket("A", majority)?.category).isEqualTo(Category.MEME)
    }

    @Test fun correctionsToOtherAreIgnored() {
        assertThat(FolderPriorLogic.learnForBucket("A", corrections("A", Category.OTHER, 9))).isNull()
    }

    @Test fun blankBucketNeverLearns() {
        assertThat(FolderPriorLogic.learnForBucket("", corrections("", Category.MEME, 9))).isNull()
    }

    @Test fun dismissedRuleNeedsFiveMoreCorrections() {
        assertThat(FolderPriorLogic.learnForBucket("A", corrections("A", Category.MEME, 7), dismissedAtCount = 5)).isNull()
        assertThat(FolderPriorLogic.learnForBucket("A", corrections("A", Category.MEME, 10), dismissedAtCount = 5)?.sampleCount)
            .isEqualTo(10)
    }

    @Test fun learnAllAcrossBuckets() {
        val list = corrections("B", Category.FORWARD, 5) + corrections("A", Category.MEME, 6) + corrections("C", Category.MEME, 2)
        val all = FolderPriorLogic.learnAll(list, dismissed = mapOf("B" to 5))
        assertThat(all).containsExactly(LearnedPrior("A", Category.MEME, 6))
    }
}

class NearDuplicateFinderTest {
    @Test fun groupsWithinDistance() {
        val base = 0x0F0F_F0F0_1234_5678L
        val items = listOf(
            1L to base,
            2L to (base xor 0b111L), // 3 bits away
            3L to base.inv(), // far away
            4L to (base xor (1L shl 60)), // 1 bit away
        )
        assertThat(NearDuplicateFinder.groups(items, maxDistance = 4)).containsExactly(listOf(1L, 2L, 4L))
    }

    @Test fun exactDistanceZero() {
        val items = listOf(1L to 42L, 2L to 42L, 3L to 43L)
        assertThat(NearDuplicateFinder.groups(items, maxDistance = 0)).containsExactly(listOf(1L, 2L))
    }

    @Test fun transitiveChainsMerge() {
        val items = listOf(1L to 0L, 2L to 0b1111L, 3L to 0b1111_1111L) // 1-2: 4 bits, 2-3: 4 bits, 1-3: 8 bits
        assertThat(NearDuplicateFinder.groups(items, maxDistance = 4)).containsExactly(listOf(1L, 2L, 3L))
    }

    @Test fun fewerThanTwoItems() {
        assertThat(NearDuplicateFinder.groups(listOf(1L to 1L))).isEmpty()
    }

    @Test fun pigeonholeFindsBitsSpreadAcrossChunks() {
        // Flip one bit in each of 4 different chunks; still within distance 4.
        val flipped = (1L shl 0) or (1L shl 20) or (1L shl 40) or (1L shl 63)
        assertThat(NearDuplicateFinder.groups(listOf(1L to 0L, 2L to flipped), 4)).containsExactly(listOf(1L, 2L))
    }

    @Test fun chunkRangesCover64Bits() {
        val ranges = NearDuplicateFinder.chunkRanges(5)
        assertThat(ranges.sumOf { it.second }).isEqualTo(64)
        assertThat(ranges.first()).isEqualTo(0 to 13)
        assertThat(NearDuplicateFinder.chunkRanges(1)).containsExactly(0 to 64)
        assertThat(NearDuplicateFinder.groups(listOf(1L to 5L, 2L to 5L), maxDistance = 0)).hasSize(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHugeDistance() {
        NearDuplicateFinder.groups(listOf(1L to 1L, 2L to 2L), maxDistance = 30)
    }
}

class DateBucketsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(2026, 11, 7, 9)

    @Test fun buckets() {
        assertThat(DateBuckets.of(at(2026, 11, 7, 1), now, zone)).isEqualTo(DateBucket.Today)
        assertThat(DateBuckets.of(at(2026, 11, 6, 23), now, zone)).isEqualTo(DateBucket.Yesterday)
        assertThat(DateBuckets.of(at(2026, 11, 2), now, zone)).isEqualTo(DateBucket.ThisWeek)
        assertThat(DateBuckets.of(at(2026, 10, 31), now, zone)).isEqualTo(DateBucket.Month(2026, 10))
        assertThat(DateBuckets.of(at(2025, 1, 15), now, zone)).isEqualTo(DateBucket.Month(2025, 1))
        assertThat(DateBuckets.of(at(2026, 11, 9), now, zone)).isEqualTo(DateBucket.Today)
    }

    @Test fun epochDayRoundTrip() {
        val day = DateBuckets.epochDay(now, zone)
        assertThat(DateBuckets.epochDay(DateBuckets.startOfDay(day, zone), zone)).isEqualTo(day)
    }
}

class DailyReviewPickerTest {
    @Test fun deterministicPerDayAndBounded() {
        val ids = (1L..100L).toList()
        val a = DailyReviewPicker.pick(ids, 20_000)
        assertThat(a).hasSize(20)
        assertThat(DailyReviewPicker.pick(ids.reversed(), 20_000)).isEqualTo(a)
        assertThat(DailyReviewPicker.pick(ids, 20_001)).isNotEqualTo(a)
        assertThat(DailyReviewPicker.pick(listOf(1L, 2L), 1)).hasSize(2)
    }

    @Test fun candidateBand() {
        assertThat(DailyReviewPicker.isCandidate(0.6f, false)).isTrue()
        assertThat(DailyReviewPicker.isCandidate(0.6f, true)).isFalse()
        assertThat(DailyReviewPicker.isCandidate(0.9f, false)).isFalse()
        assertThat(DailyReviewPicker.isCandidate(0.5f, false)).isFalse()
    }
}

class UndoStackTest {
    @Test fun keepsOnlyLastTen() {
        val s = UndoStack<Int>(10)
        (1..15).forEach(s::push)
        assertThat(s.size).isEqualTo(10)
        assertThat(s.toList().first()).isEqualTo(6)
        assertThat(s.peek()).isEqualTo(15)
        assertThat(s.pop()).isEqualTo(15)
        assertThat(s.canUndo).isTrue()
        s.clear()
        assertThat(s.pop()).isNull()
        assertThat(s.canUndo).isFalse()
    }
}

class FormattingTest {
    @Test fun bytes() {
        assertThat(Formatting.bytes(500, Locale.US)).isEqualTo("500 B")
        assertThat(Formatting.bytes(2048, Locale.US)).isEqualTo("2 KB")
        assertThat(Formatting.bytes(5L * 1024 * 1024 + 300_000, Locale.US)).isEqualTo("5.3 MB")
        assertThat(Formatting.bytes(150L * 1024 * 1024, Locale.US)).isEqualTo("150 MB")
        assertThat(Formatting.bytes(3L * 1024 * 1024 * 1024, Locale.US)).isEqualTo("3.0 GB")
    }

    @Test fun percent() {
        assertThat(Formatting.percent(0.734f)).isEqualTo("73%")
        assertThat(Formatting.percent(2f)).isEqualTo("100%")
    }
}

class ScanStatsTest {
    @Test fun rates() {
        val s = ScanStats().plusBatch(50, 2_000, 10, 500, 100).plusBatch(50, 2_000, 0, 0, 50)
        assertThat(s.imagesPerSecond).isWithin(1e-9).of(25.0)
        assertThat(s.averageMlMillis).isWithin(1e-9).of(50.0)
        assertThat(s.queueLength).isEqualTo(50)
        assertThat(ScanStats().imagesPerSecond).isEqualTo(0.0)
        assertThat(ScanStats().averageMlMillis).isEqualTo(0.0)
    }
}
