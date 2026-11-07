package com.sortsnap.domain

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.sortsnap.classifier.Category
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.media.MediaStorePagingSource
import com.sortsnap.data.media.MediaStoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository @Inject constructor(
    private val media: MediaStoreRepository,
    private val images: ImageDao,
) {
    /**
     * "All" pages MediaStore directly so first launch is instant even with 50k images and an empty
     * index. Category filters page the Room index. [indexReady] = the index has rows.
     */
    fun pager(filter: GalleryFilter, indexReady: Boolean): Flow<PagingData<GalleryItem>> {
        val config = PagingConfig(pageSize = PAGE, initialLoadSize = PAGE, prefetchDistance = PAGE / 2, enablePlaceholders = false)
        return when {
            filter == GalleryFilter.All || (filter == GalleryFilter.HideClutter && !indexReady) ->
                Pager(config) { MediaStorePagingSource(media::queryPage) }.flow.map { p -> p.map { it.toGalleryItem() } }
            filter == GalleryFilter.HideClutter ->
                Pager(config) { images.pagingExcluding(Category.CLUTTER.toList()) }.flow.map { p -> p.map { it.toGalleryItem() } }
            filter is GalleryFilter.Only ->
                Pager(config) { images.pagingIn(listOf(filter.category)) }.flow.map { p -> p.map { it.toGalleryItem() } }
            else -> error("unreachable")
        }
    }

    /** Placeholder-enabled pager for the full-screen viewer so page indices are absolute. */
    fun viewerPager(filter: GalleryFilter, indexReady: Boolean, initialIndex: Int): Flow<PagingData<GalleryItem>> {
        val config = PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = true, jumpThreshold = 90)
        val start = (initialIndex - 10).coerceAtLeast(0)
        return when {
            filter == GalleryFilter.All || (filter == GalleryFilter.HideClutter && !indexReady) ->
                Pager(config, initialKey = start) { MediaStorePagingSource(media::queryPage, media::count) }
                    .flow.map { p -> p.map { it.toGalleryItem() } }
            filter == GalleryFilter.HideClutter ->
                Pager(config, initialKey = start) { images.pagingExcluding(Category.CLUTTER.toList()) }
                    .flow.map { p -> p.map { it.toGalleryItem() } }
            filter is GalleryFilter.Only ->
                Pager(config, initialKey = start) { images.pagingIn(listOf(filter.category)) }
                    .flow.map { p -> p.map { it.toGalleryItem() } }
            else -> error("unreachable")
        }
    }

    companion object {
        const val PAGE = 120
    }
}
