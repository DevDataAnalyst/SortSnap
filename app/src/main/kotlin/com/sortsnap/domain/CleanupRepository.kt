package com.sortsnap.domain

import com.sortsnap.classifier.Category
import com.sortsnap.classifier.ClassifierConfig
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.db.SummaryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

enum class CleanupSection { OLD_SCREENSHOTS, MEMES, FORWARDS, NEAR_DUPLICATES, BLURRY }

data class SectionSummary(val section: CleanupSection, val count: Int, val bytes: Long)

/** Items grouped for display; near-duplicates come in groups, other sections in a single group. */
data class SectionContent(val groups: List<List<GalleryItem>>, val keepByDefault: Set<Long>) {
    val all: List<GalleryItem> get() = groups.flatten()
}

@Singleton
class CleanupRepository @Inject constructor(private val images: ImageDao) {
    private val config = ClassifierConfig.DEFAULT

    fun oldScreenshotCutoff(now: Long = System.currentTimeMillis()) = now - OLD_DAYS * 24L * 60 * 60 * 1000

    fun summary(section: CleanupSection, now: Long = System.currentTimeMillis()): Flow<SectionSummary> = when (section) {
        CleanupSection.OLD_SCREENSHOTS -> images.summaryBefore(Category.SCREENSHOT, oldScreenshotCutoff(now)).toSummary(section)
        CleanupSection.MEMES -> images.summaryBefore(Category.MEME, Long.MAX_VALUE).toSummary(section)
        CleanupSection.FORWARDS -> images.summaryBefore(Category.FORWARD, Long.MAX_VALUE).toSummary(section)
        CleanupSection.BLURRY -> images.blurrySummary(config.blurVarianceThreshold).toSummary(section)
        CleanupSection.NEAR_DUPLICATES -> content(section, now).map { c ->
            val removable = c.all.filter { it.id !in c.keepByDefault }
            SectionSummary(section, removable.size, removable.sumOf { it.sizeBytes })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun content(section: CleanupSection, now: Long = System.currentTimeMillis()): Flow<SectionContent> = when (section) {
        CleanupSection.OLD_SCREENSHOTS -> images.categoryBefore(Category.SCREENSHOT, oldScreenshotCutoff(now)).map { single(it.map { r -> r.toGalleryItem() }) }
        CleanupSection.MEMES -> images.categoryBefore(Category.MEME, Long.MAX_VALUE).map { single(it.map { r -> r.toGalleryItem() }) }
        CleanupSection.FORWARDS -> images.categoryBefore(Category.FORWARD, Long.MAX_VALUE).map { single(it.map { r -> r.toGalleryItem() }) }
        CleanupSection.BLURRY -> images.blurry(config.blurVarianceThreshold).map { single(it.map { r -> r.toGalleryItem() }) }
        CleanupSection.NEAR_DUPLICATES -> images.latestHashedIdFlow().distinctUntilChanged().mapLatest { nearDuplicates() }
    }.flowOn(Dispatchers.Default)

    /** Groups by perceptual hash; in each group the largest (usually original-quality) file is kept by default. */
    private suspend fun nearDuplicates(): SectionContent {
        val hashes = images.hashes().map { it.mediaId to it.contentHash }
        val groups = NearDuplicateFinder.groups(hashes, config.nearDuplicateMaxDistance)
        if (groups.isEmpty()) return SectionContent(emptyList(), emptySet())
        val byId = groups.flatten().chunked(900).flatMap { images.byIds(it) }.associateBy { it.mediaId }
        val itemGroups = groups.map { g -> g.mapNotNull { byId[it]?.toGalleryItem() }.sortedByDescending { it.sizeBytes } }
            .filter { it.size > 1 }
        return SectionContent(itemGroups, itemGroups.map { it.first().id }.toSet())
    }

    private fun single(items: List<GalleryItem>) = SectionContent(if (items.isEmpty()) emptyList() else listOf(items), emptySet())

    private fun Flow<SummaryRow>.toSummary(section: CleanupSection) =
        map { SectionSummary(section, it.count, it.totalSize ?: 0L) }

    companion object {
        const val OLD_DAYS = 30
    }
}
